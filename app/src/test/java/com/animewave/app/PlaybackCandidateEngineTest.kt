package com.animewave.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCandidateEngineTest {
    @Test fun resolvesAndRanksAuthorizedSourcesAndDeduplicatesUrls() = runBlocking {
        val providers = listOf(
            candidateProvider("candidate-low", "Low", "https://media.example/low.m3u8", "480p"),
            candidateProvider("candidate-high", "High", "https://media.example/high.m3u8", "1080p"),
            candidateProvider("candidate-duplicate", "Duplicate", "https://MEDIA.example/high.m3u8", "720p"),
        )
        providers.forEach(ProviderManager::registerStreamProvider)
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

    @Test fun rejectsUnconfirmedRightsAndUnsupportedMimeTypes() = runBlocking {
        val unconfirmed = candidateProvider(
            "candidate-unconfirmed", "Unconfirmed", "https://media.example/no-rights.mp4", "1080p",
            rightsConfirmed = false,
        )
        val unsupported = candidateProvider(
            "candidate-unsupported", "Unsupported", "https://media.example/unsupported.mkv", "1080p",
            mimeType = "video/x-matroska",
        )
        ProviderManager.registerStreamProvider(unconfirmed)
        ProviderManager.registerStreamProvider(unsupported)
        try {
            val episodes = listOf(unconfirmed, unsupported).map {
                ProviderEpisode(it.id, it.displayName, 1, "Episode 2", 2, null, "https://source.example/${it.id}")
            }
            val candidates = PlaybackCandidateEngine.resolve(
                ProviderEpisodeGroup("s1e2", 2, "Episode 2", episodes, 1)
            )
            assertTrue(candidates.isEmpty())
        } finally {
            ProviderManager.unregister(unconfirmed.id)
            ProviderManager.unregister(unsupported.id)
        }
    }

    @Test fun prefersHlsWhenQualityAndSubtitleCountsAreEqual() = runBlocking {
        val mp4 = candidateProvider(
            "candidate-mp4", "MP4", "https://media.example/video.mp4", "720p",
            mimeType = "video/mp4",
        )
        val hls = candidateProvider(
            "candidate-hls", "HLS", "https://media.example/video.m3u8", "720p",
            mimeType = "application/vnd.apple.mpegurl",
        )
        ProviderManager.registerStreamProvider(mp4)
        ProviderManager.registerStreamProvider(hls)
        try {
            val episodes = listOf(mp4, hls).map {
                ProviderEpisode(it.id, it.displayName, 1, "Episode 3", 3, null, "https://source.example/${it.id}")
            }
            val candidates = PlaybackCandidateEngine.resolve(
                ProviderEpisodeGroup("s1e3", 3, "Episode 3", episodes, 1)
            )
            assertEquals("HLS", candidates.first().stream.providerName)
        } finally {
            ProviderManager.unregister(mp4.id)
            ProviderManager.unregister(hls.id)
        }
    }

    @Test fun filtersNullProvidersAndReturnsOnlyResolvableCandidates() = runBlocking {
        val nullProvider = object : StreamProvider {
            override val id = "candidate-null"
            override val displayName = "Null provider"
            override suspend fun searchEpisodes(anime: Anime) = emptyList<ProviderEpisode>()
            override suspend fun resolve(episode: ProviderEpisode): ProviderStream? = null
        }
        val hlsUrl = "https://media.example/authorized-test.m3u8"
        val validProvider = candidateProvider(
            "candidate-valid", "Valid HLS", hlsUrl, "Auto",
        )
        ProviderManager.registerStreamProvider(nullProvider)
        ProviderManager.registerStreamProvider(validProvider)
        try {
            val episodes = listOf(nullProvider, validProvider).map {
                ProviderEpisode(it.id, it.displayName, 1, "Episode 1", 1, null, "https://source.example/${it.id}")
            }
            val group = ProviderEpisodeGroup("test-resolvable", 1, "Episode 1", episodes)
            val candidates = PlaybackCandidateEngine.resolve(group)
            assertEquals(1, candidates.size)
            assertEquals(hlsUrl, candidates.single().stream.url)
            assertNull(PlaybackCandidateEngine.nextUntried(candidates, setOf(hlsUrl)))
        } finally {
            ProviderManager.unregister(nullProvider.id)
            ProviderManager.unregister(validProvider.id)
        }
    }

    private fun candidateProvider(
        id: String,
        name: String,
        url: String,
        quality: String,
        rightsConfirmed: Boolean = true,
        mimeType: String = "application/vnd.apple.mpegurl",
    ) = object : StreamProvider {
        override val id = id
        override val displayName = name
        override suspend fun searchEpisodes(anime: Anime) = emptyList<ProviderEpisode>()
        override suspend fun resolve(episode: ProviderEpisode) = ProviderStream(
            url = url,
            providerName = name,
            rightsConfirmed = rightsConfirmed,
            mimeType = mimeType,
            quality = quality,
        )
    }
}
