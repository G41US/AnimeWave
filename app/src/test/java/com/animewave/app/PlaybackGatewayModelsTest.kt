package com.animewave.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGatewayModelsTest {
    @Test fun parsesSessionSourcesAndSubtitleTracks() {
        val response = GatewaySessionResponse.parse(JSONObject(
            """
            {
              "id":"session-123",
              "expiresAt":"2026-11-14T11:05:50.818Z",
              "sources":[
                {"url":"/proxy/session-123/asset-1","quality":"Auto","isM3U8":true}
              ],
              "subtitles":[
                {"url":"/proxy/session-123/sub-1","lang":"en","label":"English"}
              ]
            }
            """.trimIndent()
        ))

        assertEquals("session-123", response.id)
        assertEquals("/proxy/session-123/asset-1", response.sources.single().url)
        assertTrue(response.sources.single().isM3U8)
        assertEquals("Auto", response.sources.single().quality)
        assertEquals("en", response.subtitles.single().language)
    }

    @Test fun malformedOptionalEntriesAreIgnoredAndMissingSourcesStayEmpty() {
        val response = GatewaySessionResponse.parse(
            JSONObject("""{"id":"empty","sources":[{},{"url":"/proxy/a","isM3U8":false}],"subtitles":[{}]}""")
        )

        assertEquals(1, response.sources.size)
        assertFalse(response.sources.single().isM3U8)
        assertTrue(response.subtitles.isEmpty())
    }

    @Test fun requestModelsMatchGatewayJsonContract() {
        val json = GatewaySessionRequest(
            title = "Test",
            episodeId = "episode-1",
            sources = listOf(GatewaySourceRequest("https://media.example/video.m3u8", "720p", true)),
            subtitles = listOf(GatewaySubtitleRequest("https://media.example/en.vtt", "en", "English")),
        ).toJson()

        assertEquals("episode-1", json.getString("episodeId"))
        assertTrue(json.getJSONArray("sources").getJSONObject(0).getBoolean("isM3U8"))
        assertEquals("en", json.getJSONArray("subtitles").getJSONObject(0).getString("lang"))
    }
}
