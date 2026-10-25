package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Episode(val title: String, val thumbnail: String?, val url: String, val site: String)

data class Anime(
    val id: Int,
    val title: String,
    val cover: String?,
    val banner: String?,
    val accent: Long?,
    val genres: List<String>,
    val year: Int?,
    val format: String?,
    val episodes: Int?,
    val score: Int?,
    val synopsis: String,
    /** Official streaming sites (site name to HTTPS url). */
    val watchLinks: List<Pair<String, String>>,
    val streamingEpisodes: List<Episode>,
)

/** Free public anime database, no API key: https://docs.anilist.co */
object AniList {
    private const val FIELDS = "id title{romaji english} coverImage{large color} bannerImage genres seasonYear " +
        "episodes format averageScore description(asHtml:false) externalLinks{site url type} " +
        "streamingEpisodes{title thumbnail url site}"

    private fun page(alias: String, args: String, perPage: Int = 20) =
        "$alias: Page(perPage:$perPage){media(type:ANIME,isAdult:false,$args){$FIELDS}}"

    suspend fun home() = post(
        "query{" +
            page("trending", "sort:TRENDING_DESC") +
            page("airing", "status:RELEASING,sort:POPULARITY_DESC") +
            page("action", "genre_in:[\"Action\",\"Adventure\"],sort:POPULARITY_DESC") +
            page("popular", "sort:POPULARITY_DESC") +
            "}"
    )

    suspend fun search(text: String) =
        post("query(\$s:String){${page("r", "search:\$s,sort:SEARCH_MATCH", 30)}}", JSONObject().put("s", text))["r"].orEmpty()

    suspend fun genre(name: String) =
        post("query(\$g:String){${page("r", "genre:\$g,sort:POPULARITY_DESC", 30)}}", JSONObject().put("g", name))["r"].orEmpty()

    // shortcut: one page of 50, upgrade to paging if My List grows past 50 titles
    suspend fun byIds(ids: Collection<Int>) =
        post("query(\$ids:[Int]){${page("r", "id_in:\$ids", 50)}}", JSONObject().put("ids", JSONArray(ids)))["r"].orEmpty()

    private suspend fun post(query: String, variables: JSONObject = JSONObject()) = withContext(Dispatchers.IO) {
        val conn = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use {
                it.write(JSONObject().put("query", query).put("variables", variables).toString().toByteArray())
            }
            val ok = conn.responseCode in 200..299
            val body = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) throw IOException("AniList HTTP ${conn.responseCode}: ${body.take(200)}")
            parseResponse(body)
        } finally {
            conn.disconnect()
        }
    }
}

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)
/** AniList stores many official links as http://; those sites all serve https, and the app blocks cleartext. */
private fun secure(url: String?): String? = when {
    url == null -> null
    url.startsWith("https://") -> url
    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
    else -> null
}
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

/** Maps each aliased Page in the response to its anime list. */
fun parseResponse(body: String): Map<String, List<Anime>> {
    val root = JSONObject(body)
    val data = root.optJSONObject("data") ?: throw IOException("AniList error: ${root.optJSONArray("errors")}")
    return data.keys().asSequence().associateWith { key ->
        data.getJSONObject(key).optJSONArray("media").objects().map(::parseMedia)
    }
}

fun parseMedia(m: JSONObject): Anime {
    val title = m.getJSONObject("title")
    val cover = m.optJSONObject("coverImage")
    val genres = m.optJSONArray("genres")
    return Anime(
        id = m.getInt("id"),
        title = title.str("english") ?: title.str("romaji") ?: "Untitled",
        cover = cover?.str("large"),
        banner = m.str("bannerImage"),
        accent = cover?.str("color")?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.or(0xFF000000),
        genres = if (genres == null) emptyList() else (0 until genres.length()).map { genres.getString(it) },
        year = m.int("seasonYear"),
        format = m.str("format"),
        episodes = m.int("episodes"),
        score = m.int("averageScore"),
        synopsis = m.str("description").orEmpty()
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifEmpty { "No synopsis yet." },
        watchLinks = m.optJSONArray("externalLinks").objects()
            .filter { it.str("type") == "STREAMING" }
            .mapNotNull { link -> secure(link.str("url"))?.let { link.getString("site") to it } },
        streamingEpisodes = m.optJSONArray("streamingEpisodes").objects().mapNotNull { e ->
            secure(e.str("url"))
                ?.let { Episode(e.str("title") ?: "Episode", e.str("thumbnail"), it, e.str("site").orEmpty()) }
        },
    )
}
