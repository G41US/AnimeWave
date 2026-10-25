package com.animewave.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource

/** Bridges a provider-resolved stream to the app's existing Media3 player. */
object Media3StreamBridge {
    /**
     * Creates a Media3 source for a resolved stream. URLs are validated before
     * building the HTTP data source so malformed or non-network URIs fail early.
     */
    fun createMediaSource(stream: ResolvedAnimeStream): MediaSource {
        val uri = Uri.parse(stream.url)
        require(
            (uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        ) {
            "Stream URL must be a valid HTTP or HTTPS URL without embedded credentials"
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)
        val item = MediaItem.Builder()
            .setUri(uri)
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
