package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Everything the controls need from the player screen. */
interface PlayerHost {
    val label: String
    val source: String
    val isTv: Boolean
    val season: Int
    val episode: Int
    val episodes: List<Episode>
    val hasNext: Boolean
    val aspectName: String
    val downloadLabel: String?
    val subSize: Int
    val subStyle: Int
    val subColor: Int
    val subOffset: Long
    /** What automatic subtitle sync is doing, in plain words. */
    val subSync: String
    val autoplay: Boolean
    val autoSkip: Boolean
    val nightMode: Boolean
    fun toggleNightMode()
    /** Exact intro/recap/credits/preview times for what's playing (may be empty). */
    val segments: List<Segment>
    /** Small muted video view used for seek previews. */
    val previewView: android.view.View?
    /** Move the seek preview to [posMs], or stop it with null. */
    fun preview(posMs: Long?)
    fun back()
    fun next()
    fun web()
    fun sources()
    fun download()
    fun playEpisode(e: Int)
    fun cycleAspect()
    fun brightness(delta: Float): Float
    fun volume(delta: Float): Float
    fun setSubSize(i: Int)
    fun setSubStyle(i: Int)
    fun setSubColor(i: Int)
    fun setSubOffset(ms: Long)
}

/** Subtitle colours offered in the style panel. */
val SubColors = listOf(Color.White, Color(0xFFFFE14D), Color(0xFF6FE7FF), Color(0xFF7DFF9B), Color(0xFFFF8CC6))
val SubColorNames = listOf("White", "Yellow", "Cyan", "Green", "Pink")
val SubStyleNames = listOf("None", "Shadow", "Box", "Solid")

private data class Choice(val label: String, val selected: Boolean, val apply: () -> Unit)

private enum class Sheet { SETTINGS, QUALITY, SPEED, SUBTITLES, STYLE, EPISODES, AUDIO }

fun clock(ms: Long): String {
    if (ms < 0) return "0:00"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Readable on any picture: white with a soft dark shadow, no background. */
private val shade = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 2f), 8f)

@Composable
private fun Plain(icon: Int, label: String?, size: Dp = 24.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).playerGlow(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), label, tint = Color.White, modifier = Modifier.size(size))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFamily = Jakarta, shadow = shade))
        }
    }
}

