package com.animewave.app.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

enum class Kind(val api: String) { MOVIE("movie"), TV("tv") }

/** An anime: [id] is its AniList id; films are [Kind.MOVIE], everything else [Kind.TV]. */
data class Title(
    val id: Int,
    val kind: Kind,
    val name: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    val rating: Double,
    val genreIds: List<Int> = emptyList(),
) {
    val key get() = "${kind.api}:$id"
}

data class Season(val number: Int, val name: String, val episodeCount: Int)
data class Episode(val number: Int, val name: String, val overview: String, val still: String?, val runtime: Int?)

data class Person(val id: Int, val name: String, val role: String, val photo: String?)

data class Details(
    val title: Title,
    val tagline: String,
    val runtime: Int?,
    val genres: List<String>,
    val seasons: List<Season>,
    val cast: List<Person>,
    val similar: List<Title>,
    val logo: String?,
    val trailer: String?,
    /** Key art with no title printed on it (or null if there's none). */
    val cleanPoster: String?,
)

/** Artwork without baked-in title text, plus the title logo, for big hero images. */
data class Art(val image: String?, val wide: Boolean, val logo: String?)

/** A person and everything they've been in; [roles] maps a title key to the part they played (or their job). */
data class PersonDetails(val person: Person, val bio: String, val known: List<Title>, val roles: Map<String, String> = emptyMap(), val facts: String = "")

data class Row(val name: String, val items: List<Title>, val ranked: Boolean = false)

data class Genre(val id: Int, val name: String)

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

/**
 * The anime catalogue, from AniList (free, no key). Named Tmdb so the screens shared with Movio
 * stay the same; ids are AniList ids and every image is a full address.
 */
object Tmdb {
    private const val API = "https://graphql.anilist.co"

