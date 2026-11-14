package com.animewave.app

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import java.io.File
import kotlinx.coroutines.delay

val Ink = Color(0xFF07080D)
val Panel = Color(0xFF151722)
val Violet = Color(0xFF9B6DFF)
val Pink = Color(0xFFFF5FA2)
val Muted = Color(0xFF9EA1B3)
val Gold = Color(0xFFFFC94D)
internal val Soft = Color(0xFFDADBE5)
internal val Brand = Brush.linearGradient(listOf(Violet, Pink))

/** Frosted surfaces: a faint white fill with a hairline edge reads as glass on the dark canvas. */
internal val Glass = Color.White.copy(alpha = .09f)
internal val Hairline = Color.White.copy(alpha = .12f)

/** Plus Jakarta Sans (SIL Open Font License, see licenses/). Black maps to ExtraBold, its heaviest cut. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

/** Material type scale re-set in Jakarta so every Text, button and dialog picks it up. */
val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    Typography(
        displayLarge = t.displayLarge.j(), displayMedium = t.displayMedium.j(), displaySmall = t.displaySmall.j(),
        headlineLarge = t.headlineLarge.j(), headlineMedium = t.headlineMedium.j(), headlineSmall = t.headlineSmall.j(),
        titleLarge = t.titleLarge.j(), titleMedium = t.titleMedium.j(), titleSmall = t.titleSmall.j(),
        bodyLarge = t.bodyLarge.j(), bodyMedium = t.bodyMedium.j(), bodySmall = t.bodySmall.j(),
        labelLarge = t.labelLarge.j(), labelMedium = t.labelMedium.j(), labelSmall = t.labelSmall.j(),
    )
}

/** Base style for texts that need a brush, stroke or shadow; keeps the app font. */
internal fun jakarta(
    brush: Brush? = null, shadow: Shadow? = null,
    drawStyle: androidx.compose.ui.graphics.drawscope.DrawStyle? = null,
) = TextStyle(fontFamily = Jakarta, brush = brush, shadow = shadow, drawStyle = drawStyle)

/** True on TVs and tablets: top tabs, bigger type, wider margins for TV overscan. */
val LocalWide = staticCompositionLocalOf { false }

/** Top bar to overlay on the Home hero; other screens get it above their content from Shell. */
val LocalTopBar = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/** Test hook so screenshot tests can supply artwork without the network. */
val LocalImageOverride = staticCompositionLocalOf<(String?) -> Painter?> { { null } }

val Tabs = listOf("Home", "Discover", "Search", "My List", "Player")
private val TabIcons = listOf(Icons.Filled.Home, Icons.Filled.Star, Icons.Filled.Search, Icons.Filled.Favorite, Icons.Filled.PlayArrow)

val Genres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Mystery", "Horror",
)

internal val gutter: Dp @Composable get() = if (LocalWide.current) 48.dp else 20.dp

internal fun scoreText(score: Int) = "${score / 10}.${score % 10}"

private fun meta(a: Anime) = listOfNotNull(
    a.year?.toString(),
    when (val f = a.format) { null -> null; "TV", "OVA", "ONA" -> f; "TV_SHORT" -> "TV Short"; else -> f.lowercase().replaceFirstChar(Char::uppercase) },
    a.episodes?.let { "$it eps" },
).joinToString("  •  ")

@Composable
fun Art(url: String?, modifier: Modifier, description: String? = null) {
    val override = LocalImageOverride.current(url)
    if (override != null) {
        Image(override, description, modifier, contentScale = ContentScale.Crop)
    } else {
        var loading by remember(url) { mutableStateOf(url != null) }
        Box(modifier) {
            if (loading) Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp)))
            AsyncImage(
                model = url, contentDescription = description, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { loading = it is AsyncImagePainter.State.Loading },
            )
        }
    }
}

// ---------- Chrome ----------

@Composable
fun Logo(size: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size((size * 1.9).dp))
        Spacer(Modifier.width(10.dp))
        Text("ANIME", fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = Color.White)
        Text("WAVE", fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, style = jakarta(brush = Brand))
    }
}

/** Top bar. On wide screens it holds the tabs; on phones the tabs live in [BottomTabs]. */
@Composable
fun TopBar(tab: String, tabFocus: FocusRequester, update: Boolean, onTab: (String) -> Unit) {
    val wide = LocalWide.current
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Ink.copy(alpha = .85f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = gutter, vertical = if (wide) 22.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Logo(if (wide) 24 else 20)
        Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide) Tabs.forEach { t -> TopTab(t, t == tab, if (t == tab) tabFocus else null) { onTab(t) } }
            if (update) UpdatePill { onTab("Settings") }
            IconTab(Icons.Filled.Settings, "Settings", tab == "Settings", if (tab == "Settings") tabFocus else null) { onTab("Settings") }
        }
    }
}

/** Phone-only bar that slides in, solid and frosted, once the page scrolls past its own header. */
@Composable
fun StickyBar(visible: Boolean, update: Boolean, settingsActive: Boolean, onTab: (String) -> Unit) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 2 },
    ) {
        Column(Modifier.fillMaxWidth().background(Color(0xF20A0B12))) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = gutter, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Logo(17)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (update) UpdatePill { onTab("Settings") }
                    IconTab(Icons.Filled.Settings, "Settings", settingsActive, null) { onTab("Settings") }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        }
    }
}

@Composable
private fun UpdatePill(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "pill")
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Brush.linearGradient(listOf(Color.White, Color.White)) else Brand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = if (focused) Ink else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Update", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (focused) Ink else Color.White)
    }
}

@Composable
private fun IconTab(icon: ImageVector, label: String, active: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.hasFocus }
            .clip(CircleShape)
            .background(if (focused) Color.White else if (active) Violet.copy(alpha = .35f) else Glass)
            .border(1.dp, if (focused || active) Color.Transparent else Hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
    ) {
        Icon(icon, contentDescription = label, tint = when { focused -> Ink; active -> Color.White; else -> Muted }, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TopTab(label: String, active: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label, fontSize = 17.sp,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
            color = when { focused -> Ink; active -> Color.White; else -> Muted },
        )
        Box(Modifier.padding(top = 4.dp).width(if (active && !focused) 18.dp else 0.dp).height(3.dp).background(Brand, CircleShape))
    }
}

