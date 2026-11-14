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
        val payload = GatewaySessionRequest(
            title = title,
            episodeId = episodeId,
            sources = listOf(
                GatewaySourceRequest(
                    url = streamUri.toASCIIString(),
                    quality = stream.quality ?: "Auto",
                    isM3U8 = isHls(stream),
                )
            ),
            subtitles = stream.subtitles.map { subtitle ->
                GatewaySubtitleRequest(
                    url = subtitle.url,
                    language = subtitle.language ?: "und",
                    label = subtitle.label ?: "Subtitles",
                )
            },
        ).toJson()

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
            val parsed = GatewaySessionResponse.parse(json)
            val source = parsed.sources.firstOrNull()
                ?: throw IllegalStateException("Playback service returned no playable source.")
            val proxyUrl = resolveProxyUrl(base, source.url)
            val subtitles = parsed.subtitles.map { item ->
                ProviderSubtitle(
                    url = resolveProxyUrl(base, item.url),
                    language = item.language,
                    label = item.label,
                )
            }
            GatewayPlaybackSession(
                sourceUrl = proxyUrl,
                isHls = source.isM3U8,
                quality = source.quality,
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
