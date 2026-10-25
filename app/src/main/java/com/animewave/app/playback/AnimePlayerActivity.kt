package com.animewave.app.playback

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Standalone playback surface; stream resolution stays outside the UI. */
class AnimePlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            useController = true
            keepScreenOn = true
        }
        setContentView(playerView)
        val url = intent.getStringExtra(EXTRA_STREAM_URL)
        if (!url.isNullOrBlank()) {
            playStream(
                ResolvedAnimeStream(
                    url = url,
                    title = intent.getStringExtra(EXTRA_TITLE),
                    mimeType = Media3StreamBridge.mimeTypeFor(intent.getStringExtra(EXTRA_FORMAT)),
                ),
            )
        }
    }

    fun playStream(stream: ResolvedAnimeStream) {
        val activePlayer = player ?: ExoPlayer.Builder(this).build().also {
            player = it
            playerView.player = it
            it.repeatMode = Player.REPEAT_MODE_OFF
        }
        activePlayer.setMediaSource(Media3StreamBridge.createMediaSource(stream))
        activePlayer.prepare()
        activePlayer.playWhenReady = true
    }

    override fun onStop() {
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_STREAM_URL = "com.animewave.app.playback.STREAM_URL"
        const val EXTRA_TITLE = "com.animewave.app.playback.TITLE"
        const val EXTRA_FORMAT = "com.animewave.app.playback.FORMAT"
    }
}
