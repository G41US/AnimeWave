package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
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
import androidx.compose.foundation.layout.fillMaxHeight
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

// ---------- Brand ----------

@Composable
fun Logo(size: Int = 20, anchor: Boolean = false, name: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_tile), null, Modifier.size((size * 1.5).dp)
            .then(if (anchor) Modifier.onGloballyPositioned { Boot.logo = it.boundsInRoot() } else Modifier))
        if (!name) return@Row
        Spacer(Modifier.width(10.dp))
        Text("ANIME", fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = Color.White, style = jakarta(), maxLines = 1, softWrap = false)
        Text("WAVE", fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, style = jakarta(Brand), maxLines = 1, softWrap = false)
    }
}

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

/** Top bar over a scrim so it stays readable on hero artwork; sits below the status bar. */
@Composable
fun TopBar(title: String? = null, onBack: (() -> Unit)? = null, settingsActive: Boolean = false, tab: Tab? = null, onTab: ((Tab) -> Unit)? = null, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Ink.copy(alpha = .92f), Ink.copy(alpha = .55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = Gutter, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIcon(R.drawable.ic_back, size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Box(if (LocalWide.current && onTab != null) Modifier.padding(end = 12.dp) else Modifier.weight(1f)) {
            if (title != null && !(LocalWide.current && onTab != null)) Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = jakarta())
            else Logo(anchor = true)
        }
        if (LocalWide.current && onTab != null) {
            Spacer(Modifier.weight(1f))
            val tight = screenDp().width < 900.dp
            Row(horizontalArrangement = Arrangement.spacedBy(if (tight) 2.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tab.entries.forEach { t ->
                    val active = t == tab
                    Text(t.label, fontSize = if (tight) 14.sp else 16.sp, maxLines = 1, softWrap = false, style = jakarta(), fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (active) Color.White else Soft,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) Brush.linearGradient(listOf(Violet.copy(alpha = .45f), Pink.copy(alpha = .35f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .then(if (active) Modifier.tvFirstFocus(t) else Modifier).focusGlow(RoundedCornerShape(50)).clickable { onTab(t) }.padding(horizontal = if (tight) 11.dp else 16.dp, vertical = 9.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        GlassIcon(R.drawable.ic_settings, active = settingsActive, onClick = onSettings)
    }
}

enum class Tab(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    TV("Series", R.drawable.ic_tv),
    MOVIES("Movies", R.drawable.ic_movies),
    SEARCH("Search", R.drawable.ic_search),
    LIST("My List", R.drawable.ic_bookmark),
}

/** Floating frosted dock above the gesture bar; pages scroll underneath it. */
@Composable
fun Dock(tab: Tab?, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, .45f to Ink.copy(alpha = .85f), 1f to Ink))
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(66.dp)
                .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1D2030), Color(0xFF12141D))))
                .border(1.dp, Hairline, shape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t -> DockItem(t, t == tab, Modifier.weight(1f)) { onTab(t) } }
        }
    }
}

@Composable
private fun DockItem(t: Tab, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateDpAsState(if (active) 46.dp else 0.dp, label = "dock")
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).focusGlow(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(30.dp).width(50.dp)) {
            Box(Modifier.width(pill).height(30.dp).background(Brush.linearGradient(listOf(Violet.copy(alpha = .55f), Pink.copy(alpha = .45f))), CircleShape))
            Icon(painterResource(t.icon), null, tint = if (active) Color.White else Muted, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            t.label, fontSize = 10.sp, maxLines = 1, style = jakarta(),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else Muted,
        )
    }
}

// ---------- Text & buttons ----------

@Composable
fun SectionTitle(text: String, caption: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = if (LocalWide.current) 22.sp else 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Color.White, style = jakarta())
        if (caption != null) {
            Spacer(Modifier.width(10.dp))
            Text(caption, fontSize = 13.sp, color = Muted, style = jakarta(), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
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
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, style = jakarta(),
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) Color.White else Soft,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (active) Brand else Brush.linearGradient(listOf(Glass, Glass)))
            .border(1.dp, if (active) Color.Transparent else Edge, RoundedCornerShape(50))
            .focusGlow(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
fun Badge(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp, color = Soft, style = jakarta(),
    )
}

@Composable
fun Meta(t: Title, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        rating(t.rating)?.let {
            Icon(painterResource(R.drawable.ic_star), null, tint = Gold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = jakarta())
            Text("   ·   ", fontSize = 13.sp, color = Muted)
        }
        Text(listOfNotNull(t.year, if (t.kind == Kind.TV) "Series" else "Movie", extra).joinToString("   ·   "), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Soft, style = jakarta())
    }
}

// ---------- Cards ----------

