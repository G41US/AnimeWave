package com.animewave.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.media3.ui.TrackSelectionDialogBuilder
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

/** Queue helper: playback order is stable and indices are clamped for safe previous/next actions. */
object EpisodeQueue {
    fun nextIndex(size: Int, current: Int): Int? = (current + 1).takeIf { size > 0 && it in 0 until size }
    fun previousIndex(size: Int, current: Int): Int? = (current - 1).takeIf { size > 0 && it in 0 until size }
}

fun LocalEpisode<Uri>.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(video.ref)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(video.name.substringBeforeLast('.')).build())
    .setSubtitleConfigurations(listOfNotNull(subtitle?.let {
        MediaItem.SubtitleConfiguration.Builder(it.ref).setMimeType(subtitleMime(it.name))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
    })).build()

/**
 * Fullscreen queue player. Media3 advances automatically at item completion. Its native CC menu
 * selects subtitle tracks; the track dialog exposes available video renditions when the stream
 * advertises them. A stream with one rendition cannot offer a quality switch.
 */
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
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(requestHeaders)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build().apply {
                setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), startPositionMs)
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
                playWhenReady = true
            }
    }
    val latestProgress by rememberUpdatedState(onProgress)
    val controlsFocus = remember { FocusRequester() }
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
                    val factory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
                        .setDefaultRequestProperties(fallback.requestHeaders)
                    player.setMediaSource(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, factory))
                        .createMediaSource(fallback.item))
                    player.prepare()
                    player.playWhenReady = true
                    error = null
                } else error = e.errorCodeName
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { error = null }
        }
        val pauseOnStop = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        player.addListener(listener)
        lifecycle.addObserver(pauseOnStop)
        val bars = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            val item = player.currentMediaItem
            val uri = item?.localConfiguration?.uri?.toString()
            if (uri != null) {
                val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Video"
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                latestProgress(uri, title, player.currentPosition.coerceAtLeast(0L), duration)
            }
            lifecycle.removeObserver(pauseOnStop)
            player.removeListener(listener)
            player.release()
            bars?.show(WindowInsetsCompat.Type.systemBars())
            oldOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = {
            PlayerView(it).apply {
                this.player = player
                setShowSubtitleButton(true)
                setShowNextButton(items.size > 1)
                setShowPreviousButton(items.size > 1)
                keepScreenOn = true
                isFocusable = true
                isFocusableInTouchMode = true
                post { requestFocus() }
            }
        }, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xCC101018)).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Episode ${(player.currentMediaItemIndex + 1).coerceAtLeast(1)} of ${items.size}",
                color = Color.White, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = player.hasPreviousMediaItem(), onClick = { player.seekToPreviousMediaItem() }) { Text("Previous") }
                Spacer(Modifier.width(12.dp))
                Button(enabled = player.hasNextMediaItem(), onClick = { player.seekToNextMediaItem() }) { Text("Next") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    TrackSelectionDialogBuilder(context, "Video quality", player, C.TRACK_TYPE_VIDEO).build().show()
                }) { Text("Quality") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    TrackSelectionDialogBuilder(context, "Subtitles", player, C.TRACK_TYPE_TEXT).build().show()
                }) { Text("Subtitles") }
            }
        }
        error?.let {
            Text("Can't play this video ($it). Press Back to return.", color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).background(Color(0xCC000000)).padding(16.dp))
        }
    }
}
