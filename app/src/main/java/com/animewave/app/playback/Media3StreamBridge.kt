package com.animewave.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource

/** Bridges a provider-resolved stream to the app's existing Media3 player. */
object Media3StreamBridge {
    fun createMediaSource(stream: ResolvedAnimeStream): MediaSource {
        require(stream.url.startsWith("https://") || stream.url.startsWith("http://")) {
            "Stream URL must use HTTP or HTTPS"
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)
        val item = MediaItem.Builder()
            .setUri(Uri.parse(stream.url))
            .apply { stream.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) } }
            .build()
        return DefaultMediaSourceFactory(httpFactory).createMediaSource(item)
    }

    fun mimeTypeFor(format: String?): String? = when (format?.lowercase()) {
        "m3u8", "hls" -> MimeTypes.APPLICATION_M3U8
        "mpd", "dash" -> MimeTypes.APPLICATION_MPD
        "mp4" -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
