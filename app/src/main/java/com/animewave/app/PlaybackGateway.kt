package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class GatewayPlaybackSession(
    val sourceUrl: String,
    val isHls: Boolean,
    val quality: String?,
    val subtitles: List<ProviderSubtitle>,
)

/** Optional proxy for streams already resolved by an authorized AnimeWave provider. */
object PlaybackGatewayClient {
    fun normalizeBaseUrl(value: String): String {
        val uri = URI(value.trim().trimEnd('/'))
        require(uri.scheme.equals("https", ignoreCase = true)) { "Playback service must use HTTPS." }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Enter a valid playback service URL."
        }
        return uri.toASCIIString().trimEnd('/')
    }

    suspend fun createSession(
        baseUrl: String,
        title: String,
        episodeId: String,
        stream: ProviderStream,
    ): GatewayPlaybackSession = withContext(Dispatchers.IO) {
        require(stream.rightsConfirmed) { "Only authorized streams can use the playback gateway." }
        val streamUri = URI(stream.url)
        require(streamUri.scheme.equals("https", true) && !streamUri.host.isNullOrBlank() && streamUri.userInfo == null) {
            "The stream URL must be HTTPS."
        }

        val base = normalizeBaseUrl(baseUrl)
        val payload = JSONObject()
            .put("title", title)
            .put("episodeId", episodeId)
            .put("sources", JSONArray().put(
                JSONObject()
                    .put("url", streamUri.toASCIIString())
                    .put("quality", stream.quality ?: "Auto")
                    .put("isM3U8", isHls(stream))
            ))
            .put("subtitles", JSONArray().apply {
                stream.subtitles.forEach { subtitle ->
                    put(JSONObject()
                        .put("url", subtitle.url)
                        .put("lang", subtitle.language ?: "und")
                        .put("label", subtitle.label ?: "Subtitles"))
                }
            })

        val connection = (URL("$base/api/sessions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            instanceFollowRedirects = false
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                "Playback service returned HTTP $code" +
                    (message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") + "."
            }

            val json = JSONObject(body)
            val sources = json.optJSONArray("sources") ?: JSONArray()
            require(sources.length() > 0) { "Playback service returned no playable source." }
            val source = sources.getJSONObject(0)
            val proxyUrl = resolveProxyUrl(base, source.getString("url"))
            val subtitlesJson = json.optJSONArray("subtitles") ?: JSONArray()
            val subtitles = (0 until subtitlesJson.length()).mapNotNull { index ->
                val item = subtitlesJson.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ProviderSubtitle(
                    url = resolveProxyUrl(base, url),
                    language = item.optString("lang").takeIf { it.isNotBlank() },
                    label = item.optString("label").takeIf { it.isNotBlank() },
                )
            }
            GatewayPlaybackSession(
                sourceUrl = proxyUrl,
                isHls = source.optBoolean("isM3U8", isHls(stream)),
                quality = source.optString("quality").takeIf { it.isNotBlank() },
                subtitles = subtitles,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveProxyUrl(base: String, path: String): String {
        val baseUri = URI("$base/")
        val resolved = baseUri.resolve(path)
        require(
            resolved.scheme.equals("https", true) &&
                resolved.host.equals(baseUri.host, true) &&
                resolved.port == baseUri.port &&
                resolved.userInfo == null
        ) { "Playback service returned an invalid proxy URL." }
        return resolved.toASCIIString()
    }

    private fun isHls(stream: ProviderStream): Boolean {
        val mime = stream.mimeType?.substringBefore(';')?.trim()?.lowercase()
        return mime == "application/x-mpegurl" ||
            mime == "application/vnd.apple.mpegurl" ||
            stream.url.substringBefore('?').substringBefore('#').endsWith(".m3u8", true)
    }
}
