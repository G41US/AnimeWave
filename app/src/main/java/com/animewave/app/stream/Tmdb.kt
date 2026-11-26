package com.animewave.app.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

enum class Kind(val api: String) { MOVIE("movie"), TV("tv") }

/** An anime as the stream player sees it: [id] is the AniList id. */
data class Title(
    val id: Int,
    val kind: Kind,
    val name: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    val rating: Double,
) {
    val key get() = "${kind.api}:$id"
}

data class Episode(val number: Int, val name: String, val overview: String, val still: String?, val runtime: Int?)

data class Details(val title: Title, val runtime: Int?)

/**
 * Links an AniList entry to the other catalogues the stream sources understand (TMDB id with its
 * season/episode numbers, MAL id), and carries each episode's title, summary, still and length.
 * Data from ani.zip, cached for the session.
 */
object AnimeMap {
    data class Ep(val season: Int, val episode: Int, val name: String, val overview: String, val image: String?, val runtime: Int?)
    data class Info(val tmdb: Int?, val movie: Boolean, val mal: Int?, val episodes: Map<Int, Ep>, val count: Int, val runtime: Int?)

    private val cache = ConcurrentHashMap<Int, Info>()

    fun cached(anilist: Int): Info? = cache[anilist]

    suspend fun get(anilist: Int): Info = cache[anilist] ?: withContext(Dispatchers.IO) {
        val c = URL("https://api.ani.zip/mappings?anilist_id=$anilist").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        val o = c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val m = o.optJSONObject("mappings") ?: JSONObject()
        val eps = HashMap<Int, Ep>()
        o.optJSONObject("episodes")?.let { e ->
            for (k in e.keys()) {
                val n = k.toIntOrNull() ?: continue // "S1", "S2"… are specials
                val x = e.optJSONObject(k) ?: continue
                val name = x.optJSONObject("title")?.optString("en").orEmpty().ifBlank { "Episode $n" }
                val about = x.optString("overview").ifBlank { x.optString("summary") }.takeIf { it != "null" }.orEmpty()
                eps[n] = Ep(
                    season = x.optInt("seasonNumber", 1).takeIf { it > 0 } ?: 1,
                    episode = x.optInt("episodeNumber", n).takeIf { it > 0 } ?: n,
                    name = name.replace('`', '\''),
                    overview = about.replace('`', '\''),
                    image = x.optString("image").takeIf { it.startsWith("http") },
                    runtime = x.optInt("runtime").takeIf { it > 0 } ?: x.optInt("length").takeIf { it > 0 },
                )
            }
        }
        val movie = m.optString("type").equals("MOVIE", true)
        Info(
            tmdb = m.optInt("themoviedb_id").takeIf { it > 0 },
            movie = movie,
            mal = m.optInt("mal_id").takeIf { it > 0 },
            episodes = eps,
            count = o.optInt("episodeCount").takeIf { it > 0 } ?: eps.keys.maxOrNull() ?: 0,
            runtime = eps.values.mapNotNull { it.runtime }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] },
        ).also { cache[anilist] = it }
    }

    /** TMDB id and season/episode for AniList [episode], or null when the title isn't on TMDB. */
    fun tmdb(anilist: Int, episode: Int): Triple<Int, Int, Int>? {
        val i = cache[anilist] ?: return null
        val id = i.tmdb ?: return null
        val e = i.episodes[episode] ?: return Triple(id, 1, episode)
        return Triple(id, e.season, e.episode)
    }
}

/** The catalogue calls the player makes, answered from AniList + ani.zip data. */
object Tmdb {
    /** Artwork here is already a full address. */
    fun img(path: String?, size: String = ""): String? = path

    suspend fun details(t: Title): Details = Details(t, AnimeMap.get(t.id).runtime)

    suspend fun episodeCount(showId: Int, season: Int): Int = AnimeMap.get(showId).count

    suspend fun seasonCount(showId: Int): Int = 1

    suspend fun episodes(showId: Int, season: Int): List<Episode> {
        val i = AnimeMap.get(showId)
        val count = maxOf(i.count, i.episodes.keys.maxOrNull() ?: 0)
        return (1..count).map { n ->
            val e = i.episodes[n]
            Episode(n, e?.name ?: "Episode $n", e?.overview.orEmpty(), e?.image, e?.runtime ?: i.runtime)
        }
    }
}

/** The TMDB kind, id, season and episode for AniList [id] episode [e], or null if it isn't on TMDB. */
suspend fun tmdbTarget(kind: Kind, id: Int, e: Int): Pair<Kind, Triple<Int, Int, Int>>? {
    val info = runCatching { AnimeMap.get(id) }.getOrNull() ?: return null
    val tmdb = info.tmdb ?: return null
    if (info.movie || kind == Kind.MOVIE) return Kind.MOVIE to Triple(tmdb, 1, 1)
    return Kind.TV to (AnimeMap.tmdb(id, e) ?: return null)
}
