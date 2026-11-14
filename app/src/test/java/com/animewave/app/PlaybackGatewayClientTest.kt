package com.animewave.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGatewayClientTest {
    @Test
    fun normalizeBaseUrlTrimsTrailingSlash() {
        assertEquals(
            "https://playback.example.com",
            PlaybackGatewayClient.normalizeBaseUrl("  https://playback.example.com///  "),
        )
    }

    @Test
    fun normalizeBaseUrlRejectsInsecureHttp() {
        val failure = runCatching {
            PlaybackGatewayClient.normalizeBaseUrl("http://playback.example.com")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun normalizeBaseUrlRejectsEmbeddedCredentials() {
        val failure = runCatching {
            PlaybackGatewayClient.normalizeBaseUrl("https://user:pass@playback.example.com")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun gatewayRejectsUnconfirmedStreamsBeforeNetworkAccess() = runBlocking {
        val failure = runCatching {
            PlaybackGatewayClient.createSession(
                baseUrl = "https://playback.example.com",
                title = "Test episode",
                episodeId = "episode-1",
                stream = ProviderStream(
                    url = "https://media.example.com/episode.m3u8",
                    providerName = "Test provider",
                    rightsConfirmed = false,
                ),
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
