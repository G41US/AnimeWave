package com.animewave.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: uri.lastPathSegment ?: "video"

private fun subtitleMime(name: String) = when (extension(name)) {
    "vtt" -> MimeTypes.TEXT_VTT
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

fun LocalEpisode<Uri>.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(video.ref)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(video.name.substringBeforeLast('.')).build())
    .setSubtitleConfigurations(listOfNotNull(subtitle?.let {
        MediaItem.SubtitleConfiguration.Builder(it.ref)
            .setMimeType(subtitleMime(it.name))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }))
    .build()

/** Fullscreen player. Next/previous move through [items]; the CC button picks subtitle tracks. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(items: List<MediaItem>, startIndex: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(items, startIndex, 0)
            prepare()
            playWhenReady = true
        }
    }
    BackHandler(onBack = onExit)

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = e.errorCodeName }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { error = null }
        }
        val pauseOnStop = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        player.addListener(listener)
        lifecycle.addObserver(pauseOnStop)
        // Phones: landscape + hide system bars while watching. No effect on TV.
        val bars = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            lifecycle.removeObserver(pauseOnStop)
            player.removeListener(listener)
            player.release()
            bars?.show(WindowInsetsCompat.Type.systemBars())
            oldOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    setShowSubtitleButton(true)
                    keepScreenOn = true
                    isFocusable = true
                    post { requestFocus() } // D-pad and remote media keys go to the player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let {
            Text(
                "Can't play this video ($it). Press Back to return.",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).background(Color(0xCC000000)).padding(16.dp),
            )
        }
    }
}
