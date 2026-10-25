package com.animewave.app.playback

import android.net.Uri
import com.animewave.app.ConsumetClient
import java.io.IOException

/**
 * Adapter for AnimeWave's existing user-configured Consumet-compatible API.
 *
 * This provides a working source-resolution seam while the separate Aniyomi
 * host runtime remains unintegrated. The caller supplies the episode's source
 * ID (the value after the "consumet:" prefix), not a UI title.
 */
class ConsumetStreamResolver(
    private val client: ConsumetClient,
) : AnimeStreamResolver {
    override suspend fun resolve(episodeUrl: String): ResolvedAnimeStream {
        val episodeId = episodeUrl.removePrefix("consumet:").trim()
        if (episodeId.isBlank() || episodeId.contains("://")) {
            throw IOException("Expected a Consumet episode ID")
        }
        val url = client.getPlayableUrl(episodeId)
        val uri = Uri.parse(url)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw IOException("The source did not return a valid HTTPS stream")
        }
        val path = uri.path.orEmpty().substringAfterLast('/', "")
        val format = path.substringAfterLast('.', "").lowercase()
        return ResolvedAnimeStream(
            url = url,
            mimeType = Media3StreamBridge.mimeTypeFor(format),
            title = episodeId,
        )
    }
}