/** Phone tab bar: a floating frosted dock above the gesture bar; pages scroll underneath it. */
@Composable
fun BottomTabs(tab: String, onTab: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, .45f to Ink.copy(alpha = .85f), 1f to Ink))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(66.dp)
                .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1D2030), Color(0xFF12141D))))
                .border(1.dp, Hairline, shape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tabs.forEachIndexed { i, t -> DockItem(TabIcons[i], t, t == tab, Modifier.weight(1f)) { onTab(t) } }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateDpAsState(if (active) 48.dp else 0.dp, label = "dock")
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(30.dp).width(52.dp)) {
            Box(Modifier.width(pill).height(30.dp).background(Brush.linearGradient(listOf(Violet.copy(alpha = .55f), Pink.copy(alpha = .45f))), CircleShape))
            Icon(icon, contentDescription = null, tint = if (active) Color.White else Muted, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label, fontSize = 10.5.sp, maxLines = 1,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else Muted,
        )
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = gutter, end = gutter, top = 4.dp, bottom = 10.dp)) {
        Text(title, fontSize = if (LocalWide.current) 40.sp else 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SectionTitle(text: String, caption: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 30.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text, fontSize = if (LocalWide.current) 23.sp else 20.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp, color = Color.White,
        )
        if (caption != null) {
            Spacer(Modifier.width(10.dp))
            Text(caption, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

// ---------- Buttons & chips ----------

@Composable
fun PrimaryButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn")
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.hasFocus },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) Violet else Color.White, contentColor = if (focused) Color.White else Ink),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round frosted icon with a caption below it: the secondary actions beside a big Play button. */
@Composable
fun ActionIcon(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "action")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(16.dp))
            .tappable(onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(if (focused) Color.White else Glass)
                .border(1.dp, if (focused) Color.Transparent else Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (focused) Ink else Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color.White else Soft, maxLines = 1)
    }
}

/** Small frosted fact tag (year, format, episode count) shown under titles. */
@Composable
private fun MetaTag(text: String, icon: ImageVector? = null, tint: Color = Gold) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Glass).border(1.dp, Hairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

private fun metaParts(a: Anime) = listOfNotNull(
    a.year?.toString(),
    when (val f = a.format) { null -> null; "TV", "OVA", "ONA" -> f; "TV_SHORT" -> "TV Short"; else -> f.lowercase().replaceFirstChar(Char::uppercase) },
    a.episodes?.let { if (it == 1) "1 ep" else "$it eps" },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaTags(anime: Anime, center: Boolean = false) {
    FlowRow(
        horizontalArrangement = if (center) Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        anime.score?.let { MetaTag(scoreText(it), Icons.Filled.Star) }
        metaParts(anime).forEach { MetaTag(it) }
        airingLabel(anime)?.let { MetaTag(it, Icons.Filled.DateRange, tint = vivid(anime.accent)) }
    }
}

@Composable
fun GhostButton(text: String?, icon: ImageVector, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn")
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.hasFocus },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color.White else Color.White.copy(alpha = .11f),
            contentColor = if (focused) Ink else Color.White,
        ),
        border = if (focused) null else BorderStroke(1.dp, Hairline),
        contentPadding = PaddingValues(horizontal = if (text == null) 14.dp else 18.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = text ?: description ?: "My List", modifier = Modifier.size(20.dp))
        if (text != null) {
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        fontSize = 15.sp,
        fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
        color = when { focused -> Ink; active -> Color.White; else -> Soft },
        modifier = Modifier
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Brush.linearGradient(listOf(Color.White, Color.White))
                    active -> Brand
                    else -> Brush.linearGradient(listOf(Glass, Glass))
                }
            )
            .border(1.dp, if (active || focused) Color.Transparent else Hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun Pill(text: String, brush: Brush = Brand) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = Color.White,
        modifier = Modifier.background(brush, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

@Composable
private fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Ink.copy(alpha = .72f))
            .border(1.dp, Hairline, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(scoreText(score), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ---------- Hero ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Hero(
    items: List<Anime>, saved: Set<Int>, watchFocus: FocusRequester,
    onWatch: (Anime) -> Unit, onDetails: (Anime) -> Unit, onToggleSave: (Anime) -> Unit,
) {
    val wide = LocalWide.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    var index by remember(items) { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(items, paused) {
        while (!paused && items.size > 1) { delay(8000); index = (index + 1) % items.size }
    }
    val anime = items[index]
    val inList = anime.id in saved
    val titleShadow = Shadow(Color.Black.copy(alpha = .75f), Offset(0f, 4f), 22f)
    val dots: @Composable () -> Unit = {
        if (items.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.indices.forEach { i ->
                val w by animateDpAsState(if (i == index) 22.dp else 6.dp, label = "dot")
                Box(Modifier.width(w).height(4.dp).background(if (i == index) Brand else Brush.linearGradient(listOf(Color.White.copy(alpha = .3f), Color.White.copy(alpha = .3f))), CircleShape))
            }
        }
    }

    if (wide) {
        // TV: atmospheric banner behind, crisp key art on the right, story and actions on the left.
        Box(Modifier.fillMaxWidth().height(screenH * 0.94f).clipToBounds()) {
            Crossfade(anime, animationSpec = tween(900), label = "heroBg") { a -> Art(a.banner ?: a.coverXL, Modifier.fillMaxSize().kenBurns().atmosphere(16.dp)) }
            if (!canBlur) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .25f)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink, .42f to Ink.copy(alpha = .85f), .72f to Ink.copy(alpha = .4f), 1f to Ink.copy(alpha = .55f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .7f), .25f to Color.Transparent, .7f to Color.Transparent, 1f to Ink)))
            val glow = vivid(anime.accent)
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = 52.dp, y = 20.dp).size(440.dp)
                    .background(Brush.radialGradient(listOf(glow.copy(alpha = .42f), Color.Transparent)))
            )
            Crossfade(anime, animationSpec = tween(900), label = "heroArt", modifier = Modifier.align(Alignment.CenterEnd).padding(end = gutter + 28.dp, top = 56.dp)) { a ->
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier.width(214.dp).aspectRatio(2f / 3f)
                        .graphicsLayer { rotationZ = 2.5f }
                        .shadow(40.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(shape).background(Panel).border(1.dp, Hairline, shape)
                ) { Art(a.coverXL, Modifier.fillMaxSize(), a.title) }
            }
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = gutter, end = gutter, bottom = 44.dp)
                    .fillMaxWidth(.52f)
            ) {
                Pill("#${index + 1} TRENDING")
                Spacer(Modifier.height(14.dp))
                Text(
                    anime.title, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = jakarta(shadow = titleShadow),
                )
                Spacer(Modifier.height(14.dp))
                MetaTags(anime)
                Spacer(Modifier.height(12.dp))
                Text(
                    anime.synopsis, color = Soft.copy(alpha = .9f), fontSize = 15.sp, lineHeight = 23.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(22.dp))
                FlowRow(
                    Modifier.onFocusChanged { paused = it.hasFocus },
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton("Watch", Icons.Filled.PlayArrow, Modifier.focusRequester(watchFocus)) { onWatch(anime) }
                    GhostButton("Details", Icons.Filled.Info) { onDetails(anime) }
                    GhostButton(if (inList) "In My List" else "My List", if (inList) Icons.Filled.Check else Icons.Filled.Add) { onToggleSave(anime) }
                }
                Spacer(Modifier.height(22.dp))
                dots()
            }
        }
        return
    }

    // Phone: full-bleed key art with a centred title block and a Play button flanked by actions.
    Box(Modifier.fillMaxWidth().height((screenH * 0.78f).coerceIn(520.dp, 700.dp)).clipToBounds()) {
        Crossfade(anime, animationSpec = tween(900), label = "hero") { a -> Art(a.coverXL ?: a.banner, Modifier.fillMaxSize().kenBurns()) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .7f), .18f to Color.Transparent, .48f to Color.Transparent, .78f to Ink.copy(alpha = .88f), 1f to Ink)))
        // Each title's own colour glows softly behind the title block.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(320.dp).offset(y = 110.dp)
                .background(Brush.radialGradient(listOf(vivid(anime.accent).copy(alpha = .30f), Color.Transparent)))
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = gutter, end = gutter, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Pill("#${index + 1} TRENDING")
            Spacer(Modifier.height(12.dp))
            Text(
                anime.title, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, style = jakarta(shadow = titleShadow),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                (anime.genres.take(3) + listOfNotNull(anime.score?.let { "★ " + scoreText(it) })).joinToString("  •  "),
                color = Soft, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().onFocusChanged { paused = it.hasFocus },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIcon(if (inList) Icons.Filled.Check else Icons.Filled.Add, "My List", Modifier.width(76.dp)) { onToggleSave(anime) }
                PrimaryButton("Watch", Icons.Filled.PlayArrow, Modifier.weight(1f).focusRequester(watchFocus)) { onWatch(anime) }
                ActionIcon(Icons.Filled.Info, "Details", Modifier.width(76.dp)) { onDetails(anime) }
            }
            Spacer(Modifier.height(18.dp))
            dots()
        }
    }
}

