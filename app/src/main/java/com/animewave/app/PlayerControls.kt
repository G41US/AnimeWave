package com.animewave.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small set of player glyphs (Material icon paths) that the core icon pack doesn't include. */
object PlayerIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.White))
        .build()

    val Pause = icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val SkipNext = icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val SkipPrevious = icon("SkipPrevious", "M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val Replay = icon("Replay", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    val Subtitles = icon(
        "Subtitles",
        "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zM14,18H4v-2h10v2zM20,18h-4v-2h4v2zM20,14H10v-2h10v2z",
    )
}

fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** One choice in the subtitle or quality sheet. */
data class TrackOption(val id: String, val label: String, val detail: String? = null, val selected: Boolean)

/**
 * The player's on-screen controls. Stateless so it can be previewed and screenshot-tested
 * without a real ExoPlayer; [PlayerScreen] feeds it state and wires the callbacks.
 */
@Composable
fun PlayerControls(
    title: String, subtitle: String?,
    isPlaying: Boolean, buffering: Boolean,
    positionMs: Long, durationMs: Long, bufferedMs: Long,
    hasPrevious: Boolean, hasNext: Boolean,
    playFocus: FocusRequester,
    onBack: () -> Unit, onPlayPause: () -> Unit, onSeekTo: (Long) -> Unit, onSkip: (Int) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onSubtitles: () -> Unit, onQuality: () -> Unit,
    onFocusInside: (Boolean) -> Unit = {},
) {
    val wide = LocalWide.current
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .28f))
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .78f), .3f to Color.Transparent, .58f to Color.Transparent, 1f to Color.Black.copy(alpha = .9f)))
            .onFocusChanged { onFocusInside(it.hasFocus) }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = if (wide) 48.dp else 24.dp, vertical = if (wide) 32.dp else 18.dp),
    ) {
        // Top: back, what's playing, and the track menus.
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundControl(Icons.Filled.ArrowBack, "Back", 44.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = if (wide) 22.sp else 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.5.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            PillControl(PlayerIcons.Subtitles, "Subtitles", onSubtitles)
            Spacer(Modifier.width(8.dp))
            PillControl(Icons.Filled.Settings, "Quality", onQuality)
        }

        // Centre: previous, back 10s, play/pause, forward 10s, next.
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 40.dp else 28.dp),
        ) {
            if (hasPrevious) RoundControl(PlayerIcons.SkipPrevious, "Previous episode", 48.dp, onClick = onPrevious)
            SkipControl(forward = false) { onSkip(-10) }
            Box(Modifier.size(if (wide) 84.dp else 74.dp), contentAlignment = Alignment.Center) {
                RoundControl(
                    if (isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play",
                    if (wide) 84.dp else 74.dp, solid = true, modifier = Modifier.focusRequester(playFocus), onClick = onPlayPause,
                )
                if (buffering) CircularProgressIndicator(
                    Modifier.size(if (wide) 92.dp else 82.dp), color = Violet, strokeWidth = 3.dp, trackColor = Color.Transparent,
                )
            }
            SkipControl(forward = true) { onSkip(10) }
            if (hasNext) RoundControl(PlayerIcons.SkipNext, "Next episode", 48.dp, onClick = onNext)
        }

        // Bottom: times and the seek bar, plus a next-episode shortcut.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(formatClock(positionMs), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (durationMs > 0) "  /  " + formatClock(durationMs) else "", fontSize = 13.sp, color = Soft,
                )
                Spacer(Modifier.weight(1f))
                if (hasNext) PillControl(PlayerIcons.SkipNext, "Next episode", onNext)
            }
            Spacer(Modifier.height(10.dp))
            SeekBar(positionMs, durationMs, bufferedMs, onSeekTo, onSkip)
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp,
    solid: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.1f else 1f
    Box(
        modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .clip(CircleShape)
            .background(
                when {
                    solid && focused -> Violet
                    solid -> Color.White
                    focused -> Color.White
                    else -> Color.Black.copy(alpha = .35f)
                }
            )
            .border(1.dp, if (solid || focused) Color.Transparent else Color.White.copy(alpha = .25f), CircleShape)
            .tappable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = when { solid && focused -> Color.White; solid -> Ink; focused -> Ink; else -> Color.White },
            modifier = Modifier.size(size * .5f),
        )
    }
}

/** Circular arrow with "10" inside: back or forward ten seconds. */
@Composable
private fun SkipControl(forward: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.1f else 1f
    Box(
        Modifier.size(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Transparent)
            .tappable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PlayerIcons.Replay, contentDescription = if (forward) "Forward 10 seconds" else "Back 10 seconds",
            tint = if (focused) Ink else Color.White,
            modifier = Modifier.size(42.dp).graphicsLayer { if (forward) scaleX = -1f },
        )
        Text("10", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = if (focused) Ink else Color.White, modifier = Modifier.offset(y = 2.dp))
    }
}

@Composable
private fun PillControl(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.Black.copy(alpha = .35f))
            .border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = .25f), RoundedCornerShape(50))
            .tappable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Ink else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (focused) Ink else Color.White, maxLines = 1)
    }
}

/** Drag or tap to seek on touch; Left/Right step ten seconds when focused with a remote. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, bufferedMs: Long, onSeekTo: (Long) -> Unit, onSkip: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val played = when {
        durationMs <= 0 -> 0f
        dragging -> dragFraction
        else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(28.dp)
            .onFocusChanged { focused = it.hasFocus }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSkip(-10); true }
                    Key.DirectionRight -> { onSkip(10); true }
                    else -> false
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { o -> if (durationMs > 0) onSeekTo((o.x / size.width * durationMs).toLong()) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; if (durationMs > 0) onSeekTo((dragFraction * durationMs).toLong()) },
                    onDragCancel = { dragging = false },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackH = if (focused || dragging) 6.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(trackH).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
            Box(Modifier.fillMaxWidth(buffered).height(trackH).background(Color.White.copy(alpha = .3f)))
            Box(Modifier.fillMaxWidth(played).height(trackH).background(Brand))
        }
        val thumb = if (focused || dragging) 18.dp else 14.dp
        Box(
            Modifier.offset(x = (maxWidth - thumb) * played)
                .size(thumb).clip(CircleShape)
                .background(Color.White)
                .border(if (focused) 3.dp else 0.dp, Violet, CircleShape)
        )
    }
}

/** Seek feedback bubble shown on the side that was double-tapped (or after a remote skip). */
@Composable
fun SkipBubble(seconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .55f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PlayerIcons.Replay, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(22.dp).graphicsLayer { if (seconds > 0) scaleX = -1f },
        )
        Spacer(Modifier.width(8.dp))
        Text((if (seconds > 0) "+" else "−") + "${kotlin.math.abs(seconds)}s", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

/** Subtitle or quality chooser in the app's own style. */
@Composable
fun TrackSheet(title: String, options: List<TrackOption>, empty: String, onPick: (TrackOption) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) Text(empty, color = Muted, fontSize = 14.sp)
                options.forEach { option ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth()
                            .onFocusChanged { focused = it.hasFocus }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focused) Color.White.copy(alpha = .12f) else Color.Transparent)
                            .clickable { onPick(option) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.label, fontSize = 15.sp, fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Medium, color = if (option.selected) Color.White else Soft)
                            option.detail?.let { Text(it, fontSize = 12.sp, color = Muted) }
                        }
                        if (option.selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Violet, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