/** Movio's own player controls, drawn over the video. */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(player: Player, host: PlayerHost, visible: Boolean, onVisible: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var gauge by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var locked by remember { mutableStateOf(false) }
    var boosting by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var countdown by remember { mutableIntStateOf(-1) }
    var countdownDismissed by remember(host.episode) { mutableStateOf(false) }
    var autoSkipped by remember(host.episode) { mutableStateOf(setOf<Segment>()) }
    var active by remember { mutableStateOf<Segment?>(null) }
    // TV remote: where focus goes, so there's always something selected.
    val tv = LocalTv.current
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    var focusInside by remember { mutableStateOf(false) }
    var skipFocused by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (!dragging) position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            if (visible && sheet == null && player.isPlaying && System.currentTimeMillis() - lastTouch > 3500) onVisible(false)
            val left = duration - position
            val segs = host.segments
            active = segs.firstOrNull { it.type != "credits" && it.type != "preview" && it.contains(position) }
            // Auto-skip intros and recaps once each, when the viewer turned that on.
            active?.let { a ->
                if (host.autoSkip && a.type != "learned" && a !in autoSkipped && a.endMs != null) {
                    autoSkipped = autoSkipped + a; player.seekTo(a.endMs); hint = if (a.type == "recap") "Skipped recap" else "Skipped intro"
                }
            }
            // Up next starts when the credits really start (10 s), else in the last 20 s.
            val credits = segs.firstOrNull { it.type == "credits" || it.type == "preview" }?.startMs?.takeIf { it > duration / 2 }
            val inCredits = credits != null && position >= credits
            countdown = if (host.hasNext && host.autoplay && !countdownDismissed && duration > 0 &&
                (inCredits || left in 1..20_000 || player.playbackState == Player.STATE_ENDED)) {
                if (inCredits) (10 - (position - credits!!) / 1000).toInt().coerceIn(0, 10).let { if (left < it * 1000L) (left / 1000).toInt() else it }
                else (left / 1000).toInt().coerceAtLeast(0)
            } else -1
            if (countdown == 0) { countdownDismissed = true; host.next() }
            delay(250)
        }
    }
    LaunchedEffect(hint) { if (hint != null) { delay(700); hint = null } }
    LaunchedEffect(gauge) { if (gauge != null) { delay(900); gauge = null } }

    fun touch() { lastTouch = System.currentTimeMillis() }
    fun focusPlay() { if (tv) scope.launch { delay(60); runCatching { playFocus.requestFocus() } } }
    LaunchedEffect(Unit) { if (tv) runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(visible) { if (tv && !visible) runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(sheet) {
        if (!tv) return@LaunchedEffect
        if (sheet != null) { delay(220); runCatching { sheetFocus.requestFocus() } } else if (visible) runCatching { playFocus.requestFocus() }
    }
    // Back closes a panel first, then the controls, then leaves the player.
    BackHandler(enabled = sheet != null) { sheet = null }
    BackHandler(enabled = tv && visible && sheet == null && !locked) { onVisible(false) }
    fun seekBy(ms: Long) {
        player.seekTo((player.currentPosition + ms).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE))
        hint = (if (ms > 0) "+" else "−") + if (kotlin.math.abs(ms) >= 60_000) "${kotlin.math.abs(ms) / 60_000} min" else "${kotlin.math.abs(ms) / 1000}s"
        touch()
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (!tv || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch()
                if (sheet != null) return@onPreviewKeyEvent false
                val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                val arrow = e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                val skipTo = active?.takeIf { !host.autoSkip }?.endMs
                when {
                    // Controls hidden: OK takes Up next / Skip intro if showing, Left/Right skip 10 s, anything else shows the controls.
                    !visible && center && countdown >= 0 -> { countdownDismissed = true; host.next(); true }
                    center && skipTo != null && (!visible || !focusInside) -> { player.seekTo(skipTo); hint = if (active?.type == "recap") "Skipped recap" else "Skipped intro"; true }
                    !visible && e.key == Key.DirectionLeft -> { seekBy(-10_000); true }
                    !visible && e.key == Key.DirectionRight -> { seekBy(10_000); true }
                    !visible && (center || arrow) -> { onVisible(true); focusPlay(); true }
                    // Controls showing but nothing selected yet: start on Play/Pause.
                    visible && !focusInside && (center || arrow) -> { focusPlay(); true }
                    else -> false
                }
            }
            .pointerInput(locked, visible, sheet) {
                if (locked) { detectTapGestures(onTap = { onVisible(!visible) }); return@pointerInput }
                var job: Job? = null
                detectTapGestures(
                    onTap = { if (sheet != null) sheet = null else { onVisible(!visible); touch() } },
                    onDoubleTap = { o -> seekBy(if (o.x < size.width / 2) -10_000 else 10_000) },
                    onPress = {
                        job = scope.launch { delay(550); boosting = true; player.setPlaybackSpeed(2f) }
                        tryAwaitRelease()
                        job?.cancel()
                        if (boosting) { boosting = false; player.setPlaybackSpeed(1f) }
                    },
                )
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                var mode = 0
                var side = 0
                var startPos = 0L
                var scrubTo = 0L
                detectDragGestures(
                    onDragStart = { o -> mode = 0; startPos = player.currentPosition; scrubTo = startPos; side = if (o.x < size.width / 2) 1 else 2 },
                    onDragEnd = { if (mode == 3) { player.seekTo(scrubTo); dragging = false; host.preview(null) }; mode = 0 },
                    onDragCancel = { mode = 0; dragging = false; host.preview(null) },
                ) { change, d ->
                    if (mode == 0) mode = if (abs(d.x) > abs(d.y)) 3 else side
                    when (mode) {
                        1 -> gauge = 1 to host.brightness(-d.y / size.height * 1.6f)
                        2 -> gauge = 2 to host.volume(-d.y / size.height * 1.6f)
                        3 -> if (duration > 0) {
                            scrubTo = (scrubTo + (d.x / size.width * 180_000).toLong()).coerceIn(0, duration)
                            dragging = true; dragValue = scrubTo.toFloat() / duration
                            host.preview(scrubTo)
                            if (host.previewView == null) hint = "${clock(scrubTo)}  (${if (scrubTo >= startPos) "+" else "−"}${clock(abs(scrubTo - startPos))})"
                        }
                    }
                    change.consume()
                }
            },
    ) {
        if (buffering) CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = .15f), strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(52.dp))
        hint?.let { Bubble(it, Modifier.align(Alignment.Center)) }
        if (boosting) Bubble("2× speed", Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 18.dp))
        gauge?.let { (kind, v) -> Bubble("${if (kind == 1) "Brightness" else "Volume"}  ${(v * 100).toInt()}%", Modifier.align(Alignment.Center)) }

        if (locked) {
            AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Box(Modifier.safeDrawingPadding().padding(28.dp)) { Plain(R.drawable.ic_lock, "Tap to unlock") { locked = false; touch() } }
            }
        } else AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().onFocusChanged { focusInside = it.hasFocus }.background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = .8f), .22f to Color.Black.copy(alpha = .25f), .5f to Color.Black.copy(alpha = .15f),
                        .78f to Color.Black.copy(alpha = .3f), 1f to Color.Black.copy(alpha = .88f))
                ),
            ) {
                // Top: back, title, settings
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Plain(R.drawable.ic_back, null, 26.dp) { host.back() }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = Jakarta, shadow = shade))
                        if (duration > 0) Text("Ends at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + duration - position)),
                            color = Soft, fontSize = 12.sp, style = TextStyle(fontFamily = Jakarta, shadow = shade))
                    }
                    Plain(R.drawable.ic_settings, null, 26.dp) { sheet = Sheet.SETTINGS; touch() }
                }
                // Centre
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                    SeekIcon(R.drawable.ic_back10) { seekBy(-10_000) }
                    Box(
                        Modifier.size(76.dp).focusRequester(playFocus).clip(CircleShape).playerGlow(CircleShape).clickable { if (player.isPlaying) player.pause() else player.play(); touch() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_fill), null, tint = Color.White, modifier = Modifier.size(54.dp)) }
                    SeekIcon(R.drawable.ic_fwd10) { seekBy(10_000) }
                }
                // Bottom: seek bar, then one quiet row
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 4.dp)) {
                    val pv = host.previewView
                    if (dragging && pv != null && duration > 0) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(118.dp)) {
                        val w = 176.dp
                        val x = ((maxWidth - w) * dragValue.coerceIn(0f, 1f))
                        Column(Modifier.padding(start = x).width(w), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(w).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black).border(2.dp, Color.White, RoundedCornerShape(10.dp))) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { (pv.parent as? android.view.ViewGroup)?.removeView(pv); pv },
                                    modifier = Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(8.dp)),
                                    onRelease = { (it.parent as? android.view.ViewGroup)?.removeView(it) },
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(clock((dragValue * duration).toLong()), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFamily = Jakarta, shadow = shade))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(if (dragging) (dragValue * duration).toLong() else position), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFamily = Jakarta, shadow = shade))
                        SeekLine(
                            value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                            onDrag = { if (duration > 0) { dragging = true; dragValue = it; touch(); host.preview((it * duration).toLong()) } },
                            onDone = { if (dragging) player.seekTo((dragValue * duration).toLong()); dragging = false; host.preview(null) },
                            // On the bar the remote jumps a minute, like VLC; the ±10 buttons stay 10 s.
                            onStep = { dir -> seekBy(dir * 60_000L) },
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        )
                        Text("−" + clock(duration - position), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFamily = Jakarta, shadow = shade))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        if (!tv) Plain(R.drawable.ic_lock, "Lock") { locked = true; sheet = null; touch() }
                        if (host.isTv && host.episodes.isNotEmpty()) Plain(R.drawable.ic_list, "Episodes") { sheet = Sheet.EPISODES; touch() }
                        Plain(R.drawable.ic_cc, "Subtitles") { sheet = Sheet.SUBTITLES; touch() }
                        if (host.isTv && host.hasNext) Plain(R.drawable.ic_next, "Next episode") { host.next() }
                    }
                }
            }
        }

        // Skip intro / recap: only during the real segment, jumping to its exact end.
        val seg = active
        if (!locked && seg != null && seg.endMs != null && !host.autoSkip && countdown < 0 && sheet == null) {
            Box(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 24.dp, bottom = if (visible) 110.dp else 28.dp)) {
                Text(seg.label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = jakarta(),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).playerGlow(RoundedCornerShape(10.dp))
                        .clickable { player.seekTo(seg.endMs); hint = if (seg.type == "recap") "Skipped recap" else "Skipped intro"; touch() }.padding(horizontal = 18.dp, vertical = 11.dp))
            }
        }

        // Up next
        AnimatedVisibility(countdown >= 0, modifier = Modifier.align(Alignment.BottomEnd), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Column(Modifier.safeDrawingPadding().padding(22.dp).width(290.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF00E1017)).padding(16.dp)) {
                Text("Up next", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = jakarta())
                Spacer(Modifier.height(4.dp))
                val nextEp = host.episodes.firstOrNull { it.number == host.episode + 1 }
                Text(nextEp?.let { "E${it.number} · ${it.name}" } ?: "Next episode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    style = jakarta(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Play in ${countdown.coerceAtLeast(0)}", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = jakarta(),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).playerGlow(RoundedCornerShape(10.dp)).clickable { countdownDismissed = true; host.next() }.padding(horizontal = 16.dp, vertical = 10.dp))
                    Text("Cancel", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = jakarta(),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).playerGlow(RoundedCornerShape(10.dp)).clickable { countdownDismissed = true }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }

        // Side sheet
        AnimatedVisibility(sheet != null, modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            val p = sheet ?: Sheet.SETTINGS
            Column(Modifier.fillMaxHeight().width(if (p == Sheet.EPISODES || p == Sheet.STYLE) 360.dp else 310.dp).background(Color(0xF50D0F15)).safeDrawingPadding().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p in setOf(Sheet.QUALITY, Sheet.SPEED, Sheet.STYLE, Sheet.AUDIO)) {
                        Plain(R.drawable.ic_back, null, 20.dp) { sheet = if (p == Sheet.STYLE) Sheet.SUBTITLES else Sheet.SETTINGS }
                    }
                    Text(when (p) {
                        Sheet.SETTINGS -> "Settings"; Sheet.QUALITY -> "Quality"; Sheet.SPEED -> "Speed"
                        Sheet.SUBTITLES -> "Subtitles"; Sheet.STYLE -> "Subtitle style"; Sheet.EPISODES -> "Season ${host.season}"; Sheet.AUDIO -> "Audio"
                    }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, style = jakarta(), modifier = Modifier.weight(1f))
                    Plain(R.drawable.ic_close, null, 20.dp, Modifier.focusRequester(sheetFocus)) { sheet = null }
                }
                Spacer(Modifier.height(10.dp))
                when (p) {
                    Sheet.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        val q = currentQuality(player)
                        SettingRow("Quality", q, R.drawable.ic_settings) { sheet = Sheet.QUALITY }
                        SettingRow("Audio", currentAudio(player), R.drawable.ic_audio) { sheet = Sheet.AUDIO }
                        SettingRow("Night mode", if (host.nightMode) "On · quieter bangs, clearer voices" else "Off", R.drawable.ic_moon) { host.toggleNightMode() }
                        SettingRow("Speed", if (player.playbackParameters.speed == 1f) "Normal" else "${player.playbackParameters.speed}x", R.drawable.ic_speed) { sheet = Sheet.SPEED }
                        SettingRow("Picture", host.aspectName, R.drawable.ic_fit) { host.cycleAspect() }
                        SettingRow("Source", host.source, R.drawable.ic_servers) { sheet = null; host.sources() }
                        SettingRow("Download", host.downloadLabel ?: "Save for offline", R.drawable.ic_download) { host.download() }
                        SettingRow("Web player", "If this stream has trouble", R.drawable.ic_web) { sheet = null; host.web() }
                    }
                    Sheet.EPISODES -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(host.episodes, key = { it.number }) { e ->
                            val current = e.number == host.episode
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (current) Color.White.copy(alpha = .1f) else Color.Transparent)
                                    .playerGlow(RoundedCornerShape(12.dp), grow = false).clickable { sheet = null; host.playEpisode(e.number) }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Panel)) {
                                    NetImage(Tmdb.img(e.still, "w300"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${e.number}. ${e.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = jakarta(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(if (current) "Watching" else e.runtime?.let { "${it}m" } ?: "", color = if (current) Violet else Muted, fontSize = 11.sp, style = jakarta())
                                    if (e.overview.isNotBlank()) Text(e.overview, color = Muted, fontSize = 11.5.sp, lineHeight = 15.sp, style = jakarta(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Sheet.SUBTITLES -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Options(choices(player, Sheet.SUBTITLES)) { sheet = null; touch() }
                        Spacer(Modifier.height(8.dp))
                        SettingRow("Subtitle style", "${SubStyleNames[host.subStyle]} · ${SubColorNames[host.subColor]} · ${listOf("S", "M", "L", "XL")[host.subSize]}", R.drawable.ic_cc) { sheet = Sheet.STYLE }
                        SettingRow("Auto-sync", host.subSync, R.drawable.ic_refresh) { }
                        SettingRow("Timing", if (host.subOffset == 0L) "No change" else "%+.1fs".format(host.subOffset / 1000f), R.drawable.ic_speed) { sheet = Sheet.STYLE }
                    }
                    Sheet.STYLE -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        SubtitlePreview(host)
                        Spacer(Modifier.height(16.dp))
                        Caption("Size")
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("S", "M", "L", "XL").forEachIndexed { i, l -> Pick(l, host.subSize == i) { host.setSubSize(i) } }
                        }
                        Spacer(Modifier.height(14.dp))
                        Caption("Background")
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubStyleNames.forEachIndexed { i, l -> Pick(l, host.subStyle == i) { host.setSubStyle(i) } }
                        }
                        Spacer(Modifier.height(14.dp))
                        Caption("Text colour")
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SubColors.forEachIndexed { i, c ->
                                Box(Modifier.size(34.dp).clip(CircleShape).background(c)
                                    .border(if (host.subColor == i) 3.dp else 1.dp, if (host.subColor == i) Violet else Hairline, CircleShape)
                                    .playerGlow(CircleShape).clickable { host.setSubColor(i) })
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Caption("Timing")
                        Text("Too early? Press +. Too late? Press −.", color = Muted, fontSize = 12.sp, style = jakarta())
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pick("−0.5s", false) { host.setSubOffset(host.subOffset - 500) }
                            Text("%+.1fs".format(host.subOffset / 1000f), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = jakarta(),
                                modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Pick("+0.5s", false) { host.setSubOffset(host.subOffset + 500) }
                            if (host.subOffset != 0L) Pick("Reset", false) { host.setSubOffset(0) }
                        }
                    }
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) { Options(choices(player, p)) { sheet = Sheet.SETTINGS; touch() } }
                }
            }
        }
    }
}

