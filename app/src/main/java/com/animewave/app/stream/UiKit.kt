package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape

val Ink = Color(0xFF07080D)
val Panel = Color(0xFF151722)
val Violet = Color(0xFF9B6DFF)
val Pink = Color(0xFFFF5FA2)
val Muted = Color(0xFF9EA1B3)
val Soft = Color(0xFFDADBE5)
val Gold = Color(0xFFFFC94D)
val Glass = Color.White.copy(alpha = .09f)
val Hairline = Color.White.copy(alpha = .12f)
val Brand = Brush.linearGradient(listOf(Violet, Pink))
/** Page margin: roomier on TV so everything lines up with the hero. */
val Gutter: androidx.compose.ui.unit.Dp @androidx.compose.runtime.Composable get() = if (LocalWide.current) 48.dp else 18.dp

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

fun jakarta(brush: Brush? = null) = TextStyle(fontFamily = Jakarta, brush = brush)

/** Animated light sweep for loading placeholders. */
fun Modifier.shimmer(shape: RoundedCornerShape = RoundedCornerShape(14.dp)): Modifier = composed {
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep",
    )
    this.clip(shape).background(Color(0xFF181A26)).background(
        Brush.linearGradient(
            0f to Color.Transparent, .5f to Color.White.copy(alpha = .07f), 1f to Color.Transparent,
            start = Offset(sweep * 900f - 450f, 0f), end = Offset(sweep * 900f + 150f, 600f),
        )
    )
}

/**
 * Click that shrinks slightly while pressed. On TV, focus lifts it; pass the artwork's [shape]
 * to also get a soft violet glow behind it (no outlines).
 */
fun Modifier.tappable(onClick: () -> Unit): Modifier = tappableCard(null, onClick)

/** For full-width rows: a soft fill on focus, no growing (so nothing spills past the edges). */
fun Modifier.tappableRow(onClick: () -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.hasFocus }.clip(RoundedCornerShape(14.dp))
        .background(if (focused) Color.White.copy(alpha = .1f) else Color.Transparent)
        .clickable(interactionSource = null, indication = null, onClick = onClick)
}

fun Modifier.tappableCard(shape: Shape?, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale = if (pressed) .96f else 1f
    this.onFocusChanged { focused = it.hasFocus }
        .lift(focused, shape)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Premium TV focus: a smooth lift, plus a soft coloured glow when a [shape] is given. */
fun Modifier.lift(on: Boolean, shape: Shape? = null, amount: Float = 1.07f): Modifier = composed {
    val s by androidx.compose.animation.core.animateFloatAsState(if (on) amount else 1f, tween(220, easing = EnterEase), label = "lift")
    val g by androidx.compose.animation.core.animateFloatAsState(if (on && shape != null) 1f else 0f, tween(220), label = "glow")
    val glow = with(LocalDensity.current) { 12.dp.toPx() }
    this.graphicsLayer {
        scaleX = s; scaleY = s
        if (shape != null) {
            this.shape = shape; clip = false
            shadowElevation = g * glow
            ambientShadowColor = Violet; spotShadowColor = Violet.copy(alpha = .5f)
        }
    }
}

/** Card/button edge: a hairline on phones, nothing on TV. */
val Edge: Color @androidx.compose.runtime.Composable get() = if (LocalWide.current) Color.Transparent else Hairline

private val EnterEase = CubicBezierEasing(.16f, 1f, .3f, 1f)

/** Fades and lifts content in the first time it appears; [order] staggers neighbours. */
fun Modifier.enterUp(order: Int = 0): Modifier = composed {
    if (LocalInspectionMode.current || LocalPreload.current.isNotEmpty()) return@composed this
    val progress = remember { Animatable(0f) }
    val lift = with(LocalDensity.current) { 36.dp.toPx() }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(800, delayMillis = 90 * order.coerceAtMost(6), easing = EnterEase)) }
    this.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * lift }
}

/** Slow push-in on hero artwork. */
fun Modifier.kenBurns(): Modifier = composed {
    // TV boxes have weak GPUs: a never-ending full-screen zoom redraws every frame and makes the whole app lag.
    if (LocalInspectionMode.current || LocalPreload.current.isNotEmpty() || LocalWide.current) return@composed this
    val zoom by rememberInfiniteTransition(label = "kb").animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom",
    )
    this.graphicsLayer { scaleX = zoom; scaleY = zoom }
}

