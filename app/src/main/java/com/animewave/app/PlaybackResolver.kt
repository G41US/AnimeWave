package com.animewave.app

import java.net.URI

data class ResolvedPlayback(
    val uri: URI,
    val providerName: String,
    val quality: String?,
    val mimeType: String?,
    val subtitles: List<ProviderSubtitle>,
)

object PlaybackResolver {
    suspend fun resolve(episode: ProviderEpisode): ResolvedPlayback? {
        val stream = ProviderManager.resolve(episode) ?: return null
        val uri = runCatching { URI(stream.url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return ResolvedPlayback(uri, stream.providerName, stream.quality, stream.mimeType, stream.subtitles)
    }
}
