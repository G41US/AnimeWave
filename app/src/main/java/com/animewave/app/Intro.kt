package com.animewave.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun phase(t: Float, start: Float, length: Float) = ((t - start) / length).coerceIn(0f, 1f)

// Where the painted logo's loading bar sat in loading_art (879 x 887 px); ours goes in the same spot.
private const val ART_W = 879f
private const val ART_H = 887f
private const val BAR_X = 430f
private const val BAR_Y = 748f

/**
 * Loading screen played on launch while the catalog loads: ~2.2s, then waits up to
 * 1.8s more for the first data. [ready] reports when home data arrived.
 */
@Composable
fun IntroScreen(ready: () -> Boolean, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2200, easing = LinearEasing))
        withTimeoutOrNull(1800) { snapshotFlow { isReady() }.first { it } }
        onDone()
    }
    IntroContent(t.value)
}

/** One frame of the loading screen; [t] runs 0 → 1. */
@Composable
fun IntroContent(t: Float) {
    val fadeIn = phase(t, 0f, .18f)
    val ui = phase(t, .15f, .2f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF030208))) {
        val portrait = maxHeight > maxWidth
        // TV: whole artwork, blurred copy filling the sides. Phones: zoom in, but never so far
        // that the painted ANIMEWAVE wordmark (~480 px wide) gets cut off.
        val scale = if (portrait) min(max(maxWidth / ART_W.dp, maxHeight / ART_H.dp), maxWidth / 480.dp)
        else min(maxWidth / ART_W.dp, maxHeight / ART_H.dp)
        val artW = ART_W.dp * scale
        val artH = ART_H.dp * scale
        val left = (maxWidth - artW) / 2
        val top = (maxHeight - artH) / 2
        val zoom = 1f + .03f * t

        Image(
            painterResource(R.drawable.loading_blur), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fadeIn },
        )
        Box(
            Modifier.align(Alignment.Center).requiredSize(artW, artH)
                .graphicsLayer { alpha = fadeIn; scaleX = zoom; scaleY = zoom; compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Fade the artwork's edges out so it melts into the blurred fill (sides on TV, top/bottom on phones).
                    val clear = Color.Transparent
                    drawRect(
                        if (portrait) Brush.verticalGradient(0f to clear, .1f to Color.Black, .9f to Color.Black, 1f to clear)
                        else Brush.horizontalGradient(0f to clear, .1f to Color.Black, .9f to Color.Black, 1f to clear),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            Image(painterResource(R.drawable.loading_art), contentDescription = "AnimeWave", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        Petals(t, Modifier.fillMaxSize())

        // Real loading bar + label where the painting had them.
        val barW = (artW * (240f / ART_W)).coerceIn(160.dp, 300.dp)
        val barCenterX = left + artW * (BAR_X / ART_W)
        val barY = top + artH * (BAR_Y / ART_H)
        Box(
            Modifier.offset(x = barCenterX - barW / 2, y = barY).width(barW).height(5.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = .12f)).graphicsLayer { alpha = ui }
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(phase(t, .2f, .8f))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7B4DFF), Color(0xFFB78CFF))), CircleShape)
            )
        }
        val dots = ".".repeat(1 + ((t * 9).toInt() % 3))
        Text(
            "LOADING$dots", color = Color(0xFFB9B4CC), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 4.sp,
            modifier = Modifier.offset(x = barCenterX - 52.dp, y = barY + 16.dp).graphicsLayer { alpha = ui },
        )
    }
}

/** A few cherry-blossom petals drifting down, matching the artwork. Cheap: ~10 ovals per frame. */
@Composable
private fun Petals(t: Float, modifier: Modifier) {
    Canvas(modifier) {
        val petals = 10
        for (i in 0 until petals) {
            val seed = i * 0.6180339f % 1f
            val x = size.width * ((seed * 1.3f + sin(t * 3f + i) * .04f) % 1f)
            val y = size.height * ((seed * .7f + t * (.35f + seed * .3f)) % 1.1f) - 20f
            val w = 10f + 14f * seed
            rotate(degrees = t * 220f * (if (i % 2 == 0) 1 else -1) + i * 40f, pivot = Offset(x, y)) {
                drawOval(
                    color = Color(0xFFFF8FD0).copy(alpha = .35f + .3f * seed),
                    topLeft = Offset(x - w / 2, y - w / 4), size = Size(w, w / 2),
                )
            }
        }
    }
}
