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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: uri.lastPathSegment ?: "video"

private fun subtitleMime(name: String) = when (extension(name)) {
    "vtt" -> MimeTypes.TEXT_VTT
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

data class PlayerFallback(val item: MediaItem, val requestHeaders: Map<String, String> = emptyMap())

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
fun PlayerScreen(
    items: List<MediaItem>, startIndex: Int, startPositionMs: Long = 0L,
    requestHeaders: Map<String, String> = emptyMap(),
    fallbacks: List<PlayerFallback> = emptyList(),
    onExit: () -> Unit, onProgress: (String, String, Long, Long) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember(items, requestHeaders, fallbacks) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(requestHeaders)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItems(items, startIndex, startPositionMs)
                prepare()
                playWhenReady = true
            }
    }
    val latestProgress by rememberUpdatedState(onProgress)
    LaunchedEffect(player) {
        while (true) {
            val item = player.currentMediaItem
            val uri = item?.localConfiguration?.uri?.toString()
            if (uri != null) {
                val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                    ?: uri.substringAfterLast('/').ifBlank { "Video" }
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                latestProgress(uri, title, player.currentPosition.coerceAtLeast(0L), duration)
            }
            delay(5_000)
        }
    }
    BackHandler(onBack = onExit)

    DisposableEffect(player) {
        var nextFallback = 0
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val fallback = fallbacks.getOrNull(nextFallback++)
                if (fallback != null) {
                    val fallbackHttpFactory = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(false)
                        .setDefaultRequestProperties(fallback.requestHeaders)
                    val fallbackDataSource = DefaultDataSource.Factory(context, fallbackHttpFactory)
                    val fallbackSource = DefaultMediaSourceFactory(fallbackDataSource)
                        .createMediaSource(fallback.item)
                    player.setMediaSource(fallbackSource)
                    player.prepare()
                    player.playWhenReady = true
                    error = null
                } else {
                    error = e.errorCodeName
                }
            }
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
            val currentItem = player.currentMediaItem
            val currentUri = currentItem?.localConfiguration?.uri?.toString()
            if (currentUri != null) {
                val currentTitle = currentItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Video"
                val currentDuration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                latestProgress(currentUri, currentTitle, player.currentPosition.coerceAtLeast(0L), currentDuration)
            }
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
