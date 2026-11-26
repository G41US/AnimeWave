package com.animewave.app.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Subtitle(val label: String, val lang: String, val url: String, val srt: Boolean = url.endsWith(".srt"))
data class Playable(val url: String, val subtitles: List<Subtitle>, val headers: Map<String, String>)

/**
 * Asks the player service behind braflix for a title's stream directly, by TMDB id, so no web page
 * (and none of its ads) ever loads. Returns an HLS master playlist with every quality.
 */
object Source {
    const val BASE = "https://vidsrc.party"
    private const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    val headers = mapOf("Referer" to "$BASE/", "Origin" to BASE, "User-Agent" to UA)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000
            c.readTimeout = 40_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) throw IOException("Source error ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    suspend fun resolve(anime: Kind, anilist: Int, s: Int, e: Int): Playable = withContext(Dispatchers.IO) {
        val (kind, t) = tmdbTarget(anime, anilist, e) ?: throw java.io.IOException("Not on this source")
        val (id, season, episode) = t
        val path = if (kind == Kind.TV) "api/play/tv/$id/$season/$episode" else "api/play/movie/$id"
        var file: JSONObject? = null
        // The service may still be preparing the stream; ask again for up to ~40s.
        for (attempt in 0 until 8) {
            val o = JSONObject(get("$BASE/$path?async=1&wait=1"))
            file = o.optJSONObject("result")?.optJSONObject("file")
            if (file != null && o.optString("status") == "ready") break
            if (o.optString("status") in setOf("error", "failed", "not_found")) throw IOException(o.optString("message", "Not available"))
            delay(5_000)
        }
        val f = file ?: throw IOException("Stream not ready")
        val stream = f.optString("playlist").ifBlank { f.optString("stream") }.ifBlank { throw IOException("No stream") }
        val subs = try { subsFrom(f) } catch (_: Exception) { emptyList() }
        Playable(if (stream.startsWith("http")) stream else BASE + stream, subs, headers)
    }

    /** The service's subtitle list only (used next to Wyzie for every source). */
    suspend fun subtitles(kind: Kind, id: Int, season: Int, episode: Int): List<Subtitle> = withContext(Dispatchers.IO) {
        val path = if (kind == Kind.TV) "api/play/tv/$id/$season/$episode" else "api/play/movie/$id"
        var file: JSONObject? = null
        for (attempt in 0 until 5) {
            val o = JSONObject(get("$BASE/$path?async=1&wait=1"))
            file = o.optJSONObject("result")?.optJSONObject("file")
            if (file != null && o.optString("status") == "ready") break
            if (o.optString("status") in setOf("error", "failed", "not_found")) return@withContext emptyList()
            delay(4_000)
        }
        file?.let { subsFrom(it) } ?: emptyList()
    }

    /** Up to 5 English versions ("English · Alt", "English · Alt 2"…) and one of each other language. */
    private fun subsFrom(f: JSONObject): List<Subtitle> {
        val a = JSONArray(get(BASE + f.optString("subtitles")))
        val count = HashMap<String, Int>()
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { Subtitle(it.optString("label"), it.optString("lang"), BASE + it.optString("src")) }
            .filter { it.url.endsWith(".vtt") || it.url.endsWith(".srt") }
            .mapNotNull { s ->
                val n = count.getOrDefault(s.lang, 0)
                if (n >= (if (s.lang == "en" || s.lang == I18n.subLang) 5 else 1)) return@mapNotNull null
                count[s.lang] = n + 1
                val name = java.util.Locale(s.lang).displayLanguage.ifBlank { s.label }
                s.copy(label = "$name · Alt" + if (n == 0) "" else " ${n + 1}")
            }
            .sortedBy { if (it.lang == I18n.subLang) 0 else if (it.lang == "en") 1 else 2 }
            .take(20)
    }
}