@Composable
fun HeroPlaceholder(failed: Boolean, onRetry: () -> Unit) {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    if (!failed) {
        HeroSkeleton(if (LocalWide.current) screenH * 0.94f else (screenH * 0.78f).coerceIn(520.dp, 700.dp))
        return
    }
    Box(
        Modifier.fillMaxWidth().height(screenH * 0.6f)
            .background(Brush.verticalGradient(listOf(Violet.copy(alpha = .25f), Ink))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't reach the anime catalog.", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Check your internet connection.", color = Muted)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Try again", Icons.Filled.Refresh, onClick = onRetry)
        }
    }
}

// ---------- Cards & rows ----------

@Composable
private fun Poster(anime: Anime, focused: Boolean, modifier: Modifier, showScore: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.aspectRatio(2f / 3f)
            .then(if (focused) Modifier.shadow(26.dp, shape, ambientColor = vivid(anime.accent), spotColor = vivid(anime.accent)) else Modifier)
            .clip(shape)
            .background(anime.accent?.let { Color(it).copy(alpha = .6f) } ?: Panel)
    ) {
        Art(anime.cover, Modifier.fillMaxSize(), anime.title)
        // Soft inner vignette so bright covers still sit in the dark UI.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.62f to Color.Transparent, 1f to Ink.copy(alpha = .55f))))
        if (showScore) anime.score?.let { ScoreBadge(it, Modifier.align(Alignment.TopEnd).padding(7.dp)) }
        if (hasNewEpisode(anime)) {
            Text(
                "NEW EP", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).background(Brand, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        airingLabel(anime)?.let { label ->
            Text(
                label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)
                    .background(Ink.copy(alpha = .72f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Hairline, shape))
    }
}

@Composable
fun PosterCard(anime: Anime, width: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "card")
    Column(
        Modifier.width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .tappable(onClick)
    ) {
        Poster(anime, focused, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            anime.title, fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.White else Soft, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(listOfNotNull(anime.year?.toString(), anime.genres.firstOrNull()).joinToString("  ·  "), fontSize = 11.5.sp, color = Muted, maxLines = 1)
    }
}

@Composable
private fun RankedCard(rank: Int, anime: Anime, onClick: () -> Unit) {
    val wide = LocalWide.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "rank")
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .tappable(onClick),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "$rank",
            fontSize = if (wide) 150.sp else 116.sp, lineHeight = if (wide) 150.sp else 116.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-8).sp,
            style = jakarta(
                brush = if (focused) Brand else Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .12f))),
                drawStyle = Stroke(width = 7f, join = StrokeJoin.Round),
            ).copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.offset(x = 16.dp, y = 16.dp),
        )
        Poster(anime, focused, Modifier.width(if (wide) 150.dp else 116.dp), showScore = false)
    }
}

/** Data class so screens skip recomposing when nothing changed. */
data class Loaded<T>(val value: T?, val failed: Boolean, val retry: () -> Unit)

fun <T, R> Loaded<T>.map(f: (T) -> R) = Loaded(value?.let(f), failed, retry)

@Composable
private fun LoadState(
    loaded: Loaded<List<Anime>>, empty: String,
    skeleton: @Composable () -> Unit = { RowSkeleton(if (LocalWide.current) 150.dp else 118.dp) },
    content: @Composable (List<Anime>) -> Unit,
) {
    val list = loaded.value
    when {
        loaded.failed -> Row(
            Modifier.padding(horizontal = gutter, vertical = 6.dp).widthIn(max = 620.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(Glass).border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Couldn't load. Check your connection.", color = Soft, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = loaded.retry) { Text("Retry", color = Violet, fontWeight = FontWeight.Bold) }
        }
        list == null -> skeleton()
        list.isEmpty() -> EmptyNote(empty.substringBefore(". ").removeSuffix("."), empty.substringAfter(". ", "").ifBlank { "Try something else." })
        else -> content(list.distinctBy { it.id })
    }
}

@Composable
fun AnimeRow(title: String, loaded: Loaded<List<Anime>>, empty: String = "Nothing found.", onPick: (Anime) -> Unit) {
    SectionTitle(title)
    LoadState(loaded, empty) { list ->
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (LocalWide.current) 18.dp else 14.dp),
        ) {
            items(list, key = { it.id }) { a -> PosterCard(a, if (LocalWide.current) 150.dp else 118.dp) { onPick(a) } }
        }
    }
}

