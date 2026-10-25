package com.animewave.app

import android.net.Uri
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.io.File
import kotlinx.coroutines.delay

val Ink = Color(0xFF07080D)
val Panel = Color(0xFF161824)
val Violet = Color(0xFF9B6DFF)
val Pink = Color(0xFFFF5FA2)
val Muted = Color(0xFFA9ABBA)
val Gold = Color(0xFFFFC94D)
private val Soft = Color(0xFFDADBE5)
private val Brand = Brush.linearGradient(listOf(Violet, Pink))

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

private val gutter: Dp @Composable get() = if (LocalWide.current) 48.dp else 20.dp

private fun scoreText(score: Int) = "${score / 10}.${score % 10}"

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
        AsyncImage(
            model = url,
            contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier,
        )
    }
}

// ---------- Chrome ----------

@Composable
fun Logo(size: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size((size * 1.9).dp))
        Spacer(Modifier.width(10.dp))
        Text("ANIME", fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Color.White)
        Text("WAVE", fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, style = TextStyle(brush = Brand))
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
            .background(if (focused) Color.White else if (active) Violet.copy(alpha = .35f) else Color.Transparent)
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

@Composable
fun BottomTabs(tab: String, onTab: (String) -> Unit) {
    NavigationBar(containerColor = Color(0xF20B0C12), contentColor = Color.White) {
        Tabs.forEachIndexed { i, t ->
            NavigationBarItem(
                selected = t == tab, onClick = { onTab(t) },
                icon = { Icon(TabIcons[i], contentDescription = null) },
                label = { Text(t, fontSize = 11.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = Color.White,
                    unselectedIconColor = Muted, unselectedTextColor = Muted,
                    indicatorColor = Violet.copy(alpha = .35f),
                ),
            )
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = gutter, end = gutter, top = 4.dp, bottom = 8.dp)) {
        Text(title, fontSize = if (LocalWide.current) 40.sp else 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 15.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Row(Modifier.padding(start = gutter, end = gutter, top = 30.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).background(Brand, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = if (LocalWide.current) 22.sp else 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) Violet else Color.White, contentColor = if (focused) Color.White else Ink),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun GhostButton(text: String?, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn")
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.hasFocus },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color.White else Color.White.copy(alpha = .14f),
            contentColor = if (focused) Ink else Color.White,
        ),
        contentPadding = PaddingValues(horizontal = if (text == null) 14.dp else 18.dp, vertical = 13.dp),
    ) {
        Icon(icon, contentDescription = text ?: "My List", modifier = Modifier.size(20.dp))
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
                    else -> Brush.linearGradient(listOf(Panel, Panel))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun Pill(text: String, brush: Brush = Brand) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Color.White,
        modifier = Modifier.background(brush, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Ink.copy(alpha = .78f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(scoreText(score), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
    Box(Modifier.fillMaxWidth().height(if (wide) screenH * 0.94f else (screenH * 0.72f).coerceAtMost(640.dp))) {
        Crossfade(anime, animationSpec = tween(900), label = "hero") { a ->
            Art(if (wide) a.banner ?: a.coverXL else a.coverXL ?: a.banner, Modifier.fillMaxSize())
        }
        if (wide) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink, 0.45f to Ink.copy(alpha = .6f), 0.75f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .55f), 0.3f to Color.Transparent, 0.55f to Color.Transparent, 0.88f to Ink.copy(alpha = .92f), 1f to Ink)))
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(start = gutter, end = gutter, bottom = if (wide) 60.dp else 24.dp)
                .fillMaxWidth(if (wide) .52f else 1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("#${index + 1} TRENDING")
                anime.score?.let {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text(" ${scoreText(it)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                anime.title,
                fontSize = if (wide) 42.sp else 32.sp, lineHeight = if (wide) 46.sp else 36.sp,
                fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 4f), 18f)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                listOf(meta(anime), anime.genres.take(3).joinToString(" · ")).filter { it.isNotEmpty() }.joinToString("   |   "),
                color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                anime.synopsis, color = Soft.copy(alpha = .9f), fontSize = if (wide) 16.sp else 14.sp,
                lineHeight = if (wide) 23.sp else 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            FlowRow(
                Modifier.onFocusChanged { paused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryButton("Watch", Icons.Filled.PlayArrow, Modifier.focusRequester(watchFocus)) { onWatch(anime) }
                GhostButton("Details", Icons.Filled.Info) { onDetails(anime) }
                val inList = anime.id in saved
                GhostButton(if (!wide) null else if (inList) "In My List" else "My List", if (inList) Icons.Filled.Check else Icons.Filled.Add) { onToggleSave(anime) }
            }
            if (items.size > 1) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.indices.forEach { i ->
                        val w by animateDpAsState(if (i == index) 26.dp else 8.dp, label = "dot")
                        Box(Modifier.width(w).height(5.dp).background(if (i == index) Brand else Brush.linearGradient(listOf(Color.White.copy(alpha = .35f), Color.White.copy(alpha = .35f))), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun HeroPlaceholder(failed: Boolean, onRetry: () -> Unit) {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    Box(
        Modifier.fillMaxWidth().height(screenH * 0.6f)
            .background(Brush.verticalGradient(listOf(Violet.copy(alpha = .25f), Ink))),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't reach the anime catalog.", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Check your internet connection.", color = Muted)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Try again", Icons.Filled.Refresh, onClick = onRetry)
        } else Text("Loading the latest anime…", color = Muted, fontSize = 16.sp)
    }
}

// ---------- Cards & rows ----------

@Composable
private fun Poster(anime: Anime, focused: Boolean, modifier: Modifier, showScore: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.aspectRatio(2f / 3f)
            .then(if (focused) Modifier.shadow(22.dp, shape, ambientColor = Violet, spotColor = Violet) else Modifier)
            .clip(shape)
            .background(anime.accent?.let { Color(it) } ?: Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .08f), shape)
    ) {
        Art(anime.cover, Modifier.fillMaxSize(), anime.title)
        if (showScore) anime.score?.let { ScoreBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
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
            .clickable(onClick = onClick)
    ) {
        Poster(anime, focused, Modifier.fillMaxWidth())
        Spacer(Modifier.height(9.dp))
        Text(
            anime.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.White else Soft, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(listOfNotNull(anime.year?.toString(), anime.genres.firstOrNull()).joinToString(" • "), fontSize = 12.sp, color = Muted, maxLines = 1)
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
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "$rank",
            fontSize = if (wide) 150.sp else 112.sp, lineHeight = if (wide) 150.sp else 112.sp,
            fontWeight = FontWeight.Black, letterSpacing = (-6).sp,
            color = if (focused) Violet else Color(0xFF9497AE),
            style = TextStyle(
                drawStyle = Stroke(width = 8f, join = StrokeJoin.Round),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            modifier = Modifier.offset(x = 14.dp, y = 14.dp),
        )
        Poster(anime, focused, Modifier.width(if (wide) 150.dp else 112.dp), showScore = false)
    }
}

/** Data class so screens skip recomposing when nothing changed. */
data class Loaded<T>(val value: T?, val failed: Boolean, val retry: () -> Unit)

fun <T, R> Loaded<T>.map(f: (T) -> R) = Loaded(value?.let(f), failed, retry)

@Composable
private fun LoadState(loaded: Loaded<List<Anime>>, empty: String, content: @Composable (List<Anime>) -> Unit) {
    val list = loaded.value
    val note = Modifier.padding(horizontal = gutter, vertical = 8.dp)
    when {
        loaded.failed -> Row(note, verticalAlignment = Alignment.CenterVertically) {
            Text("Couldn't load. Check your connection.", color = Muted)
            TextButton(onClick = loaded.retry) { Text("Retry", color = Violet, fontWeight = FontWeight.Bold) }
        }
        list == null -> Text("Loading…", color = Muted, modifier = note)
        list.isEmpty() -> Text(empty, color = Muted, fontSize = 15.sp, modifier = note)
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
    LoadState(loaded, "Nothing found.") { list ->
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
    LoadState(loaded, empty) { list ->
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
        textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet, unfocusedBorderColor = Color(0xFF2C2E3D),
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
    onToggleSave: () -> Unit, onPlayOwn: () -> Unit,
) {
    val wide = LocalWide.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val official = anime.watchLinks.firstOrNull()
    val accent = anime.accent?.let { Color(it) } ?: Panel

    val genresLine: @Composable () -> Unit = {
        Text(anime.genres.take(3).joinToString("  ·  ").uppercase(), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
    val titleBlock: @Composable () -> Unit = {
        Text(
            anime.title, fontSize = if (wide) 40.sp else 26.sp, lineHeight = if (wide) 44.sp else 30.sp,
            fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, maxLines = if (wide) 2 else 3, overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 3f), 14f)),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            anime.score?.let {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                Text(" ${scoreText(it)}   ", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(meta(anime), color = Soft, fontSize = 14.sp)
        }
    }
    val buttons: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(official?.let { "Watch on ${it.first}" } ?: "Watch", Icons.Filled.PlayArrow, Modifier.focusRequester(watchFocus)) { onWatch(official?.second) }
            GhostButton(if (saved) "In My List" else "My List", if (saved) Icons.Filled.Check else Icons.Filled.Add, onClick = onToggleSave)
            GhostButton("Play my own file", Icons.Filled.PlayArrow, onClick = onPlayOwn)
        }
    }
    val poster: @Composable (Dp) -> Unit = { w ->
        val shape = RoundedCornerShape(14.dp)
        Box(Modifier.width(w).aspectRatio(2f / 3f).shadow(24.dp, shape).clip(shape).background(accent)) {
            Art(anime.coverXL, Modifier.fillMaxSize(), anime.title)
        }
    }

    Column {
        if (wide) {
            // TV: everything you need fits on the first screen.
            Box(Modifier.fillMaxWidth().height(screenH)) {
                Art(anime.banner ?: anime.coverXL, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink, 0.55f to Ink.copy(alpha = .75f), 1f to Ink.copy(alpha = .25f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .5f), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to Ink)))
                GhostButton("Back", Icons.Filled.ArrowBack, Modifier.statusBarsPadding().padding(start = gutter, top = 20.dp), onClick = onBack)
                Row(Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 36.dp), verticalAlignment = Alignment.Bottom) {
                    poster(168.dp)
                    Spacer(Modifier.width(32.dp))
                    Column(Modifier.widthIn(max = 640.dp)) {
                        genresLine()
                        Spacer(Modifier.height(6.dp))
                        titleBlock()
                        Spacer(Modifier.height(12.dp))
                        Text(anime.synopsis, color = Soft, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(18.dp))
                        buttons()
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Art(anime.banner ?: anime.coverXL, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .6f), 0.3f to Color.Transparent, 1f to Ink)))
                GhostButton("Back", Icons.Filled.ArrowBack, Modifier.statusBarsPadding().padding(start = gutter, top = 16.dp), onClick = onBack)
            }
            Row(Modifier.padding(horizontal = gutter).offset(y = (-90).dp), verticalAlignment = Alignment.Bottom) {
                poster(120.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = 4.dp)) { genresLine(); Spacer(Modifier.height(6.dp)); titleBlock() }
            }
            Column(Modifier.padding(horizontal = gutter).offset(y = (-70).dp)) {
                buttons()
                Spacer(Modifier.height(20.dp))
                Text(anime.synopsis, color = Soft, fontSize = 16.sp, lineHeight = 25.sp)
            }
        }
        Column(Modifier.offset(y = if (wide) 0.dp else (-60).dp)) {
            if (anime.watchLinks.size > 1) {
                SectionTitle("Also Streaming On")
                FlowRow(
                    Modifier.padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    anime.watchLinks.drop(1).forEach { (site, url) -> Chip(site, false) { onOpen(url) } }
                }
            }
            SectionTitle("Episodes")
            if (anime.streamingEpisodes.isEmpty()) {
                Text("No episode links listed for this title yet.", color = Muted, modifier = Modifier.padding(horizontal = gutter))
            } else {
                // AniList's order; each opens the episode on the official site.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(anime.streamingEpisodes) { ep -> EpisodeCard(ep) { onOpen(ep.url) } }
                }
            }
            if (wide && anime.synopsis.length > 280) {
                SectionTitle("Story")
                Text(anime.synopsis, color = Soft, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(horizontal = gutter).widthIn(max = 820.dp))
            }
        }
    }
}

@Composable
private fun EpisodeCard(ep: Episode, onClick: () -> Unit) {
    val wide = LocalWide.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "ep")
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(if (wide) 260.dp else 210.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .then(if (focused) Modifier.shadow(20.dp, shape, ambientColor = Violet, spotColor = Violet) else Modifier)
                .clip(shape).background(Panel)
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .08f), shape)
        ) {
            Art(ep.thumbnail, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Ink.copy(alpha = .8f))))
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(44.dp).background(Ink.copy(alpha = .55f), CircleShape).padding(8.dp),
            )
            Text(ep.site, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Soft, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(ep.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
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
) {
    val trending = home.value?.get("trending").orEmpty()
    Box {
        if (trending.isEmpty()) HeroPlaceholder(home.failed, home.retry)
        else Hero(trending.take(5), saved, heroFocus, onWatch, onPick, onToggleSave)
        LocalTopBar.current()
    }
    // Rows tuck up into the hero's fade for a layered look.
    Column(Modifier.offset(y = if (LocalWide.current) (-40).dp else (-12).dp)) {
        Top10Row(home.map { it["trending"].orEmpty() }, onPick)
        AnimeRow("Airing This Season", home.map { it["airing"].orEmpty() }, onPick = onPick)
        AnimeRow("Action & Adventure", home.map { it["action"].orEmpty() }, onPick = onPick)
        AnimeRow("All-Time Popular", home.map { it["popular"].orEmpty() }, onPick = onPick)
        Text(
            "Anime info from AniList.co  ·  Watch opens official streaming services",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = gutter, vertical = 28.dp),
        )
    }
}

// ---------- Settings & updates ----------

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)).background(Panel)
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp))
            .padding(22.dp)
    ) { content() }
}

@Composable
fun SettingsScreen(version: String, state: UpdateState, onCheck: () -> Unit, onDownload: (UpdateInfo) -> Unit, onInstall: (File) -> Unit) {
    Column(Modifier.padding(horizontal = gutter).widthIn(max = 720.dp)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("AnimeWave", fontSize = 22.sp, fontWeight = FontWeight.Black)
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
            Text("Install on another device", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Violet)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("5118333", fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, style = TextStyle(brush = Brand))
                Spacer(Modifier.width(12.dp))
                Text("Downloader code", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("github.com/G41US/AnimeWave/releases", color = Soft, fontSize = 14.sp)
        }
    }
}
