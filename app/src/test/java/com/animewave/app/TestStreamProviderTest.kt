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
}
