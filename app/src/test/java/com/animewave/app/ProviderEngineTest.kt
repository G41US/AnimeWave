package com.animewave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
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


    @Test fun resolverAcceptsOnlyAuthorizedHttpsStreamsWithSupportedMimeTypes() = runBlocking {
        val anime = Anime(
            id = 78, title = "Resolver Test", cover = null, coverXL = null, banner = null,
            accent = null, genres = emptyList(), year = null, format = "TV", episodes = 1,
            score = null, synopsis = "", watchLinks = emptyList(), streamingEpisodes = emptyList(),
        )
        val provider = object : AnimeProvider {
            override val id = "resolver-test"
            override val displayName = "Resolver Test"
            override suspend fun searchEpisodes(anime: Anime) = emptyList<ProviderEpisode>()
            override suspend fun resolve(episode: ProviderEpisode) = when (episode.title) {
                "valid" -> ProviderStream("https://media.example/video.m3u8", displayName, true, "application/vnd.apple.mpegurl; charset=utf-8")
                "insecure" -> ProviderStream("http://media.example/video.mp4", displayName, true, "video/mp4")
                "unsupported" -> ProviderStream("https://media.example/file.bin", displayName, true, "application/octet-stream")
                else -> null
            }
        }
        ProviderManager.register(provider)
        try {
            fun episode(title: String) = ProviderEpisode(provider.id, provider.displayName, anime.id, title, 1, null, "https://example.com/episode")
            assertEquals("https://media.example/video.m3u8", ProviderManager.resolve(episode("valid"))?.url)
            assertNull(ProviderManager.resolve(episode("insecure")))
            assertNull(ProviderManager.resolve(episode("unsupported")))
        } finally {
            ProviderManager.unregister(provider.id)
        }
    }

    @Test fun resolverPropagatesCoroutineCancellation() = runBlocking {
        val provider = object : AnimeProvider {
            override val id = "cancel-test"
            override val displayName = "Cancellation Test"
            override suspend fun searchEpisodes(anime: Anime) = emptyList<ProviderEpisode>()
            override suspend fun resolve(episode: ProviderEpisode): ProviderStream? {
                throw CancellationException("test cancellation")
            }
        }
        ProviderManager.register(provider)
        try {
            val episode = ProviderEpisode(provider.id, provider.displayName, 1, "Episode 1", 1, null, "https://example.com/ep1")
            var propagated = false
            try {
                ProviderManager.resolve(episode)
            } catch (_: CancellationException) {
                propagated = true
            }
            assertEquals(true, propagated)
        } finally {
            ProviderManager.unregister(provider.id)
        }
    }

    @Test fun subtitleExtensionsMapToMedia3MimeTypes() {
        assertEquals(MimeTypes.TEXT_VTT, SubtitleManager.mimeType("https://example.com/captions.vtt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleManager.mimeType("https://example.com/captions.srt"))
        assertNull(SubtitleManager.mimeType("https://example.com/captions.txt"))
    }

    @Test fun externalSourceSearchIsScopedToItsConfiguredHost() {
        val url = AnimeSourceRegistry.searchUrl("animepahe", "Frieren Beyond Journey End", "Episode 02")
        assertEquals(true, url.startsWith("https://www.google.com/search?q="))
        assertEquals(true, url.contains("site%3Aanimepahe.ru"))
        assertEquals("https://anilist.co", AnimeSourceRegistry.searchUrl("listed", "Frieren", "Episode 2"))
        assertEquals(true, AnimeSourceRegistry.searchUrl("animepahe", "Frieren").contains("Frieren"))
    }

    @Test fun progressIsBounded() {
        val record = PlaybackRecord("content://media/1", "Episode 1", 150, 100, 1)
        assertEquals(1f, record.progress)
    }
}