@Composable
fun Top10Row(loaded: Loaded<List<Anime>>, onPick: (Anime) -> Unit) {
    SectionTitle("Top 10 Today")
    LoadState(loaded, "Nothing found.", skeleton = { RowSkeleton(if (LocalWide.current) 150.dp else 116.dp) }) { list ->
        LazyRow(
            contentPadding = PaddingValues(start = gutter - 12.dp, end = gutter, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (LocalWide.current) 12.dp else 8.dp),
        ) {
            itemsIndexed(list.take(10), key = { _, a -> a.id }) { i, a -> RankedCard(i + 1, a) { onPick(a) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeGrid(loaded: Loaded<List<Anime>>, empty: String = "Nothing found.", onPick: (Anime) -> Unit) {
    LoadState(loaded, empty, skeleton = { GridSkeleton() }) { list ->
        BoxWithConstraints(Modifier.padding(horizontal = gutter, vertical = 10.dp)) {
            val gap = if (LocalWide.current) 20.dp else 12.dp
            val cols = maxOf(3, ((maxWidth + gap) / ((if (LocalWide.current) 130.dp else 110.dp) + gap)).toInt())
            val w = (maxWidth - gap * (cols - 1)) / cols - 1.dp // 1dp slack so rounding never wraps a row early
            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                list.forEach { a -> PosterCard(a, w) { onPick(a) } }
            }
        }
    }
}

/** Search with nothing typed yet: recent searches and what's trending right now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestions(
    recent: List<String>, trending: List<Anime>,
    onRecent: (String) -> Unit, onClearRecent: () -> Unit, onPick: (Anime) -> Unit,
) {
    if (recent.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter - 8.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent searches", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClearRecent) { Text("Clear", color = Muted, fontWeight = FontWeight.SemiBold) }
        }
        FlowRow(
            Modifier.padding(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { recent.forEach { q -> Chip(q, false) { onRecent(q) } } }
    }
    if (trending.isNotEmpty()) {
        SectionTitle("Trending now")
        Column(Modifier.padding(horizontal = gutter - 8.dp).widthIn(max = 760.dp)) {
            trending.forEachIndexed { i, a -> TrendingRow(i + 1, a) { onPick(a) } }
        }
    } else if (recent.isEmpty()) {
        EmptyNote("Search every anime on AniList", "Type a title in English, Japanese or romaji.")
    }
}

@Composable
private fun TrendingRow(rank: Int, anime: Anime, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = .1f) else Color.Transparent)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .tappable(onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(34.dp),
            style = jakarta(brush = if (rank <= 3) Brand else Brush.linearGradient(listOf(Muted, Muted))),
        )
        Box(Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Panel)) {
            Art(anime.cover, Modifier.fillMaxSize(), anime.title)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(anime.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (listOfNotNull(anime.score?.let { "★ " + scoreText(it) }) + metaParts(anime).take(2) + anime.genres.take(1)).joinToString("  ·  "),
                fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp))
    }
}

/** Discover banner: the genre's most popular title as a wide, blurred showcase card. */
@Composable
fun GenreShowcase(genre: String, top: Anime?, onPick: (Anime) -> Unit) {
    val wide = LocalWide.current
    val shape = RoundedCornerShape(22.dp)
    if (top == null) {
        Box(Modifier.padding(horizontal = gutter, vertical = 12.dp).fillMaxWidth().height(if (wide) 210.dp else 170.dp).shimmer(shape))
        return
    }
    var focused by remember { mutableStateOf(false) }
    val accent = vivid(top.accent)
    Box(
        Modifier.padding(horizontal = gutter, vertical = 12.dp).fillMaxWidth().height(if (wide) 210.dp else 170.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape).background(Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Hairline, shape)
            .tappable { onPick(top) },
    ) {
        Art(top.banner ?: top.coverXL, Modifier.fillMaxSize().atmosphere(10.dp))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink.copy(alpha = .95f), .6f to Ink.copy(alpha = .55f), 1f to Ink.copy(alpha = .15f))))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = .28f), Color.Transparent))))
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#1 IN ${genre.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp,
                    style = jakarta(brush = Brush.linearGradient(listOf(accent, lerp(accent, Color.White, .45f)))))
                Spacer(Modifier.height(6.dp))
                Text(top.title, fontSize = if (wide) 28.sp else 22.sp, lineHeight = if (wide) 32.sp else 26.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                MetaTags(top)
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier.width(if (wide) 118.dp else 92.dp).aspectRatio(2f / 3f).graphicsLayer { rotationZ = 3f }
                    .shadow(24.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Panel)
            ) { Art(top.coverXL, Modifier.fillMaxSize(), top.title) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridSkeleton() {
    BoxWithConstraints(Modifier.padding(horizontal = gutter, vertical = 10.dp)) {
        val gap = if (LocalWide.current) 20.dp else 12.dp
        val cols = maxOf(3, ((maxWidth + gap) / ((if (LocalWide.current) 130.dp else 110.dp) + gap)).toInt())
        val w = (maxWidth - gap * (cols - 1)) / cols - 1.dp
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(cols * 2) { PosterSkeleton(w) }
        }
    }
}

@Composable
fun GenreChips(genre: String, onPick: (String) -> Unit) {
    LazyRow(
        Modifier.padding(top = 10.dp),
        contentPadding = PaddingValues(horizontal = gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Genres) { g -> Chip(g, g == genre) { onPick(g) } }
    }
}

@Composable
fun SearchBox(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 8.dp),
        placeholder = { Text("Titles, genres, characters…", color = Muted, fontSize = 17.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Muted) },
        singleLine = true,
        textStyle = TextStyle(fontFamily = Jakarta, fontSize = 18.sp, color = Color.White),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet, unfocusedBorderColor = Hairline,
            focusedContainerColor = Panel, unfocusedContainerColor = Panel,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Violet,
        ),
    )
}

