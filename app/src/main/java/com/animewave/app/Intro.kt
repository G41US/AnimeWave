package com.animewave.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private fun phase(t: Float, start: Float, length: Float) = ((t - start) / length).coerceIn(0f, 1f)

/** Overshoot easing for the logo pop. */
private fun backOut(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1
    val y = x - 1
    return 1 + c3 * y * y * y + c1 * y * y
}

/**
 * Title screen played on launch while the catalog loads. Lasts ~1.7s, waits up to
 * 1.8s more for the first data, then hands over. [ready] reports when home data arrived.
 */
@Composable
fun IntroScreen(ready: () -> Boolean, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1700, easing = LinearEasing))
        withTimeoutOrNull(1800) { snapshotFlow { isReady() }.first { it } }
        onDone()
    }
    IntroContent(t.value)
}

/** One frame of the title animation; [t] runs 0 → 1. */
@Composable
fun IntroContent(t: Float) {
    val wide = LocalWide.current
    val logo = backOut(phase(t, 0f, .4f))
    val title = phase(t, .25f, .3f)
    val tagline = phase(t, .45f, .3f)
    val glow = .35f + .25f * phase(t, 0f, .6f)
    val brand = Brush.linearGradient(listOf(Violet, Pink))

    Box(
        Modifier.fillMaxSize().background(Ink)
            .background(Brush.radialGradient(listOf(Violet.copy(alpha = glow), Color.Transparent), radius = if (wide) 900f else 700f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.intro_logo), contentDescription = "AnimeWave",
                modifier = Modifier.size(if (wide) 132.dp else 112.dp).graphicsLayer {
                    scaleX = .55f + .45f * logo; scaleY = .55f + .45f * logo
                    alpha = phase(t, 0f, .2f)
                },
            )
            Spacer(Modifier.height(if (wide) 28.dp else 22.dp))
            Row(Modifier.graphicsLayer { alpha = title; translationY = (1 - title) * 40f }) {
                Text("ANIME", fontSize = if (wide) 54.sp else 40.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = Color.White)
                Text("WAVE", fontSize = if (wide) 54.sp else 40.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, style = TextStyle(brush = brand))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "YOUR NEXT ANIME OBSESSION",
                fontSize = if (wide) 15.sp else 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp, color = Muted,
                modifier = Modifier.graphicsLayer { alpha = tagline; translationY = (1 - tagline) * 24f },
            )
        }
        // Loading bar
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 64.dp else 96.dp)
                .width(180.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))
                .graphicsLayer { alpha = tagline }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(phase(t, .3f, .7f)).background(brand, CircleShape))
        }
    }
}

