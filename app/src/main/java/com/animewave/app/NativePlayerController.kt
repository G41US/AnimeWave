package com.animewave.app

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

/**
 * Native renderer for direct media URLs the app is allowed to play.
 * Provider pages with JavaScript-based players must be opened in the device's external browser; this controller handles direct media URLs only.
 */
class NativePlayerController(
    private val playerView: PlayerView,
) {
    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("AnimeWave/1.0 (Android)")
        .setAllowCrossProtocolRedirects(false)

    private val player: ExoPlayer = ExoPlayer.Builder(playerView.context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .also { playerView.player = it }

    fun play(media: DiscoveredMedia) {
        val uri = Uri.parse(media.url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "Only valid HTTPS media URLs can be played."
        }
        val itemBuilder = MediaItem.Builder().setUri(uri)
        when (media.kind) {
            MediaKind.HLS -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            MediaKind.DASH -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            MediaKind.PROGRESSIVE -> itemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }
        player.setMediaItem(itemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }

    fun pause() = player.pause()

    fun release() {
        playerView.player = null
        player.release()
    }
}