// ---------- Detail ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailContent(
    anime: Anime, saved: Boolean, watchFocus: FocusRequester,
    onBack: () -> Unit, onWatch: (String?) -> Unit, onOpen: (String) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    selectedSourceId: String, onSourceSelected: (String) -> Unit,
    onToggleSave: () -> Unit, onPlayOwn: () -> Unit,
    providerDiscovery: ProviderDiscoveryResult? = null,
    /** Watched fraction per episode page URL, from playback history. */
    progress: Map<String, Float> = emptyMap(),
    /** True while providers are still being searched for episodes. */
    discovering: Boolean = false,
    providerDiscoveryFailed: Boolean = false,
    onRetryDiscovery: () -> Unit = {},
) {
    var showSourcePicker by remember { mutableStateOf(false) }
    var providerChoices by remember { mutableStateOf<List<ProviderEpisode>?>(null) }
    var storyOpen by remember(anime.id) { mutableStateOf(false) }
    val selectedSource = AnimeSourceRegistry.byId(selectedSourceId)
    val wide = LocalWide.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val official = anime.watchLinks.firstOrNull()
    val discoveredGroups = providerDiscovery?.groups.orEmpty()
    val discoveredEpisodes = discoveredGroups.flatMap { it.episodes }
        .map { Episode(it.title, it.thumbnail, it.pageUrl, it.providerName) }
        .ifEmpty { anime.streamingEpisodes }
    val accent = vivid(anime.accent)
    val watchLabel = official?.let { "Watch on ${it.first}" } ?: "Watch"
    val findOnSource = {
        val url = if (selectedSourceId == "listed") "https://anilist.co/anime/${anime.id}"
        else AnimeSourceRegistry.searchUrl(selectedSourceId, anime.title)
        onOpen(url)
    }

    val genresLine: @Composable () -> Unit = {
        if (anime.genres.isNotEmpty()) Text(
            anime.genres.take(3).joinToString("  ·  ").uppercase(), fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp, letterSpacing = 1.6.sp,
            style = jakarta(brush = Brush.linearGradient(listOf(accent, lerp(accent, Color.White, .45f)))),
        )
    }
    val title: @Composable (Boolean) -> Unit = { big ->
        Text(
            anime.title, fontSize = if (big) 42.sp else 28.sp, lineHeight = if (big) 46.sp else 32.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, maxLines = if (big) 2 else 3, overflow = TextOverflow.Ellipsis,
            textAlign = if (big) TextAlign.Start else TextAlign.Center,
            style = jakarta(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 18f)),
        )
    }
    val sourceCaption: @Composable () -> Unit = {
        Text("Browse source: ${selectedSource.displayName} · catalogue episode links are metadata, not video streams", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }

    providerChoices?.let { choices ->
        AlertDialog(
            onDismissRequest = { providerChoices = null },
            containerColor = Panel,
            title = { Text("Choose an episode source", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    choices.forEach { choice ->
                        TextButton(onClick = {
                            providerChoices = null
                            onPlayEpisode(Episode(choice.title, choice.thumbnail, choice.pageUrl, choice.providerName))
                        }) {
                            Text(choice.providerName, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { providerChoices = null }) { Text("Cancel") } },
        )
    }

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            containerColor = Panel,
            title = { Text("Choose a source", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Choose where episode searches should go. Sources without a built-in adapter open a host-scoped web search; native playback is available only for approved direct streams.", color = Soft, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    AnimeSourceRegistry.sources.forEach { source ->
                        TextButton(onClick = {
                            onSourceSelected(source.id)
                            showSourcePicker = false
                        }) {
                            Text(
                                text = (if (source.id == selectedSourceId) "✓  " else "") + source.displayName,
                                color = if (source.id == selectedSourceId) Violet else Color.White,
                                fontWeight = if (source.id == selectedSourceId) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSourcePicker = false }) { Text("Close") } },
        )
    }

    Column {
        if (wide) {
            // TV: everything you need fits on the first screen.
            Box(Modifier.fillMaxWidth().height(screenH).clipToBounds()) {
                Art(anime.banner ?: anime.coverXL, Modifier.fillMaxSize().atmosphere(14.dp))
                if (!canBlur) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .25f)))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink, .5f to Ink.copy(alpha = .84f), 1f to Ink.copy(alpha = .35f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .6f), .25f to Color.Transparent, .7f to Color.Transparent, 1f to Ink)))
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = gutter - 120.dp, y = 60.dp).size(440.dp)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = .32f), Color.Transparent)))
                )
                GhostButton("Back", Icons.Filled.ArrowBack, Modifier.statusBarsPadding().padding(start = gutter, top = 20.dp), onClick = onBack)
                Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 40.dp), verticalAlignment = Alignment.Bottom) {
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.width(190.dp).aspectRatio(2f / 3f)
                            .shadow(36.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(shape).background(Panel).border(1.dp, Hairline, shape)
                    ) { Art(anime.coverXL, Modifier.fillMaxSize(), anime.title) }
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.widthIn(max = 660.dp)) {
                        genresLine()
                        Spacer(Modifier.height(8.dp))
                        title(true)
                        Spacer(Modifier.height(14.dp))
                        MetaTags(anime)
                        Spacer(Modifier.height(14.dp))
                        Text(anime.synopsis, color = Soft, fontSize = 15.sp, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(22.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(watchLabel, Icons.Filled.PlayArrow, Modifier.focusRequester(watchFocus)) { onWatch(official?.second) }
                            GhostButton(if (saved) "In My List" else "My List", if (saved) Icons.Filled.Check else Icons.Filled.Add, onClick = onToggleSave)
                            GhostButton("Sources", Icons.AutoMirrored.Filled.List) { showSourcePicker = true }
                            GhostButton(null, Icons.Filled.Search, description = "Find on ${selectedSource.displayName}", onClick = findOnSource)
                            GhostButton(null, Icons.Filled.AddCircle, description = "Play my own file", onClick = onPlayOwn)
                        }
                        Spacer(Modifier.height(12.dp))
                        sourceCaption()
                    }
                }
            }
        } else {
            // Phone: blurred key art as atmosphere, a sharp poster on top, centred title block.
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                Art(anime.coverXL ?: anime.banner, Modifier.matchParentSize().atmosphere(28.dp))
                if (!canBlur) Box(Modifier.matchParentSize().background(Ink.copy(alpha = .3f)))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .55f), .4f to Ink.copy(alpha = .35f), .82f to Ink.copy(alpha = .94f), 1f to Ink)))
                Box(
                    Modifier.align(Alignment.Center).fillMaxWidth().height(420.dp)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = .30f), Color.Transparent)))
                )
                BackIcon(Modifier.statusBarsPadding().padding(start = gutter - 4.dp, top = 10.dp), onBack)
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = gutter, end = gutter, top = 58.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.width(158.dp).aspectRatio(2f / 3f)
                            .shadow(34.dp, shape, ambientColor = Color.Black, spotColor = accent)
                            .clip(shape).background(Panel)
                    ) {
                        Art(anime.coverXL, Modifier.fillMaxSize(), anime.title)
                        Box(Modifier.fillMaxSize().border(1.dp, Hairline, shape))
                    }
                    Spacer(Modifier.height(20.dp))
                    genresLine()
                    Spacer(Modifier.height(8.dp))
                    title(false)
                    Spacer(Modifier.height(12.dp))
                    MetaTags(anime, center = true)
                }
            }
            Column(Modifier.padding(horizontal = gutter)) {
                Spacer(Modifier.height(18.dp))
                PrimaryButton(watchLabel, Icons.Filled.PlayArrow, Modifier.fillMaxWidth().focusRequester(watchFocus)) { onWatch(official?.second) }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionIcon(if (saved) Icons.Filled.Check else Icons.Filled.Add, if (saved) "In My List" else "My List", onClick = onToggleSave)
                    ActionIcon(Icons.AutoMirrored.Filled.List, "Sources") { showSourcePicker = true }
                    ActionIcon(Icons.Filled.Search, "Find", onClick = findOnSource)
                    ActionIcon(Icons.Filled.AddCircle, "My file", onClick = onPlayOwn)
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    anime.synopsis, color = Soft, fontSize = 15.sp, lineHeight = 23.sp,
                    maxLines = if (storyOpen) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                )
                if (anime.synopsis.length > 200) Text(
                    if (storyOpen) "Show less" else "Read more", color = Violet, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { storyOpen = !storyOpen }.padding(vertical = 8.dp),
                )
                Spacer(Modifier.height(6.dp))
                sourceCaption()
            }
        }
        Column {
            if (anime.watchLinks.size > 1) {
                SectionTitle("Also streaming on")
                FlowRow(
                    Modifier.padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    anime.watchLinks.drop(1).distinctBy { it.first }.forEach { (site, url) -> Chip(site, false) { onOpen(url) } }
                }
            }
            val entries = remember(discoveredGroups, discoveredEpisodes, anime.id) {
                if (discoveredGroups.isNotEmpty()) discoveredGroups.map { group ->
                    val first = group.episodes.first()
                    EpisodeEntry(
                        key = group.key, number = group.episodeNumber, season = group.seasonNumber,
                        title = group.title, thumbnail = first.thumbnail, url = first.pageUrl,
                        site = if (group.episodes.size > 1) "${group.episodes.size} sources" else first.providerName,
                        native = OfficialPlaybackRegistry.find(anime.id, group.title) != null,
                        sources = group.episodes,
                    )
                } else discoveredEpisodes.mapIndexed { i, ep ->
                    EpisodeEntry(
                        key = ep.url + "#" + i, number = EpisodeMatcher.episodeNumber(ep.title), season = EpisodeMatcher.seasonNumber(ep.title),
                        title = ep.title, thumbnail = ep.thumbnail, url = ep.url, site = ep.site,
                        native = OfficialPlaybackRegistry.find(anime.id, ep.title) != null,
                    )
                }
            }
            SectionTitle("Episodes", caption = entries.size.takeIf { it > 0 }?.let { if (it == 1) "1 listed" else "$it listed" })
            providerDiscovery?.takeIf { it.providers.isNotEmpty() }?.let { result ->
                Text(
                    "Checked " + result.providers.count { it.available } + " provider(s) · " + discoveredEpisodes.size + " episode listing(s)",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = gutter, vertical = 2.dp),
                )
            }
            when {
                entries.isNotEmpty() -> EpisodeList(entries, accent, progress) { entry ->
                    // Equivalent listings from several providers: let the viewer choose which page to open.
                    val sources = entry.sources
                    if (sources != null && sources.size > 1) providerChoices = sources
                    else if (sources != null) sources.first().let { onPlayEpisode(Episode(it.title, it.thumbnail, it.pageUrl, it.providerName)) }
                    else onPlayEpisode(Episode(entry.title, entry.thumbnail, entry.url, entry.site))
                }
                discovering -> EpisodeListSkeleton()
                providerDiscoveryFailed -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Couldn't check episode sources. Check your connection and retry.", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetryDiscovery) { Text("Retry", color = Violet, fontWeight = FontWeight.Bold) }
                }
                else -> EmptyNote("No episode links listed for this title yet.", "Try another source, or play a file you own.")
            }
            if (wide && anime.synopsis.length > 280) {
                SectionTitle("Story")
                Text(anime.synopsis, color = Soft, fontSize = 16.sp, lineHeight = 26.sp, modifier = Modifier.padding(horizontal = gutter).widthIn(max = 820.dp))
            }
        }
    }
}

