package com.animewave.app.stream

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.animewave.app.R

@Composable
fun GlassIcon(icon: Int, active: Boolean = false, size: Dp = 42.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (active) Brand else Brush.linearGradient(listOf(Glass, Glass)))
            .border(1.dp, if (active) Color.Transparent else Edge, CircleShape)
            .focusGlow(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = if (active) Color.White else Soft, modifier = Modifier.size(size * .5f)) }
}

@Composable
fun GhostButton(text: String, icon: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val lit = LocalWide.current && focused
    val ink = if (lit) Ink else Color.White
    Row(
        modifier.onFocusChanged { focused = it.hasFocus }.clip(RoundedCornerShape(14.dp))
            .background(if (lit) Color.White else if (LocalWide.current) Color.White.copy(alpha = .14f) else Glass)
            .border(1.dp, Edge, RoundedCornerShape(14.dp))
            .tappable(onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, style = jakarta(), maxLines = 1)
    }
}

@Composable
fun Message(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = jakarta())
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, color = Muted, style = jakarta(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(action, R.drawable.ic_check, onClick = onAction)
        }
    }
}

@Composable
fun Label(text: String) {
    Text(t(text).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Violet, style = jakarta())
}

@Composable
fun PrimaryButton(text: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tv = LocalWide.current
    var focused by remember { mutableStateOf(false) }
    val lit = !tv || focused
    Row(
        modifier.onFocusChanged { focused = it.hasFocus }.clip(RoundedCornerShape(14.dp))
            .background(if (lit) Color.White else Color.White.copy(alpha = .14f)).tappable(onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), null, tint = if (lit) Ink else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (lit) Ink else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.1).sp, style = jakarta(), maxLines = 1)
    }
}
