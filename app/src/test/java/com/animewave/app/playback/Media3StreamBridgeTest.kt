package com.animewave.app.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class Media3StreamBridgeTest {
    @Test
    fun mapsCommonStreamFormatsToMedia3MimeTypes() {
        assertEquals(MimeTypes.APPLICATION_M3U8, Media3StreamBridge.mimeTypeFor("m3u8"))
        assertEquals(MimeTypes.APPLICATION_M3U8, Media3StreamBridge.mimeTypeFor("HLS"))
        assertEquals(MimeTypes.APPLICATION_MPD, Media3StreamBridge.mimeTypeFor("mpd"))
        assertEquals(MimeTypes.APPLICATION_MPD, Media3StreamBridge.mimeTypeFor("DASH"))
        assertEquals(MimeTypes.VIDEO_MP4, Media3StreamBridge.mimeTypeFor("mp4"))
    }

    @Test
    fun returnsNullForUnknownOrMissingFormat() {
        assertNull(Media3StreamBridge.mimeTypeFor("webm"))
        assertNull(Media3StreamBridge.mimeTypeFor(null))
        assertNull(Media3StreamBridge.mimeTypeFor(""))
    }

    @Test
    fun acceptsValidHttpAndHttpsStreamUrls() {
        assertTrue(Media3StreamBridge.isSupportedStreamUrl("https://cdn.example/video.m3u8"))
        assertTrue(Media3StreamBridge.isSupportedStreamUrl("http://192.0.2.10/video.mp4"))
    }

    @Test
    fun rejectsUnsupportedOrMalformedStreamUrls() {
        assertFalse(Media3StreamBridge.isSupportedStreamUrl("file:///sdcard/video.mp4"))
        assertFalse(Media3StreamBridge.isSupportedStreamUrl("javascript:alert(1)"))
        assertFalse(Media3StreamBridge.isSupportedStreamUrl("https:///missing-host"))
        assertFalse(Media3StreamBridge.isSupportedStreamUrl("https://user:password@cdn.example/video.mp4"))
        assertFalse(Media3StreamBridge.isSupportedStreamUrl("not a url"))
    }
}