@Composable
fun Poster(t: Title, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, Edge, RoundedCornerShape(12.dp))) {
        NetImage(Tmdb.img(t.poster, if (LocalWide.current) "w500" else "w342"), t.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun PosterCard(t: Title, width: Dp, onClick: () -> Unit) {
    if (LocalWide.current) {
        // TV: artwork only; the name appears under the poster you're on.
        var focused by remember { mutableStateOf(false) }
        Column(Modifier.width(width)) {
            Box(Modifier.onFocusChanged { focused = it.hasFocus }.tappableCard(RoundedCornerShape(12.dp), onClick)) { Poster(t, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(10.dp))
            Text(if (focused) t.name else "", fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = jakarta(),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(Modifier.width(width).tappable(onClick)) {
        Poster(t, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(t.name, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = Soft, style = jakarta(),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(t.year, if (t.kind == Kind.TV) "Series" else "Movie").joinToString("  ·  "), fontSize = 11.5.sp, color = Muted, style = jakarta(), maxLines = 1)
    }
}

@Composable
fun PosterRow(row: Row, onOpen: (Title) -> Unit) {
    SectionTitle(rowTitle(row.name))
    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(if (row.ranked) 4.dp else if (LocalWide.current) 16.dp else 12.dp)) {
        itemsIndexed(row.items, key = { _, t -> t.key }) { i, t ->
            if (row.ranked) RankedCard(i + 1, t) { onOpen(t) } else PosterCard(t, if (LocalWide.current) 176.dp else 118.dp) { onOpen(t) }
        }
    }
}

/** Top 10 card: a big, heavy chrome numeral tucked under the poster. */
@Composable
fun RankedCard(rank: Int, t: Title, onClick: () -> Unit) {
    val wide = LocalWide.current
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.onFocusChanged { focused = it.hasFocus }.clickable(interactionSource = null, indication = null, onClick = onClick), verticalAlignment = Alignment.Bottom) {
        val size = if (wide) 176.sp else 120.sp
        val chrome = Brush.verticalGradient(0f to Color.White, .55f to Color(0xFFE4DEFF), 1f to Color(0xFF8F86C2))
        Box(Modifier.offset(x = 14.dp, y = 16.dp)) {
            // A stroke of the same chrome under the fill makes the digits heavier than the font allows.
            Text(rank.toString(), fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, letterSpacing = (-8).sp,
                style = jakarta(chrome).copy(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = if (wide) 14f else 9f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
            Text(rank.toString(), fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, letterSpacing = (-8).sp, style = jakarta(chrome))
        }
        Poster(t, Modifier.width(if (wide) 168.dp else 112.dp).lift(focused, RoundedCornerShape(12.dp)))
    }
}

@Composable
fun ContinueCard(p: Progress, progress: Float? = null, leftMs: Long? = null, onClick: () -> Unit) {
    val tv = LocalWide.current
    if (!tv) {
        Column(Modifier.width(220.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tappableCard(RoundedCornerShape(14.dp), onClick).clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Edge, RoundedCornerShape(14.dp))) {
                NetImage(Tmdb.img(p.title.backdrop ?: p.title.poster, "w500"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to Ink.copy(alpha = .85f))))
                Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_play_fill), null, tint = Ink, modifier = Modifier.size(20.dp))
                }
                if (p.title.kind == Kind.TV) Text(
                    "Episode ${p.episode}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, style = jakarta(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 14.dp),
                )
                if (progress != null && progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Brand))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(p.title.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Soft, style = jakarta(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    // TV: clean artwork with a slim progress bar; the name and time left sit underneath.
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(300.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.hasFocus }.tappableCard(shape, onClick).clip(shape).background(Panel),
        ) {
            NetImage(Tmdb.img(p.title.backdrop ?: p.title.poster, "w500"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.6f to Color.Transparent, 1f to Ink.copy(alpha = .6f))))
            // Play mark only on the card you're on.
            androidx.compose.animation.AnimatedVisibility(focused, Modifier.align(Alignment.Center), enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_play_fill), null, tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            if (progress != null && progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .28f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(p.title.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (focused) Color.White else Soft, style = jakarta(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val left = leftMs?.let { ms -> val m = (ms / 60_000).toInt().coerceAtLeast(1); if (m >= 60) "${m / 60}h ${m % 60}m left" else "${m}m left" }
        val sub = listOfNotNull(if (p.title.kind == Kind.TV) "Episode ${p.episode}" else null, left).joinToString("  ·  ")
        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted, style = jakarta(), maxLines = 1)
    }
}

// ---------- Hero ----------

@Composable
fun Hero(items: List<Title>, saved: (Title) -> Boolean, onPlay: (Title) -> Unit, onOpen: (Title) -> Unit, onToggle: (Title) -> Unit) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    // Holds still while the remote is on its buttons, so Play plays what you're looking at.
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(items, paused) { while (items.size > 1 && !paused) { delay(7000); index = (index + 1) % items.size } }
    val wide = LocalWide.current
    val heroH = if (wide) (screenDp().height * .72f).coerceAtLeast(380.dp) else 560.dp
    Box(Modifier.fillMaxWidth().height(heroH).clipToBounds()) {
        val pre = LocalPreload.current
        val arts = remember(items) { androidx.compose.runtime.mutableStateMapOf<String, Art>().apply { items.forEach { t -> (pre["art:${t.key}"] as? Art)?.let { put(t.key, it) } } } }
        LaunchedEffect(items) { items.forEach { t -> if (t.key !in arts) arts[t.key] = Tmdb.art(t) } }
        AnimatedContent(items[index], transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "hero") { t ->
            Box(Modifier.fillMaxSize()) {
                val art = arts[t.key]
                val image = when {
                    wide -> Tmdb.img(t.backdrop ?: art?.image, "w1280")
                    art == null -> null
                    else -> Tmdb.img(art.image, if (art.wide) "w1280" else "w780")
                }
                if (image != null) NetImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().kenBurns())
                if (wide) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink.copy(alpha = .85f), .55f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .35f), .3f to Color.Transparent, .62f to Ink.copy(alpha = .55f), 1f to Ink)))
            }
        }
        val t = items[index]
        Column(Modifier.align(Alignment.BottomStart).padding(start = if (wide) 48.dp else Gutter, end = Gutter, bottom = 18.dp)
            .then(if (wide) Modifier.width(560.dp) else Modifier).enterUp()) {
            val logo = arts[t.key]?.logo
            val tvx = LocalTv.current
            if (logo != null) NetImage(Tmdb.img(logo, "w500"), t.name, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                modifier = if (tvx) Modifier.width(380.dp).height(118.dp) else Modifier.width(260.dp).height(84.dp))
            else Text(t.name, fontSize = if (wide) 40.sp else 34.sp, lineHeight = if (wide) 44.sp else 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = Color.White,
                style = jakarta(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Meta(t)
            if (wide && t.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(t.overview, fontSize = 14.sp, lineHeight = 20.sp, color = if (tvx) Soft.copy(alpha = .8f) else Soft, style = jakarta(), maxLines = if (tvx) 2 else 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.onFocusChanged { paused = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Play", R.drawable.ic_play_fill, if (wide) Modifier.widthIn(min = 200.dp).tvFirstFocus("hero") else Modifier.weight(1f)) { onPlay(t) }
                GhostButton(if (saved(t)) "Saved" else "My List", if (saved(t)) R.drawable.ic_check else R.drawable.ic_plus) { onToggle(t) }
                GlassIcon(R.drawable.ic_info, size = 50.dp) { onOpen(t) }
            }
            Spacer(Modifier.height(16.dp))
            // TV: slim bars that fill up until the next film shows. Phone: dots.
            if (tvx) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items.indices.forEach { i ->
                    val fill = remember(index, paused) { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(index, paused) { if (i == index && !paused) fill.animateTo(1f, tween(7000, easing = androidx.compose.animation.core.LinearEasing)) }
                    Box(Modifier.width(30.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i < index) .75f else .22f))) {
                        if (i == index) Box(Modifier.fillMaxWidth(if (paused) 1f else fill.value).fillMaxHeight().background(Color.White))
                    }
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = if (wide) Arrangement.Start else Arrangement.Center) {
                items.indices.forEach { i ->
                    val w by animateDpAsState(if (i == index) 22.dp else 6.dp, label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).width(w).height(6.dp).clip(CircleShape)
                        .background(if (i == index) Brand else Brush.linearGradient(listOf(Hairline, Hairline))))
                }
            }
        }
    }
}

// ---------- States ----------

@Composable
fun RowSkeleton() {
    Column {
        Box(Modifier.padding(start = Gutter, top = 28.dp, bottom = 12.dp).width(160.dp).height(18.dp).shimmer(RoundedCornerShape(6.dp)))
        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { Box(Modifier.width(118.dp).aspectRatio(2f / 3f).shimmer()) }
        }
    }
}

@Composable
fun HeroSkeleton() {
    Box(Modifier.fillMaxWidth().height(560.dp).shimmer(RoundedCornerShape(0.dp)))
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
fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1E2C), Color(0xFF12141D))))
            .border(1.dp, Edge, RoundedCornerShape(22.dp)).padding(20.dp),
    ) { content() }
}

@Composable
fun Label(text: String) {
    Text(t(text).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Violet, style = jakarta())
}

@Suppress("unused")
@Composable
fun BoxScope.Scrim() = Box(Modifier.matchParentSize().background(Ink.copy(alpha = .6f)))
