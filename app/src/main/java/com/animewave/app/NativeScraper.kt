package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Finds media URLs that a page explicitly publishes in its HTML (video/source tags and
 * direct media links). It does not execute scripts, defeat access controls, or infer hidden
 * CDN URLs. Dynamic players are not supported by the native HTML parser; open source links in the device's external browser.
 */
class NativeScraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    suspend fun discoverPublicMedia(pageUrl: String): List<DiscoveredMedia> =
        withContext(Dispatchers.IO) {
            requireHttpsUrl(pageUrl)
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "AnimeWave/1.0 (Android)")
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Source returned HTTP ${response.code}")
                val body = response.body?.string() ?: return@withContext emptyList()
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.isNotBlank() && !contentType.contains("html", ignoreCase = true)) {
                    return@withContext listOfNotNull(mediaFromUrl(response.request.url.toString()))
                }

                val doc = Jsoup.parse(body, response.request.url.toString())
                val found = linkedMapOf<String, DiscoveredMedia>()
                doc.select("video[src], video source[src], audio[src], audio source[src], a[href]")
                    .forEach { element ->
                        val raw = when {
                            element.hasAttr("src") -> element.absUrl("src")
                            else -> element.absUrl("href")
                        }
                        mediaFromUrl(raw)?.let { found.putIfAbsent(it.url, it) }
                    }
                found.values.toList()
            }
        }

    private fun mediaFromUrl(rawUrl: String): DiscoveredMedia? {
        if (rawUrl.isBlank()) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) return null
        val path = uri.path.orEmpty().lowercase()
        val type = when {
            path.endsWith(".m3u8") -> MediaKind.HLS
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MediaKind.PROGRESSIVE
            path.endsWith(".mpd") -> MediaKind.DASH
            else -> return null
        }
        return DiscoveredMedia(uri.toASCIIString(), type)
    }

    private fun requireHttpsUrl(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo.isNullOrBlank()) {
            "Only valid HTTPS pages can be checked."
        }
    }
}

enum class MediaKind { HLS, PROGRESSIVE, DASH }

data class DiscoveredMedia(val url: String, val kind: MediaKind)