/** Frosted round Back button for screens that start with full-bleed artwork. */
@Composable
private fun BackIcon(modifier: Modifier, onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.onFocusChanged { focused = it.hasFocus }
            .size(44.dp).clip(CircleShape)
            .background(if (focused) Color.White else Ink.copy(alpha = .45f))
            .border(1.dp, if (focused) Color.Transparent else Hairline, CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = if (focused) Ink else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Empty state card: a title line and a hint, on a frosted panel. */
@Composable
private fun EmptyNote(title: String, hint: String) {
    Column(
        Modifier.padding(horizontal = gutter, vertical = 6.dp).widthIn(max = 620.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Glass).border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(hint, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
    }
}

/** One row in the episode list; [sources] holds equivalent listings from several providers. */
private data class EpisodeEntry(
    val key: String, val number: Int?, val season: Int?, val title: String, val thumbnail: String?,
    val url: String, val site: String, val native: Boolean, val sources: List<ProviderEpisode>? = null,
)

/** "Episode 12 - The Storm" → "The Storm"; titles that are only an episode label stay as they are. */
private fun episodeName(title: String): String {
    val stripped = title.replace(Regex("""^(?i)\s*(?:season\s*\d+\s*[·:,-]?\s*)?(?:episode|ep)\.?\s*\d+\s*[-:–—.]\s*"""), "").trim()
    return stripped.ifEmpty { title }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeList(
    entries: List<EpisodeEntry>, accent: Color, progress: Map<String, Float>, onPlay: (EpisodeEntry) -> Unit,
) {
    val seasons = remember(entries) { entries.mapNotNull { it.season }.distinct().sorted() }
    var season by remember(entries) { mutableStateOf(seasons.firstOrNull()) }
    var newestFirst by remember(entries) { mutableStateOf(false) }
    var showAll by remember(entries) { mutableStateOf(false) }
    val visible = remember(entries, season, newestFirst) {
        val inSeason = if (seasons.size > 1) entries.filter { it.season == season } else entries
        val ordered = inSeason.sortedWith(compareBy<EpisodeEntry>({ it.season ?: 0 }, { it.number ?: Int.MAX_VALUE }))
        if (newestFirst) ordered.reversed() else ordered
    }
    val limit = 12
    FlowRow(
        Modifier.padding(horizontal = gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (seasons.size > 1) seasons.forEach { n -> Chip("Season $n", n == season) { season = n; showAll = false } }
        if (visible.size > 1) Chip(if (newestFirst) "Newest first" else "Oldest first", false) { newestFirst = !newestFirst }
    }
    Column(
        Modifier.padding(horizontal = gutter - 8.dp).widthIn(max = 920.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (if (showAll) visible else visible.take(limit)).forEach { entry ->
            EpisodeRow(entry, accent, progress[entry.url] ?: 0f) { onPlay(entry) }
        }
    }
    if (!showAll && visible.size > limit) {
        GhostButton(
            "Show all ${visible.size} episodes", Icons.Filled.KeyboardArrowDown,
            Modifier.padding(start = gutter, top = 8.dp),
        ) { showAll = true }
    }
}

@Composable
private fun EpisodeRow(entry: EpisodeEntry, accent: Color, watched: Float, onClick: () -> Unit) {
    val wide = LocalWide.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val thumbShape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = .1f) else Color.Transparent)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .tappable(onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(if (wide) 176.dp else 136.dp).aspectRatio(16f / 9f).clip(thumbShape).background(Panel)
        ) {
            Art(entry.thumbnail, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .25f)))
            Box(
                Modifier.align(Alignment.Center).size(32.dp).clip(CircleShape)
                    .background(if (focused) Color.White else Ink.copy(alpha = .55f))
                    .border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = .4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = if (focused) Ink else Color.White, modifier = Modifier.size(19.dp)) }
            if (watched > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .2f))) {
                Box(Modifier.fillMaxWidth(watched.coerceIn(.03f, 1f)).height(3.dp).background(accent))
            }
            Box(Modifier.fillMaxSize().border(1.dp, Hairline, thumbShape))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val label = listOfNotNull(entry.season?.let { "S$it" }, entry.number?.let { "EPISODE $it" }).joinToString(" · ")
            if (label.isNotEmpty()) Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = accent)
            Text(
                episodeName(entry.title), fontSize = if (wide) 16.sp else 14.5.sp, lineHeight = if (wide) 21.sp else 19.sp,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            val status = when {
                watched >= .9f -> "Watched"
                watched > 0f -> "${(watched * 100).toInt()}% watched"
                entry.native -> "Plays in AnimeWave"
                else -> "Opens source page"
            }
            Text(
                listOf(entry.site, status).filter { it.isNotBlank() }.joinToString("  ·  "),
                fontSize = 11.5.sp, color = if (entry.native || watched > 0f) Soft else Muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

// ---------- Player tab ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerHome(episodes: List<LocalEpisode<Uri>>, onPickFiles: () -> Unit, onLink: () -> Unit, onPlay: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = gutter)) {
        Text(
            "Play episodes saved on this device. Pick .srt, .vtt or .ass files named like the video " +
                "(Ep 1.mkv + Ep 1.srt) and subtitles load automatically. Built-in subtitles are under the CC button.",
            color = Soft, fontSize = 16.sp, lineHeight = 23.sp,
            modifier = if (LocalWide.current) Modifier.widthIn(max = 760.dp) else Modifier,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Open videos", Icons.Filled.PlayArrow, onClick = onPickFiles)
            GhostButton("Play a stream link", Icons.Filled.Add, onClick = onLink)
        }
        if (episodes.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Text("Episodes (${episodes.size})", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            episodes.forEachIndexed { i, ep -> LocalEpisodeRow(i + 1, ep.video.name, ep.subtitle?.name) { onPlay(i) } }
        }
    }
}

@Composable
private fun LocalEpisodeRow(number: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(if (focused) Color.White else Panel)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (focused) Violet else Muted, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (focused) Ink else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text("CC  $subtitle", color = if (focused) Ink.copy(alpha = .7f) else Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = if (focused) Violet else Color.White)
    }
}

