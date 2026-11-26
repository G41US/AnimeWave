package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared between the launch animation and Home. */
object Boot {
    /** Home has its first content (or knows it's offline). */
    var ready by mutableStateOf(false)
    /** Where the top bar's logo tile sits, so the launch icon can land on it. */
    var logo by mutableStateOf<Rect?>(null)
}

/** Light sweep across the logo (repeating while Home loads), then the icon glides into the top bar and Home fades in. */
@Composable
fun Splash(onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val glide = remember { Animatable(0f) }
    var start by remember { mutableStateOf<Rect?>(null) }
    val still = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        appear.animateTo(1f, tween(220))
        val began = System.currentTimeMillis()
        do {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        } while (!Boot.ready && System.currentTimeMillis() - began < 4000)
        glide.animateTo(1f, tween(700, easing = CubicBezierEasing(.6f, 0f, .2f, 1f)))
        onDone()
    }
    val g = glide.value
    Box(Modifier.fillMaxSize()) {
        // The dark cover fades away to reveal Home underneath.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - ((g - .2f) / .65f).coerceIn(0f, 1f) }.background(Ink))
        Column(
            Modifier.align(Alignment.Center)
                .graphicsLayer { alpha = if (still) 1f else appear.value; scaleX = .94f + .06f * appear.value; scaleY = scaleX }
                .then(if (g == 0f && sweep.value > 0f && sweep.value < 1f) Modifier.sweep(sweep.value) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painterResource(R.drawable.logo_tile), null,
                Modifier.size(96.dp)
                    .onGloballyPositioned { if (g == 0f) start = it.boundsInRoot() }
                    .graphicsLayer {
                        val from = start; val to = Boot.logo
                        if (from != null && to != null && g > 0f) {
                            translationX = (to.center.x - from.center.x) * g
                            translationY = (to.center.y - from.center.y) * g
                            val s = 1f + (to.width / from.width - 1f) * g
                            scaleX = s; scaleY = s
                        } else if (g > 0f) alpha = 1f - g
                    },
            )
            Row(Modifier.graphicsLayer { alpha = 1f - (g / .35f).coerceIn(0f, 1f) }) {
                Text("ANIME", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, color = Color.White, style = jakarta())
                Text("WAVE", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, style = jakarta(Brand))
            }
        }
    }
}

/** A soft diagonal beam that lights up only the logo's own pixels. */
private fun Modifier.sweep(p: Float) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (p <= 0f || p >= 1f) return@drawWithContent
        val x = -size.width * .5f + size.width * 2f * p
        val band = size.width * .22f
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent, .5f to Color.White.copy(alpha = .75f), 1f to Color.Transparent,
                start = Offset(x - band, 0f), end = Offset(x + band * .2f, size.height * .6f),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }
