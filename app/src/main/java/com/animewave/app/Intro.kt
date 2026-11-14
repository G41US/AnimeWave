package com.animewave.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.sin

private fun phase(t: Float, start: Float, length: Float) = ((t - start) / length).coerceIn(0f, 1f)

/** Fast start, long soft landing: the curve luxury UIs use for things settling into place. */
private val Settle = CubicBezierEasing(.16f, 1f, .3f, 1f)
private val Accelerate = CubicBezierEasing(.7f, 0f, .84f, 0f)
private fun settle(x: Float) = Settle.transform(x.coerceIn(0f, 1f))

/** Ease for the reveal into Home, shared with MainActivity so the app settles in step with the iris. */
val RevealEasing = CubicBezierEasing(.65f, 0f, .35f, 1f)

/**
 * Title sequence played on launch while the catalog loads: the emblem materialises with a light
 * sweep, the wordmark tracks in, then (once data is ready) [onReady] fires and the caller animates
 * [reveal] 0 → 1, which opens an iris from the emblem onto Home.
 */
@Composable
fun IntroScreen(ready: () -> Boolean, reveal: () -> Float, onReady: () -> Unit) {
    val t = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1900, easing = LinearEasing))
        withTimeoutOrNull(2200) { snapshotFlow { isReady() }.first { it } }
        onReady()
    }
    IntroContent(t.value, reveal)
}

/** One frame of the title sequence; [t] runs 0 → 1, [exit] is the reveal progress (0 while loading). */
@Composable
fun IntroContent(t: Float, exit: () -> Float = { 0f }) {
    val stage = phase(t, 0f, .3f)
    val emblem = settle(phase(t, .06f, .5f))
    val sheen = phase(t, .38f, .32f)
    val words = settle(phase(t, .34f, .5f))
    val line = phase(t, .5f, .2f)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = 1f - phase(exit(), .72f, .28f)
            }
            .drawWithContent {
                drawContent()
                // Iris: a widening hole from the emblem lets Home show through.
                val hole = Accelerate.transform(phase(exit(), .12f, .88f))
                if (hole > 0f) {
                    val center = Offset(size.width / 2f, size.height / 2f - 40.dp.toPx())
                    val r = hypot(size.width, size.height) * hole
                    // Feathered edge so the opening feels like light, not a cut-out.
                    drawCircle(
                        Brush.radialGradient(0f to Color.Black, .78f to Color.Black, 1f to Color.Transparent, center = center, radius = r),
                        radius = r, center = center, blendMode = BlendMode.DstOut,
                    )
                }
            }
            .background(Color(0xFF030208)),
    ) {
        val wide = maxWidth > maxHeight
        val emblemSize = if (wide) 132.dp else 116.dp

        // Atmosphere: the key art, blurred and dim, slowly pulling back.
        Image(
            painterResource(R.drawable.loading_blur), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = .5f * stage
                val s = 1.12f - .08f * t
                scaleX = s; scaleY = s
            },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0f to Color.Transparent, .55f to Color(0xCC030208), 1f to Color(0xFF030208),
                    radius = maxOf(constraints.maxWidth, constraints.maxHeight) * .75f,
                )
            )
        )

        // Emblem centred 40dp above the middle; the iris opens from the same point.
        Box(Modifier.align(Alignment.Center).offset(y = (-40).dp), contentAlignment = Alignment.Center) {
            // Breathing glow behind the emblem.
            Box(
                Modifier.size(emblemSize * 2.6f).graphicsLayer {
                    alpha = emblem * (.55f + .15f * sin(t * 9f)) * (1f - phase(exit(), 0f, .4f))
                }.background(Brush.radialGradient(listOf(Violet.copy(alpha = .55f), Pink.copy(alpha = .12f), Color.Transparent)))
            )
            Image(
                painterResource(R.drawable.intro_emblem), contentDescription = "AnimeWave",
                modifier = Modifier.size(emblemSize)
                    .graphicsLayer {
                        val out = phase(exit(), 0f, .55f)
                        val s = (.78f + .22f * emblem) * (1f + .55f * Accelerate.transform(out))
                        scaleX = s; scaleY = s
                        alpha = emblem * (1f - out)
                        translationY = (1f - emblem) * 18.dp.toPx()
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // A band of light glides across the emblem once.
                        if (sheen in 0.001f..0.999f) {
                            val x = -size.width + sheen * size.width * 3f
                            drawRect(
                                Brush.linearGradient(
                                    0f to Color.Transparent, .5f to Color.White.copy(alpha = .7f), 1f to Color.Transparent,
                                    start = Offset(x, 0f), end = Offset(x + size.width * .6f, size.height),
                                ),
                                blendMode = BlendMode.SrcAtop,
                            )
                        }
                    },
            )
        }
        Column(
            Modifier.align(Alignment.Center).offset(y = (-40).dp + emblemSize / 2 + 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Wordmark tracks in from wide letter-spacing and settles.
            Row(
                Modifier.graphicsLayer {
                    alpha = words * (1f - phase(exit(), 0f, .35f))
                    translationY = (1f - words) * 14.dp.toPx()
                },
            ) {
                val spacing = (16f - 9f * words).sp
                Text("ANIME", fontSize = if (wide) 26.sp else 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = spacing, color = Color.White)
                Text(
                    "WAVE", fontSize = if (wide) 26.sp else 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = spacing,
                    style = jakarta(brush = Brush.linearGradient(listOf(Color(0xFFB78CFF), Pink))),
                )
            }
            Spacer(Modifier.height(26.dp))
            // Hairline progress instead of a "LOADING..." label.
            Box(
                Modifier.width(112.dp).height(2.dp).clip(CircleShape)
                    .graphicsLayer { alpha = line * (1f - phase(exit(), 0f, .3f)) }
                    .background(Color.White.copy(alpha = .1f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(.08f + .92f * phase(t, .5f, .5f))
                        .background(Brush.horizontalGradient(listOf(Violet, Pink)), CircleShape)
                )
            }
        }
    }
}