@Composable
fun LinkDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val clean = url.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Play a stream link") },
        text = {
            Column {
                Text("Paste an HTTPS video or HLS (.m3u8) link you have permission to play.", fontSize = 14.sp, color = Soft)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPlay(clean) }, enabled = clean.startsWith("https://")) { Text("Play") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Screens ----------

@Composable
fun HomeContent(
    home: Loaded<Map<String, List<Anime>>>, saved: Set<Int>, heroFocus: FocusRequester,
    onPick: (Anime) -> Unit, onWatch: (Anime) -> Unit, onToggleSave: (Anime) -> Unit,
    continueWatching: List<PlaybackRecord> = emptyList(),
    onResume: (PlaybackRecord) -> Unit = {},
    onPlayClassic: (() -> Unit)? = null,
) {
    val trending = home.value?.get("trending").orEmpty()
    Box {
        if (trending.isEmpty()) HeroPlaceholder(home.failed, home.retry)
        else Hero(trending.take(5), saved, heroFocus, onWatch, onPick, onToggleSave)
        LocalTopBar.current()
    }
    // Rows tuck up into the hero's fade for a layered look.
    Column(Modifier.offset(y = if (LocalWide.current) (-40).dp else (-4).dp)) {
        // Sections rise in one after another the first time Home appears.
        Column(Modifier.enterUp(0)) { ContinueWatchingSection(continueWatching, onResume) }
        Column(Modifier.enterUp(1)) { Top10Row(home.map { it["trending"].orEmpty() }, onPick) }
        if (onPlayClassic != null) Column(Modifier.enterUp(2)) { ClassicCard(onPlayClassic) }
        Column(Modifier.enterUp(3)) { AnimeRow("Airing This Season", home.map { it["airing"].orEmpty() }, onPick = onPick) }
        Column(Modifier.enterUp(4)) { AnimeRow("Action & Adventure", home.map { it["action"].orEmpty() }, onPick = onPick) }
        Column(Modifier.enterUp(5)) { AnimeRow("All-Time Popular", home.map { it["popular"].orEmpty() }, onPick = onPick) }
        Text(
            "Anime info from AniList.co  ·  Watch opens official streaming services",
            color = Muted.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = gutter, vertical = 28.dp),
        )
    }
}