/** Thin seek line: only the line itself reacts; a small thumb, thicker when the remote is on it. */
@Composable
private fun SeekLine(value: Float, onDrag: (Float) -> Unit, onDone: () -> Unit, onStep: (Int) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    val line by androidx.compose.animation.core.animateDpAsState(if (focused) 6.dp else 4.dp, label = "line")
    val knob by androidx.compose.animation.core.animateDpAsState(if (focused) 18.dp else 12.dp, label = "knob")
    val v = value.coerceIn(0f, 1f)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.height(26.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                val dpad = e.key == Key.DirectionLeft || e.key == Key.DirectionRight || e.key == Key.DirectionUp || e.key == Key.DirectionDown
                if (e.type != KeyEventType.KeyDown) dpad
                else when (e.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(1); true }
                    Key.DirectionUp -> { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Up); true }
                    Key.DirectionDown -> { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { o -> onDrag((o.x / size.width).coerceIn(0f, 1f)); onDone() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onDrag((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onDone, onDragCancel = onDone,
                ) { c, _ -> onDrag((c.position.x / size.width).coerceIn(0f, 1f)); c.consume() }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(line).clip(CircleShape).background(Color.White.copy(alpha = .28f)))
        Box(Modifier.fillMaxWidth(v).height(line).clip(CircleShape).background(Brand))
        Box(Modifier.offset(x = w * v - knob / 2).size(knob).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun SubtitlePreview(host: PlayerHost) {
    val color = SubColors[host.subColor]
    val size = listOf(14, 17, 20, 24)[host.subSize].sp
    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B5B7A), Color(0xFF8A6A4A)))),
        contentAlignment = Alignment.BottomCenter) {
        val textStyle = when (host.subStyle) {
            0 -> TextStyle(fontFamily = Jakarta, shadow = Shadow(Color.Black, Offset.Zero, 6f))
            1 -> TextStyle(fontFamily = Jakarta, shadow = Shadow(Color.Black.copy(alpha = .9f), Offset(2f, 3f), 6f))
            else -> TextStyle(fontFamily = Jakarta)
        }
        val bg = when (host.subStyle) { 2 -> Color.Black.copy(alpha = .55f); 3 -> Color.Black; else -> Color.Transparent }
        Text("This is how subtitles will look", color = color, fontSize = size, fontWeight = FontWeight.SemiBold, style = textStyle,
            modifier = Modifier.padding(bottom = 14.dp).background(bg).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = jakarta(), modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Pick(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Ink else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = jakarta(),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Color.White else Color.White.copy(alpha = .1f))
            .playerGlow(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun SettingRow(title: String, value: String, icon: Int, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).playerGlow(RoundedCornerShape(12.dp), grow = false).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = Soft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = jakarta(), maxLines = 1, softWrap = false)
        Text(value, color = Muted, fontSize = 13.sp, style = jakarta(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun Bubble(text: String, modifier: Modifier) {
    Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = jakarta(),
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 18.dp, vertical = 10.dp))
}

@Composable
private fun SeekIcon(icon: Int, onClick: () -> Unit) {
    Box(Modifier.size(58.dp).clip(CircleShape).playerGlow(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(38.dp))
        Text("10", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, style = jakarta(), modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun Options(choices: List<Choice>, onPicked: () -> Unit) {
    if (choices.isEmpty()) Text("Not available for this video", color = Muted, fontSize = 14.sp, style = jakarta())
    choices.forEach { c ->
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                .background(if (c.selected) Color.White.copy(alpha = .1f) else Color.Transparent)
                .playerGlow(RoundedCornerShape(12.dp), grow = false).clickable { c.apply(); onPicked() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(c.label, color = Color.White, fontSize = 15.sp, fontWeight = if (c.selected) FontWeight.Bold else FontWeight.Medium, style = jakarta(), modifier = Modifier.weight(1f))
            if (c.selected) Icon(painterResource(R.drawable.ic_check), null, tint = Violet, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(UnstableApi::class)
private fun currentQuality(player: Player): String {
    val group = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return "Auto"
    val override = player.trackSelectionParameters.overrides[group.mediaTrackGroup] ?: return "Auto"
    return override.trackIndices.firstOrNull()?.let { "${group.getTrackFormat(it).height}p" } ?: "Auto"
}

@OptIn(UnstableApi::class)
private fun choices(player: Player, sheet: Sheet): List<Choice> {
    val params = player.trackSelectionParameters
    return when (sheet) {
        Sheet.SPEED -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { s ->
            Choice(if (s == 1f) "Normal" else "${s}x", player.playbackParameters.speed == s) { player.setPlaybackSpeed(s) }
        }
        Sheet.QUALITY -> {
            val group = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return emptyList()
            val override = params.overrides[group.mediaTrackGroup]
            val auto = Choice("Auto", override == null) {
                player.trackSelectionParameters = params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            }
            val tracks = (0 until group.length).filter { group.isTrackSupported(it) }
                .distinctBy { group.getTrackFormat(it).height }
                .sortedByDescending { group.getTrackFormat(it).height }
                .map { i ->
                    val h = group.getTrackFormat(i).height
                    Choice(if (h > 0) "${h}p" else "Track ${i + 1}", override?.trackIndices?.contains(i) == true) {
                        player.trackSelectionParameters = params.buildUpon().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                    }
                }
            if (tracks.size <= 1) tracks.ifEmpty { listOf(auto) } else listOf(auto) + tracks
        }
        Sheet.SUBTITLES -> {
            val off = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            if (groups.isEmpty()) return emptyList()
            listOf(Choice("Off", off) {
                player.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }) + groups.flatMapIndexed { gi, g ->
                (0 until g.length).map { i ->
                    val f = g.getTrackFormat(i)
                    val name = f.label ?: f.language?.let { Locale(it).displayLanguage } ?: "Subtitles ${gi + 1}"
                    Choice(name, !off && g.isTrackSelected(i)) {
                        player.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                    }
                }
            }.distinctBy { it.label }
        }
        Sheet.AUDIO -> {
            // Every language/dub the stream carries, e.g. English, French, original.
            val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            groups.flatMapIndexed { gi, g ->
                (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
                    Choice(audioName(g.getTrackFormat(i), gi * 10 + i), g.isTrackSelected(i)) {
                        player.trackSelectionParameters = params.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                    }
                }
            }.distinctBy { it.label }
        }
        else -> emptyList()
    }
}

private fun audioName(f: androidx.media3.common.Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
    val ch = when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
    return listOfNotNull(f.label ?: lang ?: "Track ${n + 1}", ch?.takeIf { f.label == null }).joinToString(" · ")
}

private fun currentAudio(player: Player): String {
    val g = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val total = g.sumOf { it.length }
    val sel = g.firstNotNullOfOrNull { gr -> (0 until gr.length).firstOrNull { gr.isTrackSelected(it) }?.let { gr.getTrackFormat(it) } }
    val name = sel?.let { audioName(it, 0) } ?: "Default"
    return if (total > 1) name else "$name (only one)"
}

/** TV focus without hard outlines: a soft light behind the control and a slight lift. */
fun Modifier.playerGlow(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp), grow: Boolean = true): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    // Full-width rows never grow, so they can't spill out of their panel.
    val lift by androidx.compose.animation.core.animateFloatAsState(if (focused && grow) 1.1f else 1f, label = "lift")
    this.onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer { scaleX = lift; scaleY = lift }
        .background(if (focused) Color.White.copy(alpha = .22f) else Color.Transparent, shape)
}
