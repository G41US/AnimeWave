package com.animewave.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.net.URI

/** Bridges a provider-resolved stream to the app's existing Media3 player. */
object Media3StreamBridge {
    fun createMediaSource(stream: ResolvedAnimeStream): MediaSource {
        require(isSupportedStreamUrl(stream.url)) {
            "Stream URL must be a valid HTTP or HTTPS URL without embedded credentials"
        }
        val uri = Uri.parse(stream.url)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply { stream.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) } }
            .build()
        return DefaultMediaSourceFactory(httpFactory).createMediaSource(item)
    }

    /** Pure URL validation so it can be covered by local JVM unit tests. */
    fun isSupportedStreamUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        (uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    fun mimeTypeFor(format: String?): String? = when (format?.lowercase()) {
        "m3u8", "hls" -> MimeTypes.APPLICATION_M3U8
        "mpd", "dash" -> MimeTypes.APPLICATION_MPD
        "mp4" -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