@Composable
private fun ContinueWatchingSection(records: List<PlaybackRecord>, onResume: (PlaybackRecord) -> Unit) {
    if (records.isEmpty()) return
    SectionTitle("Continue Watching")
    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(records, key = { it.uri }) { record -> ResumeCard(record) { onResume(record) } }
    }
}

@Composable
private fun ResumeCard(record: PlaybackRecord, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "resume")
    val shape = RoundedCornerShape(16.dp)
    val (show, episode) = record.title.split(" — ", limit = 2).let { it[0] to it.getOrNull(1)?.substringBefore(" · ") }
    Column(
        Modifier.width(if (LocalWide.current) 300.dp else 250.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .tappable(onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .then(if (focused) Modifier.shadow(22.dp, shape, ambientColor = Violet, spotColor = Violet) else Modifier)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Violet.copy(alpha = .45f), Pink.copy(alpha = .25f), Color(0xFF141621))))
        ) {
            if (record.artwork != null) Art(record.artwork, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.35f to Color.Transparent, 1f to Ink.copy(alpha = .9f))))
            Box(
                Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape)
                    .background(if (focused) Color.White else Ink.copy(alpha = .5f))
                    .border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = .4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = if (focused) Ink else Color.White, modifier = Modifier.size(26.dp)) }
            Text(
                formatPlaybackTime((record.durationMs - record.positionMs).coerceAtLeast(0)) + " left",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Soft,
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            )
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .15f))) {
                Box(Modifier.fillMaxWidth(record.progress.coerceAtLeast(.02f)).height(4.dp).background(Brand))
            }
            Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Hairline, shape))
        }
        Spacer(Modifier.height(9.dp))
        Text(show, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (episode != null) Text(episode, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Public-domain Japanese animated short used to verify real anime playback in AnimeWave. */
@Composable
private fun ClassicCard(onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "classic")
    val shape = RoundedCornerShape(20.dp)
    SectionTitle("Classic anime", caption = "Public domain")
    Row(
        Modifier.padding(horizontal = gutter).widthIn(max = 720.dp).fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(Brush.linearGradient(listOf(Violet.copy(alpha = .32f), Pink.copy(alpha = .16f), Color(0xFF141621))))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Hairline, shape)
            .tappable(onPlay)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(if (focused) Color.White else Color.White.copy(alpha = .92f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Ink, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("PLAYS IN ANIMEWAVE", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, style = jakarta(brush = Brand))
            Spacer(Modifier.height(4.dp))
            Text("My Big Emergency", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Sanae Yamamoto  ·  1936", fontSize = 12.5.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1000).toInt()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

// ---------- Settings & updates ----------

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1E2C), Color(0xFF12141D))))
            .border(1.dp, Hairline, RoundedCornerShape(22.dp))
            .padding(22.dp)
    ) { content() }
}

@Composable
fun SettingsScreen(version: String, state: UpdateState, onCheck: () -> Unit, onDownload: (UpdateInfo) -> Unit, playbackGatewayUrl: String, onPlaybackGatewayUrlChange: (String) -> Unit, onInstall: (File) -> Unit) {
    Column(Modifier.padding(horizontal = gutter).widthIn(max = 720.dp)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("AnimeWave", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
                    Text("Version $version", color = Muted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Updates", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Violet)
            Spacer(Modifier.height(6.dp))
            Text(
                when (state) {
                    UpdateState.Idle -> "Check for the newest version of AnimeWave."
                    UpdateState.Checking -> "Checking for updates…"
                    UpdateState.UpToDate -> "You're on the latest version."
                    is UpdateState.Available -> "Version ${state.info.versionName} is available."
                    is UpdateState.Downloading -> "Downloading version ${state.info.versionName}…"
                    is UpdateState.Ready -> "Version ${state.info.versionName} is downloaded. Install it to update; your My List is kept."
                    is UpdateState.Failed -> state.message
                },
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
            val notes = when (state) {
                is UpdateState.Available -> state.info.notes
                is UpdateState.Ready -> state.info.notes
                else -> ""
            }
            if (notes.isNotBlank()) Text("What's new: $notes", color = Soft, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            if (state is UpdateState.Downloading) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Violet, trackColor = Color.White.copy(alpha = .1f),
                )
            }
            Spacer(Modifier.height(18.dp))
            when (state) {
                is UpdateState.Available -> PrimaryButton("Download & install", Icons.Filled.KeyboardArrowDown) { onDownload(state.info) }
                is UpdateState.Ready -> PrimaryButton("Install update", Icons.Filled.Check) { onInstall(state.apk) }
                is UpdateState.Downloading -> GhostButton("Downloading ${(state.progress * 100).toInt().coerceAtLeast(0)}%", Icons.Filled.KeyboardArrowDown) {}
                UpdateState.Checking -> GhostButton("Checking…", Icons.Filled.Refresh) {}
                else -> PrimaryButton("Check for updates", Icons.Filled.Refresh, onClick = onCheck)
            }
        }
        SettingsCard {
            Text("Playback service", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Violet)
            Spacer(Modifier.height(8.dp))
            Text(
                "Optional. Add your deployed AnimeWave playback service URL to proxy authorized HLS streams. Leave blank to play supported authorized streams directly.",
                color = Muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = playbackGatewayUrl,
                onValueChange = onPlaybackGatewayUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("HTTPS service URL") },
                placeholder = { Text("https://your-playback-service.example") },
            )
        }
        SettingsCard {
            Text("Install on another device", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Violet)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("5118333", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, style = jakarta(brush = Brand))
                Spacer(Modifier.width(12.dp))
                Text("Downloader code", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("github.com/G41US/AnimeWave/releases", color = Soft, fontSize = 14.sp)
        }
    }
}
