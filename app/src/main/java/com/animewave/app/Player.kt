package com.animewave.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.media3.common.TrackSelectionOverride
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

/**
 * Fullscreen player with AnimeWave's own controls: tap (or press OK on a remote) to show them,
 * double-tap a side to skip ten seconds, Left/Right on a remote skip while the controls are hidden.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    items: List<MediaItem>, startIndex: Int, startPositionMs: Long = 0L,
    requestHeaders: Map<String, String> = emptyMap(),
    fallbacks: List<PlayerFallback> = emptyList(),
    onExit: () -> Unit,
    onProgress: (uri: String, title: String, positionMs: Long, durationMs: Long, artwork: String?, key: String?) -> Unit = { _, _, _, _, _, _ -> },
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
    fun report() {
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: uri.substringAfterLast('/').ifBlank { "Video" }
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        latestProgress(
            uri, title, player.currentPosition.coerceAtLeast(0L), duration,
            item.mediaMetadata.artworkUri?.toString(), item.mediaMetadata.extras?.getString(EPISODE_KEY),
        )
    }
    LaunchedEffect(player) {
        while (true) { report(); delay(5_000) }
    }
    BackHandler(onBack = onExit)

    // Player state mirrored into Compose for the controls.
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var hasPrevious by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var focusInside by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf<Pair<Int, Int>?>(null) } // seconds to serial, re-triggers the bubble
    var sheet by remember { mutableStateOf<Int?>(null) } // C.TRACK_TYPE_TEXT or C.TRACK_TYPE_VIDEO
    val playFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    fun refresh() {
        isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        buffering = player.playbackState == Player.STATE_BUFFERING
        position = player.currentPosition.coerceAtLeast(0L)
        duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        buffered = player.bufferedPosition.coerceAtLeast(0L)
        val meta = player.mediaMetadata
        title = meta.title?.toString()?.substringBefore(" · ")?.takeIf { it.isNotBlank() } ?: "Now playing"
        subtitle = meta.artist?.toString() ?: meta.title?.toString()?.substringAfter(" · ", "")?.takeIf { it.isNotBlank() }
        hasPrevious = player.hasPreviousMediaItem()
        hasNext = player.hasNextMediaItem()
    }
    fun show() { controls = true; poke++ }
    fun seekBy(seconds: Int) {
        val target = (player.currentPosition + seconds * 1000L).coerceIn(0L, (player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE))
        player.seekTo(target)
        skip = seconds to ((skip?.second ?: 0) + 1)
        refresh()
    }
    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
        refresh(); show()
    }

    LaunchedEffect(player, controls) {
        while (true) { refresh(); delay(if (controls) 250 else 1000) }
    }
    // Controls fade away after a few quiet seconds while the video plays.
    LaunchedEffect(controls, poke, isPlaying, sheet) {
        if (controls && isPlaying && sheet == null) { delay(4_000); controls = false }
    }
    LaunchedEffect(skip) { if (skip != null) { delay(700); skip = null } }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(controls) { if (!controls) runCatching { rootFocus.requestFocus() } }

    DisposableEffect(player) {
        var nextFallback = 0
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val fallback = fallbacks.getOrNull(nextFallback++)
                if (fallback != null) {
                    val resumeAt = player.currentPosition.coerceAtLeast(0L)
                    val fallbackHttpFactory = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(false)
                        .setDefaultRequestProperties(fallback.requestHeaders)
                    val fallbackDataSource = DefaultDataSource.Factory(context, fallbackHttpFactory)
                    val fallbackSource = DefaultMediaSourceFactory(fallbackDataSource)
                        .createMediaSource(fallback.item)
                    player.setMediaSource(fallbackSource, resumeAt)
                    player.prepare()
                    player.playWhenReady = true
                    error = null
                } else {
                    error = e.errorCodeName
                    controls = true
                }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { error = null; refresh() }
            override fun onEvents(p: Player, events: Player.Events) { refresh() }
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
            report()
            lifecycle.removeObserver(pauseOnStop)
            player.removeListener(listener)
            player.release()
            bars?.show(WindowInsetsCompat.Type.systemBars())
            oldOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause, Key.Spacebar -> { togglePlay(); true }
                    Key.MediaPlay -> { player.play(); show(); true }
                    Key.MediaPause -> { player.pause(); show(); true }
                    Key.MediaFastForward -> { seekBy(10); true }
                    Key.MediaRewind -> { seekBy(-10); true }
                    Key.MediaNext -> { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); show(); true }
                    Key.MediaPrevious -> { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); show(); true }
                    else -> when {
                        // Hidden controls: Left/Right skip, anything else brings the controls back.
                        !controls && e.key == Key.DirectionLeft -> { seekBy(-10); true }
                        !controls && e.key == Key.DirectionRight -> { seekBy(10); true }
                        !controls && e.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown) -> {
                            show(); runCatching { playFocus.requestFocus() }; true
                        }
                        // Controls showing but nothing focused yet (first remote press): start on Play.
                        controls && !focusInside && e.key in setOf(Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight) -> {
                            poke++; runCatching { playFocus.requestFocus() }; true
                        }
                        else -> { poke++; false }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (controls) controls = false else show() },
                    onDoubleTap = { o -> seekBy(if (o.x < size.width / 2f) -10 else 10) },
                )
            },
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering && !controls) CircularProgressIndicator(
            Modifier.align(Alignment.Center).size(56.dp), color = Violet, strokeWidth = 3.dp, trackColor = Color.Transparent,
        )
        skip?.let { (seconds, _) ->
            SkipBubble(seconds, Modifier.align(if (seconds < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 56.dp))
        }
        AnimatedVisibility(controls, enter = fadeIn(tween(180)), exit = fadeOut(tween(260))) {
            PlayerControls(
                title = title, subtitle = subtitle, isPlaying = isPlaying, buffering = buffering,
                positionMs = position, durationMs = duration, bufferedMs = buffered,
                hasPrevious = hasPrevious, hasNext = hasNext, playFocus = playFocus,
                onBack = onExit, onPlayPause = ::togglePlay,
                onSeekTo = { player.seekTo(it); refresh(); show() },
                onSkip = { seekBy(it); show() },
                onPrevious = { player.seekToPreviousMediaItem(); show() },
                onNext = { player.seekToNextMediaItem(); show() },
                onSubtitles = { sheet = C.TRACK_TYPE_TEXT },
                onQuality = { sheet = C.TRACK_TYPE_VIDEO },
                onFocusInside = { focusInside = it },
            )
        }
        error?.let {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 460.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Panel)
                    .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp)).padding(22.dp),
            ) {
                Text("Can't play this video", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("The source didn't respond ($it). Try another source or episode.", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                PrimaryButton("Back", Icons.Filled.ArrowBack, onClick = onExit)
            }
        }
    }

    sheet?.let { type ->
        val options = remember(type, poke, sheet) { trackOptions(player, type) }
        TrackSheet(
            title = if (type == C.TRACK_TYPE_TEXT) "Subtitles" else "Quality",
            options = options,
            empty = if (type == C.TRACK_TYPE_TEXT) "This video has no subtitle tracks." else "This stream has a single quality.",
            onPick = { option -> applyTrack(player, type, option.id); sheet = null; show() },
            onDismiss = { sheet = null; show() },
        )
    }
}

/** Builds the sheet entries: "Off"/"Auto" first, then each playable track. */
@OptIn(UnstableApi::class)
private fun trackOptions(player: Player, type: Int): List<TrackOption> {
    val groups = player.currentTracks.groups.filter { it.type == type }
    val tracks = groups.flatMapIndexed { g, group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { t ->
            val f = group.getTrackFormat(t)
            val label = if (type == C.TRACK_TYPE_VIDEO) {
                if (f.height > 0) "${f.height}p" else f.label ?: "Track ${t + 1}"
            } else {
                f.label ?: f.language?.let { java.util.Locale.forLanguageTag(it).displayName.ifBlank { it } } ?: "Track ${t + 1}"
            }
            val detail = if (type == C.TRACK_TYPE_VIDEO && f.bitrate > 0) "%.1f Mbps".format(f.bitrate / 1_000_000f) else null
            TrackOption("$g:$t", label, detail, group.isTrackSelected(t))
        }
    }.let { list -> if (type == C.TRACK_TYPE_VIDEO) list.sortedByDescending { it.label.removeSuffix("p").toIntOrNull() ?: 0 } else list }
    if (tracks.isEmpty()) return emptyList()
    val params = player.trackSelectionParameters
    val off = type == C.TRACK_TYPE_TEXT && params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val forced = params.overrides.keys.any { it.type == type }
    val first = if (type == C.TRACK_TYPE_TEXT) TrackOption("off", "Off", null, off || tracks.none { it.selected })
    else TrackOption("auto", "Auto", "Best for your connection", !forced)
    return listOf(first) + tracks.map { if (first.selected) it.copy(selected = false) else it }
}

@OptIn(UnstableApi::class)
private fun applyTrack(player: Player, type: Int, id: String) {
    val builder = player.trackSelectionParameters.buildUpon()
    when (id) {
        "off" -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        "auto" -> builder.clearOverridesOfType(type)
        else -> {
            val (g, t) = id.split(':').map(String::toInt)
            val group = player.currentTracks.groups.filter { it.type == type }.getOrNull(g) ?: return
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, t))
        }
    }
    player.trackSelectionParameters = builder.build()
}
