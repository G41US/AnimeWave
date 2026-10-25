package com.animewave.app.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
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
}
