package com.animewave.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestStreamProviderTest {
    @Test
    fun resolvesMuxHlsTestEpisode() = runBlocking {
        val provider = TestStreamProvider()
        val stream = provider.resolve(
            ProviderEpisode(
                providerId = "test-mux",
                providerName = "Mux HLS test stream",
                animeId = 1,
                title = "Mux HLS test",
                episodeNumber = 1,
                thumbnail = null,
                pageUrl = "https://test-streams.mux.dev/",
            )
        )

        assertNotNull(stream)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", stream!!.url)
        assertTrue(stream.rightsConfirmed)
        assertEquals("application/vnd.apple.mpegurl", stream.mimeType)
    }

    @Test
    fun ignoresEpisodesFromOtherProviders() = runBlocking {
        val stream = TestStreamProvider().resolve(
            ProviderEpisode(
                providerId = "other",
                providerName = "Other provider",
                animeId = 1,
                title = "Some episode",
                episodeNumber = 1,
                thumbnail = null,
                pageUrl = "https://example.com/episode",
            )
        )

        assertNull(stream)
    }
    @Test
    fun searchEpisodesReturnsDiscoveryShape() = runBlocking {
        val anime = Anime(
            id = 1, title = "Test", cover = null, coverXL = null, banner = null,
            accent = null, genres = emptyList(), year = null, format = "TV", episodes = 1,
            score = null, synopsis = "", watchLinks = emptyList(), streamingEpisodes = emptyList()
        )
        val ep = TestStreamProvider().searchEpisodes(anime).single()
        assertEquals("test-mux", ep.providerId)
        assertEquals("https://test-streams.mux.dev/", ep.pageUrl)
        assertEquals(1, ep.episodeNumber)
    }
}