    private val genreNames = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Slice of Life",
        "Mystery", "Horror", "Sports", "Supernatural", "Psychological", "Mecha", "Music", "Thriller",
    )
    val tvGenres = genreNames.mapIndexed { i, n -> Genre(i + 1, n) }
    val movieGenres = tvGenres

    private const val FIELDS = "id format title{romaji english} coverImage{extraLarge large color} bannerImage genres " +
        "seasonYear startDate{year} averageScore description(asHtml:false) episodes duration"

    fun img(path: String?, size: String = ""): String? = path

    /** Where catalogue responses are kept on the phone (set at app start). */
    @Volatile var diskDir: java.io.File? = null
    private const val FRESH_MS = 6 * 60 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, JSONObject>()

    /** AniList query with a disk cache: fresh for 6 hours, and with no internet the last copy is used. */
    private suspend fun query(q: String, vars: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", q).put("variables", vars).toString()
        val key = Integer.toHexString(body.hashCode()) + body.length
        cache[key]?.let { return@withContext it }
        val file = diskDir?.let { java.io.File(it, "$key.json") }
        if (file != null && file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS)
            runCatching { JSONObject(file.readText()) }.getOrNull()?.let { cache[key] = it; return@withContext it }
        try {
            var tries = 0
            while (true) {
                val c = URL(API).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                // AniList allows ~30 requests a minute; wait and retry when it says slow down.
                if (c.responseCode == 429 && tries++ < 2) { Thread.sleep(((c.getHeaderField("Retry-After")?.toLongOrNull() ?: 3L) * 1000).coerceAtMost(8000)); continue }
                if (c.responseCode !in 200..299) throw IOException("Catalogue error ${c.responseCode}")
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("data")
                cache[key] = o
                runCatching { file?.writeText(o.toString()) }
                return@withContext o
            }
            @Suppress("UNREACHABLE_CODE") throw IOException()
        } catch (e: Exception) {
            file?.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }?.let { return@withContext it }
            throw e
        }
    }

    private fun clean(text: String) = text
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\(Source:[^)]*\\)"), "").replace(Regex("\n{3,}"), "\n\n").trim()

    private fun parse(m: JSONObject): Title {
        val t = m.optJSONObject("title")
        val cover = m.optJSONObject("coverImage")
        val g = m.optJSONArray("genres")
        val genres = if (g == null) emptyList() else (0 until g.length()).map { g.getString(it) }
        return Title(
            id = m.getInt("id"),
            kind = if (m.optString("format") == "MOVIE") Kind.MOVIE else Kind.TV,
            name = t?.optString("english")?.takeIf { it.isNotBlank() && it != "null" } ?: t?.optString("romaji").orEmpty(),
            overview = clean(m.optString("description").takeIf { it != "null" }.orEmpty()),
            poster = cover?.optString("extraLarge")?.takeIf { it.startsWith("http") } ?: cover?.optString("large")?.takeIf { it.startsWith("http") },
            backdrop = m.optString("bannerImage").takeIf { it.startsWith("http") },
            year = (m.optInt("seasonYear").takeIf { it > 0 } ?: m.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 })?.toString(),
            rating = m.optInt("averageScore") / 10.0,
            genreIds = genres.mapNotNull { n -> tvGenres.firstOrNull { it.name == n }?.id },
        )
    }

    private fun media(a: JSONArray?): List<Title> =
        if (a == null) emptyList() else (0 until a.length()).map { parse(a.getJSONObject(it)) }

    private suspend fun list(args: String, perPage: Int = 20): List<Title> =
        media(query("query{Page(perPage:$perPage){media(type:ANIME,isAdult:false,$args){$FIELDS}}}").getJSONObject("Page").optJSONArray("media"))

    /** "Because you watched" rows from recent titles' recommendations. */
    suspend fun becauseYouWatched(recent: List<Title>, exclude: Set<String>): List<Row> = coroutineScope {
        recent.distinctBy { it.key }.take(3).map { t ->
            async {
                try {
                    val recs = details(t).similar.filter { it.key !in exclude }
                    if (recs.size >= 4) Row("Because you watched ${t.name}", recs) else null
                } catch (_: Exception) { null }
            }
        }.mapNotNull { it.await() }
    }

    /** Loads every row at once instead of one after another. */
    private suspend fun rows(vararg specs: Pair<String, suspend () -> List<Title>>, ranked: String? = null): List<Row> =
        coroutineScope {
            specs.map { (name, load) -> async { Row(name, try { load() } catch (_: Exception) { emptyList() }, ranked = name == ranked) } }.map { it.await() }
        }.filter { it.items.isNotEmpty() }.also { if (it.isEmpty()) error("Nothing loaded") }

    private fun r(name: String, load: suspend () -> List<Title>) = name to load

    /** Rows that come after "Because you watched" on Home. */
    const val PERSONAL_AFTER = 3

    private const val SERIES = "format_in:[TV,TV_SHORT,ONA,OVA,SPECIAL]"

    suspend fun home(): List<Row> = rows(
        r("Top 10 today") { list("sort:TRENDING_DESC", 10) },
        r("Airing now") { list("status:RELEASING,sort:POPULARITY_DESC") },
        r("Trending this week") { list("sort:TRENDING_DESC", 30).drop(10) },
        r("Popular this season") { list("season:${season()},seasonYear:${java.time.LocalDate.now().year},sort:POPULARITY_DESC") },
        r("All-time favourites") { list("sort:POPULARITY_DESC") },
        r("Top rated") { list("sort:SCORE_DESC") },
        r("Anime films") { list("format:MOVIE,sort:POPULARITY_DESC") },
        ranked = "Top 10 today",
    )

    private fun season() = when (java.time.LocalDate.now().monthValue) { in 1..3 -> "WINTER"; in 4..6 -> "SPRING"; in 7..9 -> "SUMMER"; else -> "FALL" }

    suspend fun browse(kind: Kind, genre: Genre?): List<Row> {
        val format = if (kind == Kind.MOVIE) "format:MOVIE" else SERIES
        if (genre != null) {
            val g = "genre:\"${genre.name}\""
            return listOf(
                Row("Popular ${genre.name}", list("$format,$g,sort:POPULARITY_DESC")),
                Row("Trending ${genre.name}", list("$format,$g,sort:TRENDING_DESC")),
                Row("Highly rated ${genre.name}", list("$format,$g,sort:SCORE_DESC")),
            ).filter { it.items.isNotEmpty() }
        }
        return if (kind == Kind.MOVIE) rows(
            r("Top 10 films") { list("$format,sort:TRENDING_DESC", 10) },
            r("Popular") { list("$format,sort:POPULARITY_DESC") },
            r("Top rated") { list("$format,sort:SCORE_DESC") },
            r("Coming soon") { list("$format,status:NOT_YET_RELEASED,sort:POPULARITY_DESC") },
            ranked = "Top 10 films",
        ) else rows(
            r("Top 10 series") { list("$format,sort:TRENDING_DESC", 10) },
            r("Airing now") { list("$format,status:RELEASING,sort:POPULARITY_DESC") },
            r("Popular this season") { list("$format,season:${season()},seasonYear:${java.time.LocalDate.now().year},sort:POPULARITY_DESC") },
            r("Popular") { list("$format,sort:POPULARITY_DESC") },
            r("Top rated") { list("$format,sort:SCORE_DESC") },
            ranked = "Top 10 series",
        )
    }

    suspend fun search(q: String): List<Title> {
        val o = query("query(\$s:String){Page(perPage:40){media(type:ANIME,isAdult:false,search:\$s,sort:SEARCH_MATCH){$FIELDS}}}", JSONObject().put("s", q))
        return media(o.getJSONObject("Page").optJSONArray("media"))
    }

    suspend fun details(t: Title): Details {
        val m = query(
            "query(\$id:Int){Media(id:\$id){$FIELDS trailer{id site} " +
                "characters(sort:[ROLE,RELEVANCE],perPage:15){edges{role node{name{full} image{large}} voiceActors(language:JAPANESE){id name{full} image{large}}}} " +
                "recommendations(sort:RATING_DESC,perPage:20){nodes{mediaRecommendation{$FIELDS}}}}}",
            JSONObject().put("id", t.id),
        ).getJSONObject("Media")
        val full = parse(m)
        val g = m.optJSONArray("genres")
        val genres = if (g == null) emptyList() else (0 until g.length()).map { g.getString(it) }
        val count = m.optInt("episodes").takeIf { it > 0 } ?: runCatching { AnimeMap.get(t.id).count }.getOrDefault(0)
        val cast = m.optJSONObject("characters")?.optJSONArray("edges")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val e = a.getJSONObject(i)
                val character = e.optJSONObject("node")?.optJSONObject("name")?.optString("full").orEmpty()
                val va = e.optJSONArray("voiceActors")?.optJSONObject(0) ?: return@mapNotNull null
                Person(va.optInt("id"), va.optJSONObject("name")?.optString("full").orEmpty(), character,
                    va.optJSONObject("image")?.optString("large")?.takeIf { it.startsWith("http") && !it.contains("default") })
            }
        } ?: emptyList()
        val similar = m.optJSONObject("recommendations")?.optJSONArray("nodes")?.let { a ->
            (0 until a.length()).mapNotNull { a.getJSONObject(it).optJSONObject("mediaRecommendation")?.let(::parse) }
        } ?: emptyList()
        val trailer = m.optJSONObject("trailer")?.takeIf { it.optString("site") == "youtube" }?.optString("id")
        return Details(
            full.copy(poster = full.poster ?: t.poster), "", m.optInt("duration").takeIf { it > 0 }, genres,
            if (full.kind == Kind.TV) listOf(Season(1, "Episodes", count)) else emptyList(),
            cast, similar, null, trailer, full.poster,
        )
    }

    /** Hero artwork: the wide banner when there is one, else the key art. */
    suspend fun art(t: Title): Art = Art(t.backdrop ?: t.poster, t.backdrop != null, null)

    suspend fun person(id: Int): PersonDetails {
        val s = query(
            "query(\$id:Int){Staff(id:\$id){name{full} image{large} description(asHtml:false) primaryOccupations dateOfBirth{year} homeTown " +
                "characterMedia(sort:POPULARITY_DESC,perPage:40){edges{characters{name{full}} node{$FIELDS}}}}}",
            JSONObject().put("id", id),
        ).getJSONObject("Staff")
        val roles = HashMap<String, String>()
        val known = s.optJSONObject("characterMedia")?.optJSONArray("edges")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val e = a.getJSONObject(i)
                val t = e.optJSONObject("node")?.let(::parse) ?: return@mapNotNull null
                e.optJSONArray("characters")?.optJSONObject(0)?.optJSONObject("name")?.optString("full")?.let { roles.putIfAbsent(t.key, it) }
                t.takeIf { it.poster != null }
            }.distinctBy { it.key }
        } ?: emptyList()
        val jobs = s.optJSONArray("primaryOccupations")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val born = s.optJSONObject("dateOfBirth")?.optInt("year")?.takeIf { it > 0 }
        val facts = listOfNotNull(jobs.firstOrNull(), born?.let { "Born $it" }, s.optString("homeTown").takeIf { it.isNotBlank() && it != "null" }).joinToString("  ·  ")
        return PersonDetails(
            Person(id, s.optJSONObject("name")?.optString("full").orEmpty(), jobs.firstOrNull() ?: "Voice actor", s.optJSONObject("image")?.optString("large")),
            clean(s.optString("description").takeIf { it != "null" }.orEmpty()), known, roles, facts,
        )
    }

    /** Latest aired episode of a series, as "1:episode" plus when it aired. */
    suspend fun lastAired(showId: Int): Pair<String, String>? {
        val m = query("query(\$id:Int){Media(id:\$id){episodes status nextAiringEpisode{episode airingAt}}}", JSONObject().put("id", showId)).getJSONObject("Media")
        val next = m.optJSONObject("nextAiringEpisode")
        val last = next?.optInt("episode")?.minus(1) ?: m.optInt("episodes").takeIf { it > 0 } ?: return null
        if (last < 1) return null
        return "1:$last" to (next?.optLong("airingAt")?.minus(7 * 86_400)?.toString() ?: "")
    }

    suspend fun episodeCount(showId: Int, season: Int): Int = episodes(showId, season).size

    suspend fun seasonCount(showId: Int): Int = 1

    /** Every aired episode with its title, summary and still. */
    suspend fun episodes(showId: Int, season: Int): List<Episode> {
        val i = AnimeMap.get(showId)
        val aired = runCatching { lastAired(showId)?.first?.substringAfter(':')?.toIntOrNull() }.getOrNull()
        val count = (aired ?: maxOf(i.count, i.episodes.keys.maxOrNull() ?: 0)).coerceAtLeast(i.episodes.keys.filter { aired == null || it <= aired }.maxOrNull() ?: 0)
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
