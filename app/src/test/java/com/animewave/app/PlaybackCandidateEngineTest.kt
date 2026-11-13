package com.animewave.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackCandidateEngineTest {
    @Test fun resolvesAndRanksAuthorizedSourcesAndDeduplicatesUrls() = runBlocking {
        val providers = listOf(
            candidateProvider("candidate-low", "Low", "https://media.example/low.m3u8", "480p"),
            candidateProvider("candidate-high", "High", "https://media.example/high.m3u8", "1080p"),
            candidateProvider("candidate-duplicate", "Duplicate", "https://MEDIA.example/high.m3u8", "720p"),
        )
        providers.forEach(ProviderManager::register)
        try {
            val episodes = providers.mapIndexed { index, provider ->
                ProviderEpisode(provider.id, provider.displayName, 1, "Episode 1", 1, null, "https://source.example/ep$index")
            }
            val group = ProviderEpisodeGroup("s1e1", 1, "Episode 1", episodes, 1)
            val candidates = PlaybackCandidateEngine.resolve(group)
            assertEquals(2, candidates.size)
            assertEquals("1080p", candidates.first().stream.quality)
            assertEquals("480p", candidates.last().stream.quality)
            assertEquals(candidates.first(), PlaybackCandidateEngine.nextUntried(candidates, emptySet()))
            assertEquals(candidates.last(), PlaybackCandidateEngine.nextUntried(candidates, setOf(candidates.first().stream.url)))
            assertNull(PlaybackCandidateEngine.nextUntried(candidates, candidates.map { it.stream.url }.toSet()))
        } finally {
            providers.forEach { ProviderManager.unregister(it.id) }
        }
    }

    private fun candidateProvider(
        id: String,
        name: String,
        url: String,
        quality: String,
    ) = object : AnimeProvider {
        override val id = id
        override val displayName = name
        override suspend fun searchEpisodes(anime: Anime) = emptyList<ProviderEpisode>()
        override suspend fun resolve(episode: ProviderEpisode) = ProviderStream(
            url = url,
            providerName = name,
            rightsConfirmed = true,
            mimeType = "application/vnd.apple.mpegurl",
            quality = quality,
        )
    }
}
