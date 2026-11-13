package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Internet Archive adapter limited to items with explicit, allow-listed open licenses. */
class InternetArchiveProvider : AnimeProvider {
    override val id = "internet-archive"
    override val displayName = "Internet Archive (licensed videos)"

    override suspend fun searchEpisodes(anime: Anime): List<ProviderEpisode> = withContext(Dispatchers.IO) {
        val title = escapeSolr(anime.title)
        val query = "(title:\"$title\" OR subject:\"$title\") AND mediatype:movies"
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&fl[]=title&fl[]=licenseurl&rows=10&page=1&output=json"
        val docs = getJson(url).optJSONObject("response")?.optJSONArray("docs")
            ?: return@withContext emptyList()
        buildList {
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val identifier = doc.optString("identifier")
                val titleText = doc.optString("title")
                if (identifier.isBlank() || titleText.isBlank() || !isAllowedLicense(doc.optString("licenseurl"))) continue
                add(ProviderEpisode(id, displayName, anime.id, titleText,
                    EpisodeMatcher.episodeNumber(titleText),
                    "https://archive.org/services/img/$identifier",
                    "https://archive.org/details/$identifier"))
            }
        }
    }

    override suspend fun resolve(episode: ProviderEpisode): ProviderStream? = withContext(Dispatchers.IO) {
        if (episode.providerId != id) return@withContext null
        val identifier = episode.pageUrl.substringAfterLast('/').takeIf { it.matches(Regex("[A-Za-z0-9_-]{5,80}")) }
            ?: return@withContext null
        val metadata = getJson("https://archive.org/metadata/$identifier")
        val item = metadata.optJSONObject("metadata") ?: return@withContext null
        if (!isAllowedLicense(item.optString("licenseurl"))) return@withContext null
        val files = metadata.optJSONArray("files") ?: return@withContext null
        val candidates = (0 until files.length()).mapNotNull { index ->
            val file = files.optJSONObject(index) ?: return@mapNotNull null
            val name = file.optString("name")
            val format = file.optString("format").lowercase()
            val mime = when {
                name.endsWith(".m3u8", true) || format.contains("hls") -> "application/vnd.apple.mpegurl"
                name.endsWith(".mpd", true) || format.contains("dash") -> "application/dash+xml"
                name.endsWith(".mp4", true) || format.contains("mpeg4") -> "video/mp4"
                else -> null
            } ?: return@mapNotNull null
            val height = Regex("(2160|1440|1080|720|576|480|360)p?", RegexOption.IGNORE_CASE)
                .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            Triple(name, mime, height)
        }
        val selected = candidates.sortedWith(compareByDescending<Triple<String, String, Int>> { it.second.contains("mpegurl") }
            .thenByDescending { it.third }).firstOrNull() ?: return@withContext null
        val encodedId = URLEncoder.encode(identifier, Charsets.UTF_8.name())
        val encodedFile = selected.first.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        ProviderStream("https://archive.org/download/$encodedId/$encodedFile", displayName, true,
            selected.second, selected.third.takeIf { it > 0 }?.let { "${it}p" } ?: "Archive source")
    }

    private fun escapeSolr(value: String) =
        value.replace(Regex("""[+\\-&|!(){}\\[\\]^"~*?:\\\\/]"""), " ")
            .replace(Regex("""\\s+"""), " ").trim()

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AnimeWave/1.0 (licensed archive client)")
            if (connection.responseCode !in 200..299) return JSONObject()
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    companion object {
        fun isAllowedLicense(value: String): Boolean {
            val license = value.trim().lowercase().trimEnd('/')
            return license in setOf(
                "https://creativecommons.org/publicdomain/zero/1.0",
                "https://creativecommons.org/licenses/by/4.0",
                "https://creativecommons.org/licenses/by/3.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "https://creativecommons.org/licenses/by-sa/3.0",
                "https://creativecommons.org/publicdomain/mark/1.0",
            ) || license.startsWith("https://creativecommons.org/publicdomain/mark/")
        }
    }
}
