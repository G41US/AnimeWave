package com.animewave.app

import android.os.Build
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screenshot tests and previews render a single still frame, so entrance animations start finished there. */
@Composable
internal fun staticFrame(): Boolean = LocalInspectionMode.current

/** Animated light sweep for loading placeholders. */
fun Modifier.shimmer(shape: RoundedCornerShape = RoundedCornerShape(14.dp)): Modifier = composed {
    val still = staticFrame()
    val sweep = if (still) .35f else rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep",
    ).value
    this.clip(shape).background(Color(0xFF181A26)).background(
        Brush.linearGradient(
            0f to Color.Transparent, .5f to Color.White.copy(alpha = .07f), 1f to Color.Transparent,
            start = Offset(sweep * 900f - 450f, 0f), end = Offset(sweep * 900f + 150f, 600f),
        )
    )
}

/** Click handler that also shrinks the element slightly while a finger is down. */
fun Modifier.tappable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = if (pressed) .96f else 1f
    this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** False while the launch title sequence still covers the app; entrances wait for it. */
val LocalEntranceReady = compositionLocalOf { true }

private val EnterEase = CubicBezierEasing(.16f, 1f, .3f, 1f)

/**
 * Fades and lifts content in the first time it appears (after the launch reveal, if one is
 * playing); [order] staggers neighbouring sections so they cascade in one after another.
 */
fun Modifier.enterUp(order: Int = 0): Modifier = composed {
    if (staticFrame()) return@composed this
    val ready = LocalEntranceReady.current
    val progress = remember { Animatable(0f) }
    val lift = with(LocalDensity.current) { 44.dp.toPx() }
    LaunchedEffect(ready) {
        if (ready) progress.animateTo(1f, tween(900, delayMillis = 110 * order.coerceAtMost(6), easing = EnterEase))
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

/** Slow push-in on hero artwork (the "Ken Burns" effect). */
fun Modifier.kenBurns(): Modifier = composed {
    if (staticFrame()) return@composed this
    val zoom by rememberInfiniteTransition(label = "kb").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom",
    )
    this.graphicsLayer { scaleX = zoom; scaleY = zoom }
}

/**
 * Low-resolution banners look soft when stretched, so they are blurred on purpose (Android 12+)
 * and become atmosphere; older devices get a darker wash instead.
 */
fun Modifier.atmosphere(radius: Dp = 22.dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31) this.blur(radius) else this

val canBlur: Boolean get() = Build.VERSION.SDK_INT >= 31

/**
 * AniList accent colours range from near-black to neon. This keeps the hue but forces enough
 * saturation and brightness to glow on the dark UI, so every title gets a usable tint.
 */
fun vivid(accent: Long?, fallback: Color = Violet): Color {
    accent ?: return fallback
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(Color(accent).toArgb(), hsv)
    if (hsv[1] < .12f) return fallback // greys have no hue worth keeping
    hsv[1] = hsv[1].coerceIn(.55f, .85f)
    hsv[2] = hsv[2].coerceIn(.78f, .95f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ---------- Skeletons ----------

@Composable
fun PosterSkeleton(width: Dp) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).shimmer())
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(.85f).height(12.dp).shimmer(RoundedCornerShape(6.dp)))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(.5f).height(10.dp).shimmer(RoundedCornerShape(5.dp)))
    }
}

@Composable
fun RowSkeleton(cardWidth: Dp, count: Int = 8) {
    Row(
        Modifier.padding(horizontal = gutter, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (LocalWide.current) 18.dp else 14.dp),
    ) { repeat(count) { PosterSkeleton(cardWidth) } }
}

@Composable
fun HeroSkeleton(height: Dp) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.5f to Color.Transparent, 1f to Ink)))
        Column(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 40.dp)) {
            Box(Modifier.width(110.dp).height(22.dp).shimmer(RoundedCornerShape(50)))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(280.dp).height(30.dp).shimmer(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(200.dp).height(30.dp).shimmer(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(20.dp))
            Box(Modifier.width(220.dp).height(50.dp).shimmer(RoundedCornerShape(14.dp)))
        }
    }
}

@Composable
fun EpisodeListSkeleton(rows: Int = 4) {
    Column(Modifier.padding(horizontal = gutter)) {
        repeat(rows) {
            Row(Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.width(if (LocalWide.current) 176.dp else 136.dp).aspectRatio(16f / 9f).shimmer(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(top = 6.dp)) {
                    Box(Modifier.width(150.dp).height(13.dp).shimmer(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(90.dp).height(10.dp).shimmer(RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
