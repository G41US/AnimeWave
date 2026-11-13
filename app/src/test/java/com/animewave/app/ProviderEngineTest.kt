package com.animewave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking
import androidx.media3.common.MimeTypes

class ProviderEngineTest {
    @Test fun extractsEpisodeNumbers() {
        assertEquals(2, EpisodeMatcher.episodeNumber("Episode 02"))
        assertEquals(10, EpisodeMatcher.episodeNumber("Show - Ep 10 [1080p]"))
        assertEquals(3, EpisodeMatcher.episodeNumber("Show 1x03"))
        assertNull(EpisodeMatcher.episodeNumber("Special OVA"))
    }

    @Test fun normalizesEpisodeTitles() {
        assertEquals("show", EpisodeMatcher.normalize("Show Episode 02"))
        assertEquals("show", EpisodeMatcher.normalize("Show - Ep 2"))
    }

    @Test fun providerDiscoveryGroupsEpisodesAndRejectsUnapprovedStreams() = runBlocking {
        val anime = Anime(
            id = 77, title = "Test Anime", cover = null, coverXL = null, banner = null,
            accent = null, genres = emptyList(), year = null, format = "TV", episodes = 2,
            score = null, synopsis = "", watchLinks = emptyList(), streamingEpisodes = emptyList(),
        )
        val provider = object : AnimeProvider {
            override val id = "test-provider"
            override val displayName = "Test Provider"
            override suspend fun searchEpisodes(anime: Anime) = listOf(
                ProviderEpisode(id, displayName, anime.id, "Episode 1", 1, null, "https://example.com/ep1"),
                ProviderEpisode(id, displayName, anime.id, "Episode 2", 2, null, "https://example.com/ep2"),
            )
            override suspend fun resolve(episode: ProviderEpisode) =
                ProviderStream("https://media.example/ep1.m3u8", displayName, rightsConfirmed = false)
        }
        ProviderManager.register(provider)
        try {
            val result = ProviderManager.discover(anime)
            assertEquals(2, result.groups.size)
            assertEquals(2, result.providers.first { it.providerId == provider.id }.episodeCount)
            assertNull(ProviderManager.resolve(result.groups.first().episodes.first()))
        } finally {
            ProviderManager.unregister(provider.id)
        }
    }

    @Test fun subtitleExtensionsMapToMedia3MimeTypes() {
        assertEquals(MimeTypes.TEXT_VTT, SubtitleManager.mimeType("https://example.com/captions.vtt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleManager.mimeType("https://example.com/captions.srt"))
        assertNull(SubtitleManager.mimeType("https://example.com/captions.txt"))
    }

    @Test fun progressIsBounded() {
        val record = PlaybackRecord("content://media/1", "Episode 1", 150, 100, 1)
        assertEquals(1f, record.progress)
    }
}
