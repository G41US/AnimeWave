package com.animewave.app

class TestStreamProvider : StreamProvider {
    override val id = "test-mux"
    override val displayName = "Mux HLS test stream"

    override suspend fun searchEpisodes(anime: Anime): List<ProviderEpisode> =
        listOf(
            ProviderEpisode(
                providerId = id,
                providerName = displayName,
                animeId = anime.id,
                title = "Mux HLS test",
                episodeNumber = 1,
                thumbnail = null,
                pageUrl = "https://test-streams.mux.dev/",
            )
        )

    override suspend fun resolve(episode: ProviderEpisode): ProviderStream? {
        if (episode.providerId != id) return null
        return ProviderStream(
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            providerName = displayName,
            rightsConfirmed = true,
            mimeType = "application/vnd.apple.mpegurl",
            quality = "Auto",
        )
    }
}
