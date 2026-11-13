package com.animewave.app

import java.net.URI

data class ResolvedPlayback(
    val uri: URI,
    val providerName: String,
    val quality: String?,
    val mimeType: String?,
    val subtitles: List<ProviderSubtitle>,
    val requestHeaders: Map<String, String> = emptyMap(),
)

object PlaybackResolver {
    suspend fun resolve(episode: ProviderEpisode): ResolvedPlayback? {
        val stream = ProviderManager.resolve(episode) ?: return null
        val uri = runCatching { URI(stream.url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return ResolvedPlayback(
            uri = uri,
            providerName = stream.providerName,
            quality = stream.quality,
            mimeType = stream.mimeType,
            subtitles = stream.subtitles,
            requestHeaders = stream.requestHeaders,
        )
    }
}