fun rating(r: Double) = if (r <= 0) null else String.format("%.1f", r)

/** True on TVs and other wide screens: tabs move to the top and the dock goes away. */
val LocalWide = compositionLocalOf { false }
/** Running on a TV (remote control, no touch). */
val LocalTv = compositionLocalOf { false }
/** Screenshots: data handed in up front, and entrance animations already finished. */
val LocalPreload = compositionLocalOf<Map<Any?, Any?>> { emptyMap() }

/** Visible screen size in the current (possibly TV-scaled) dp. */
@androidx.compose.runtime.Composable
fun screenDp(): androidx.compose.ui.unit.DpSize {
    val size = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { androidx.compose.ui.unit.DpSize(size.width.toDp(), size.height.toDp()) }
}
/** On TV, the top bar each page draws inside itself. */
val LocalTopBar = compositionLocalOf<(@androidx.compose.runtime.Composable () -> Unit)?> { null }

/** On TV, puts the remote on this element when the page opens so there's always a starting point. */
fun Modifier.tvFirstFocus(key: Any?): Modifier = composed {
    if (!LocalTv.current) return@composed this
    val r = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(key) { kotlinx.coroutines.delay(120); runCatching { r.requestFocus() } }
    this.focusRequester(r)
}

/** What the TV remote is on: a soft light fill and a slight lift. No outlines. */
fun Modifier.focusGlow(shape: Shape = RoundedCornerShape(14.dp), grow: Boolean = true): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (grow) Modifier.lift(focused, amount = 1.06f) else Modifier)
        .background(if (focused) Color.White.copy(alpha = .12f) else Color.Transparent, shape)
}

/** TV: scroll only as far as needed to show the focused item, instead of pinning it near the top. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
object MinimalScroll : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset >= 0f && end <= containerSize -> 0f
            size > containerSize -> offset
            offset < 0f -> offset
            else -> end - containerSize
        }
    }
}

/** The list on screen right now, so the TV remote can always scroll it. */
object TvScroll {
    var target: androidx.compose.foundation.gestures.ScrollableState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var focus: androidx.compose.ui.focus.FocusManager? = null
    var step = 600f

    /** Scrolls the current page when the remote's Up/Down found nothing to move to. */
    fun nudge(down: Boolean): Boolean {
        val t = target ?: return false
        val sc = scope ?: return false
        if (down && !t.canScrollForward || !down && !t.canScrollBackward) return false
        sc.launch {
            t.animateScrollBy(if (down) step else -step)
            // Land on whatever just scrolled into view.
            focus?.moveFocus(if (down) androidx.compose.ui.focus.FocusDirection.Down else androidx.compose.ui.focus.FocusDirection.Up)
        }
        return true
    }
}

/**
 * TV remote: Up/Down moves focus to the next item; when nothing focusable is in that direction
 * the page scrolls instead, so the remote never gets stuck.
 */
fun Modifier.dpadScroll(state: androidx.compose.foundation.gestures.ScrollableState): Modifier = composed {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val step = with(LocalDensity.current) { 260.dp.toPx() }
    androidx.compose.runtime.DisposableEffect(state) {
        TvScroll.target = state; TvScroll.scope = scope; TvScroll.focus = focus; TvScroll.step = step
        onDispose { if (TvScroll.target === state) { TvScroll.target = null; TvScroll.scope = null } }
    }
    this.onPreviewKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val down = when (e.key) {
            androidx.compose.ui.input.key.Key.DirectionDown -> true
            androidx.compose.ui.input.key.Key.DirectionUp -> false
            else -> return@onPreviewKeyEvent false
        }
        val dir = if (down) androidx.compose.ui.focus.FocusDirection.Down else androidx.compose.ui.focus.FocusDirection.Up
        if (focus.moveFocus(dir)) true else TvScroll.nudge(down)
    }
}

/** Screenshots: images supplied directly instead of loaded from the network. */
val LocalImageOverride = compositionLocalOf<((String) -> androidx.compose.ui.graphics.painter.Painter?)?> { null }

/** Network image (Coil), with a hook for screenshots. */
@androidx.compose.runtime.Composable
fun NetImage(
    model: String?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
) {
    val painter = model?.let { LocalImageOverride.current?.invoke(it) }
    if (painter != null) androidx.compose.foundation.Image(painter, contentDescription, modifier, alignment, contentScale)
    else coil.compose.AsyncImage(model, contentDescription, modifier, alignment = alignment, contentScale = contentScale)
}
