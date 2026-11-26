package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Space under the floating dock so the last row is never hidden behind it. */
@Composable
fun dockSpace(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (LocalWide.current) 32.dp else 112.dp

/** Space under the top bar for screens without a hero. */
@Composable
fun barSpace(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 70.dp

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Composable
private fun <T> load(key: Any?, block: suspend () -> T): Pair<Load<T>, () -> Unit> {
    var attempt by remember { mutableIntStateOf(0) }
    @Suppress("UNCHECKED_CAST") val pre = LocalPreload.current[key] as T?
    var state by remember(key) { mutableStateOf<Load<T>>(if (pre != null) Load.Done(pre) else Load.Loading) }
    LaunchedEffect(key, attempt) {
        if (pre != null && attempt == 0) return@LaunchedEffect
        state = Load.Loading
        state = try { Load.Done(block()) } catch (e: Exception) { Load.Failed(e.message ?: "Something went wrong") }
    }
    return state to { attempt++ }
}

@Composable
private fun Failed(retry: () -> Unit) =
    Message("Can't load right now", "Check your internet connection and try again.", "Try again", retry)

// ---------- Home / Movies / TV ----------

@Composable
fun FeedScreen(kind: Kind?, store: Store, onOpen: (Title) -> Unit, onPlay: (Title) -> Unit, onDownloads: () -> Unit = {}) {
    var genre by remember(kind) { mutableStateOf<Genre?>(null) }
    val (state, retry) = load(kind to genre) {
        if (kind != null) Tmdb.browse(kind, genre) else {
            val rows = Tmdb.home()
            val watched = store.continueWatching.map { it.title.key }.toSet()
            val byw = Tmdb.becauseYouWatched(store.continueWatching.map { it.title } + store.myList, watched)
            // Top 10, New in cinemas, Trending, then "Because you watched", then the rest.
            rows.take(Tmdb.PERSONAL_AFTER) + byw + rows.drop(Tmdb.PERSONAL_AFTER)
        }
    }
    LaunchedEffect(state) { if (state !is Load.Loading) Boot.ready = true }
    // Get the titles you're most likely to play ready ahead of time.
    LaunchedEffect(Unit) { store.continueWatching.take(3).forEach { Prefetch.title(it.title, it.season, it.episode) } }
    val tv1 = rememberScrollState()
    ScrollList(tv1, PaddingValues(bottom = dockSpace())) {
        when (val s = state) {
            Load.Loading -> {
                item { HeroSkeleton() }
                items(3) { RowSkeleton() }
            }
            is Load.Failed -> {
                item { Spacer(Modifier.height(barSpace())) }
                item { Message("You're offline", "Connect to the internet to browse, or watch something you've downloaded.", "Open Downloads", onDownloads) }
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GhostButton("Try again", R.drawable.ic_refresh, onClick = retry) } }
            }
            is Load.Done -> {
                val rows = s.value
                item(key = "hero") {
                    Hero(
                        (rows.firstOrNull { it.name.startsWith("Trending") } ?: rows.firstOrNull())?.items?.filter { it.overview.isNotBlank() }?.take(6) ?: emptyList(),
                        saved = { store.inList(it) }, onPlay = onPlay, onOpen = onOpen, onToggle = { store.toggle(it) },
                    )
                }
                if (kind != null) item(key = "genres") {
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 18.dp)) {
                        item { Chip("All", genre == null) { genre = null } }
                        items(if (kind == Kind.MOVIE) Tmdb.movieGenres else Tmdb.tvGenres) { g -> Chip(g.name, genre == g) { genre = g } }
                    }
                }
                if (kind == null && store.continueWatching.isNotEmpty()) item(key = "continue") {
                    SectionTitle("Continue watching")
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(if (LocalWide.current) 18.dp else 12.dp)) {
                        items(store.continueWatching, key = { it.title.key }) { p ->
                            val pos = store.position(store.posKey(p.title, p.season, p.episode))
                            ContinueCard(p, pos?.let { (a, b) -> if (b > 0) a.toFloat() / b else null }, pos?.let { (a, b) -> (b - a).takeIf { b > 0 && it > 0 } }) { onPlay(p.title) }
                        }
                    }
                }
                rows.forEachIndexed { i, row ->
                    item(key = "row$i${row.name}") { Box(Modifier.enterUp(i)) { Column { PosterRow(row, onOpen) } } }
                }
            }
        }
    }
}

// ---------- Search ----------

@Composable
fun SearchScreen(onOpen: (Title) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(250); focus.requestFocus() }
    LaunchedEffect(query) { delay(450); submitted = query.trim() }
    val (state, retry) = load(submitted) { if (submitted.length < 2) emptyList() else Tmdb.search(submitted) }

    val tv2 = rememberLazyGridState()
    val tvBar = LocalTopBar.current
    LazyVerticalGrid(
        GridCells.Adaptive(if (LocalWide.current) 200.dp else 108.dp), Modifier.fillMaxSize().dpadScroll(tv2), state = tv2,
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = if (tvBar != null) 0.dp else barSpace(), bottom = dockSpace()),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (tvBar != null) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(Gutter)) { tvBar() } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Glass)
                    .border(1.dp, Edge, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Movies, shows, people…", color = Muted, fontSize = 16.sp, style = jakarta())
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Jakarta),
                        cursorBrush = SolidColor(Violet),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitted = query.trim(); keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) GlassIcon(R.drawable.ic_close, size = 30.dp) { query = "" }
            }
        }
        when (val s = state) {
            Load.Loading -> if (submitted.length >= 2) items(9) { Box(Modifier.aspectRatio(2f / 3f).shimmer()) }
            is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { Failed(retry) }
            is Load.Done -> when {
                submitted.length < 2 -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Message("Find something to watch", "Movies, shows and people.")
                }
                s.value.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Message("No results", "Try a different title.")
                }
                else -> items(s.value, key = { it.key }) { t -> PosterCard(t, 108.dp) { onOpen(t) } }
            }
        }
    }
}

// ---------- My List ----------

@Composable
fun MyListScreen(store: Store, onOpen: (Title) -> Unit, onDownloads: () -> Unit) {
    val tv3 = rememberLazyGridState()
    val tvBar = LocalTopBar.current
    LazyVerticalGrid(
        GridCells.Adaptive(if (LocalWide.current) 200.dp else 108.dp), Modifier.fillMaxSize().dpadScroll(tv3), state = tv3,
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = if (tvBar != null) 0.dp else barSpace(), bottom = dockSpace()),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (tvBar != null) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(Gutter)) { tvBar() } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row { GhostButton("Downloads", R.drawable.ic_download, onClick = onDownloads) }
        }
        if (store.myList.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Message("Your list is empty", "Add movies and shows to watch later.")
        } else items(store.myList, key = { it.key }) { t -> PosterCard(t, 108.dp) { onOpen(t) } }
    }
}

// ---------- Details ----------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(title: Title, store: Store, onOpen: (Title) -> Unit, onPerson: (Person) -> Unit, onTrailer: (String) -> Unit, onPlay: (Title, Int, Int) -> Unit) {
    val (state, retry) = load(title.key) { Tmdb.details(title) }
    val last = store.lastEpisode(title)
    LaunchedEffect(title.key) { Prefetch.title(title, last?.season ?: 1, last?.episode ?: 1) }
    var season by remember(title.key) { mutableIntStateOf(last?.season ?: 1) }

    val tv4 = rememberScrollState()
    // TV: open at the top with the full hero; Play is already on screen, so don't scroll to it.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides MinimalScroll) {
    ScrollList(tv4, PaddingValues(bottom = dockSpace())) {
        item(key = "top") {
            val d = (state as? Load.Done)?.value
            val t = d?.title ?: title
            val wide = LocalWide.current
            val artH = if (wide) (screenDp().height * .68f).coerceAtLeast(340.dp) else 500.dp
            Box(Modifier.fillMaxWidth().height(artH).clipToBounds()) {
                // Art without printed titles; while details load, show nothing rather than a poster with text.
                val image = when {
                    d == null -> null
                    wide && t.backdrop != null -> Tmdb.img(t.backdrop, "w1280")
                    d.cleanPoster != null -> Tmdb.img(d.cleanPoster, "w780")
                    else -> Tmdb.img(t.backdrop ?: t.poster, "w1280")
                }
                if (image != null) NetImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().kenBurns())
                if (wide) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink.copy(alpha = .85f), .6f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .4f), .35f to Color.Transparent, .7f to Ink.copy(alpha = .7f), 1f to Ink)))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = Gutter).enterUp()) {
                    if (d?.logo != null) NetImage(Tmdb.img(d.logo, "w500"), t.name, contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart, modifier = Modifier.width(260.dp).height(84.dp))
                    else Text(t.name, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = Color.White, style = jakarta())
                    Spacer(Modifier.height(10.dp))
                    Meta(t, d?.runtime?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" })
                }
            }
        }
        when (val s = state) {
            Load.Loading -> item { Box(Modifier.padding(Gutter).fillMaxWidth().height(52.dp).shimmer()) }
            is Load.Failed -> item { Failed(retry) }
            is Load.Done -> {
                val d = s.value
                val t = d.title
                item(key = "actions") {
                    val wide = LocalWide.current
                    Column(Modifier.padding(horizontal = Gutter).padding(top = 18.dp).then(if (wide) Modifier.widthIn(max = 720.dp) else Modifier)) {
                        val label = when {
                            t.kind == Kind.TV && last != null -> "Resume Episode ${last.episode}"
                            t.kind == Kind.TV -> "Play Episode 1"
                            else -> "Play"
                        }
                        val saved = store.inList(t)
                        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(label, R.drawable.ic_play_fill, Modifier.widthIn(min = 220.dp).tvFirstFocus(t.key)) { onPlay(t, last?.season ?: 1, last?.episode ?: 1) }
                            GhostButton(if (saved) "In My List" else "My List", if (saved) R.drawable.ic_check else R.drawable.ic_plus) { store.toggle(t) }
                            if (d.trailer != null) GhostButton("Trailer", R.drawable.ic_trailer) { onTrailer(d.trailer) }
                        } else {
                            PrimaryButton(label, R.drawable.ic_play_fill, Modifier.fillMaxWidth().tvFirstFocus(t.key)) {
                                onPlay(t, last?.season ?: 1, last?.episode ?: 1)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GhostButton(if (saved) "In My List" else "My List", if (saved) R.drawable.ic_check else R.drawable.ic_plus, Modifier.weight(1f)) { store.toggle(t) }
                                if (d.trailer != null) GhostButton("Trailer", R.drawable.ic_trailer, Modifier.weight(1f)) { onTrailer(d.trailer) }
                            }
                        }
                        if (d.genres.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Badge(d.genres.take(3).joinToString("  ·  ") { it.uppercase() })
                        }
                        if (d.tagline.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(d.tagline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = jakarta())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(t.overview, fontSize = 14.5.sp, lineHeight = 21.sp, color = Soft, style = jakarta())

                    }
                }
                if (d.cast.isNotEmpty()) item(key = "cast") {
                    SectionTitle("Cast")
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(d.cast, key = { it.id }) { p -> CastCard(p) { onPerson(p) } }
                    }
                }
                if (t.kind == Kind.TV && d.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        SectionTitle("Episodes")
                        if (d.seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(d.seasons) { se -> Chip("Season ${se.number}", se.number == season) { season = se.number } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item(key = "eps$season") { EpisodeList(t, season, last) { e -> onPlay(t, season, e) } }
                }
                if (d.similar.isNotEmpty()) item(key = "similar") { PosterRow(Row("More like this", d.similar), onOpen) }
            }
        }
    }
    }
}

@Composable
private fun EpisodeList(show: Title, season: Int, last: Progress?, onPlay: (Int) -> Unit) {
    val (state, retry) = load(show.id to season) { Tmdb.episodes(show.id, season) }
    val wide = LocalWide.current
    Column(Modifier.padding(horizontal = Gutter).then(if (wide) Modifier.widthIn(max = 900.dp) else Modifier)) {
        when (val s = state) {
            Load.Loading -> repeat(3) { Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(78.dp).shimmer()) }
            is Load.Failed -> Failed(retry)
            is Load.Done -> s.value.forEach { e ->
                val current = last?.season == season && last.episode == e.number
                var focused by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { focused = it.hasFocus }.clip(RoundedCornerShape(16.dp))
                    .background(if (focused) Color.White.copy(alpha = .1f) else Color.Transparent).tappableRow { onPlay(e.number) }.padding(vertical = 6.dp, horizontal = if (wide) 8.dp else 0.dp),
                    verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(if (wide) 220.dp else 132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Panel).border(if (current) 1.5.dp else 1.dp, if (current) Violet else Edge, RoundedCornerShape(12.dp))) {
                        NetImage(Tmdb.img(e.still ?: show.backdrop, "w300"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).size(32.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_play_fill), null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.number}. ${e.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (current) Color.White else Soft, style = jakarta(),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(listOfNotNull(e.runtime?.let { "${it}m" }, if (current) "Watching" else null).joinToString("  ·  ").ifBlank { "Episode ${e.number}" },
                            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (current) Violet else Muted, style = jakarta())
                        if (e.overview.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(e.overview, fontSize = if (wide) 14.sp else 12.5.sp, lineHeight = if (wide) 20.sp else 18.sp, color = Muted, style = jakarta(),
                                maxLines = if (wide) 3 else 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val version: String, val pct: Int) : UpdateState
    data class Ready(val version: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun SettingsScreen(version: String, update: UpdateState, blocked: Int, store: Store, onLanguage: () -> Unit, onDownloads: () -> Unit, onAlerts: (Boolean) -> Unit, onUpdate: () -> Unit, onClearCache: () -> Unit) {
    val tv5 = rememberScrollState()
    ScrollList(tv5, PaddingValues(start = Gutter, end = Gutter, top = barSpace(), bottom = dockSpace()), narrow = true) {
        item { ProfileCard(onLanguage) }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.logo_tile), null, Modifier.size(56.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("AnimeWave", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = jakarta())
                        Text("Version $version", fontSize = 14.sp, color = Muted, style = jakarta())
                    }
                }
                Spacer(Modifier.height(20.dp))
                Label("Updates")
                Spacer(Modifier.height(6.dp))
                Text(
                    when (update) {
                        UpdateState.Idle -> "Check for the newest version of AnimeWave."
                        UpdateState.Checking -> "Checking for updates…"
                        UpdateState.UpToDate -> "You're on the latest version."
                        is UpdateState.Downloading -> "Downloading version ${update.version}…"
                        is UpdateState.Ready -> "Version ${update.version} is ready to install."
                        is UpdateState.Failed -> update.message
                    },
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = jakarta(),
                )
                if (update is UpdateState.Downloading || update is UpdateState.Checking) {
                    Spacer(Modifier.height(14.dp))
                    val pct = (update as? UpdateState.Downloading)?.pct ?: -1
                    if (pct < 0) LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = Violet, trackColor = Glass)
                    else LinearProgressIndicator({ pct / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = Violet, trackColor = Glass)
                }
                Spacer(Modifier.height(18.dp))
                when (update) {
                    UpdateState.Checking, is UpdateState.Downloading -> {}
                    is UpdateState.Ready -> PrimaryButton("Install update", R.drawable.ic_check, onClick = onUpdate)
                    else -> PrimaryButton("Check for updates", R.drawable.ic_refresh, onClick = onUpdate)
                }
            }
        }
        item {
            Card {
                Label("Playback")
                Spacer(Modifier.height(6.dp))
                var autoplay by remember { mutableStateOf(store.autoplayNext) }
                ToggleRow("Autoplay next episode", "Plays the next episode after a short countdown.", autoplay) { autoplay = it; store.autoplayNext = it }
                var autoSkip by remember { mutableStateOf(store.autoSkip) }
                ToggleRow("Skip intros automatically", "Skips intros and recaps the moment they start.", autoSkip) { autoSkip = it; store.autoSkip = it }
                var alerts by remember { mutableStateOf(store.alerts) }
                ToggleRow("New episode alerts", "Know when a show in My List has a new episode.", alerts) { alerts = it; store.alerts = it; onAlerts(it) }
                Spacer(Modifier.height(6.dp))
                GhostButton("Downloads", R.drawable.ic_download, onClick = onDownloads)
            }
        }
        item {
            Card {
                Label("Ad blocking")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_shield), null, tint = Color(0xFF5EE6A8), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (blocked > 0) tf("On · {1} ad and tracker sites blocked", "%,d".format(blocked)) else "On",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = jakarta())
                }
                Spacer(Modifier.height(6.dp))
                Text("Pop-ups, redirects and banner ads are blocked while you watch.", fontSize = 13.sp, color = Muted, style = jakarta())
            }
        }
        item {
            Card {
                Label("Storage")
                Spacer(Modifier.height(6.dp))
                Text("Clear saved images and player data if something isn't loading.", fontSize = 13.sp, color = Muted, style = jakarta())
                Spacer(Modifier.height(14.dp))
                GhostButton("Clear cache", null, onClick = onClearCache)
            }
        }
    }
}


// ---------- People ----------

@Composable
fun Avatar(p: Person, size: Dp) {
    Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(Brush.linearGradient(listOf(Violet.copy(alpha = .5f), Pink.copy(alpha = .4f)))), contentAlignment = Alignment.Center) {
        Text(p.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), color = Color.White,
            fontSize = (size.value * .32f).sp, fontWeight = FontWeight.Bold, style = jakarta())
        if (p.photo != null) NetImage(Tmdb.img(p.photo, "w185"), p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun CastCard(p: Person, onClick: () -> Unit) {
    Column(Modifier.width(82.dp).tappable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(p, 72.dp)
        Spacer(Modifier.height(8.dp))
        Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Soft, style = jakarta(), maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (p.role.isNotBlank()) Text(p.role, fontSize = 11.sp, color = Muted, style = jakarta(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun PersonScreen(person: Person, onOpen: (Title) -> Unit) {
    val (state, retry) = load(person.id) { Tmdb.person(person.id) }
    var more by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<Kind?>(null) }
    val tv6 = rememberLazyGridState()
    val tvBar = LocalTopBar.current
    val wide = LocalWide.current
    val d = (state as? Load.Done)?.value
    LazyVerticalGrid(
        GridCells.Adaptive(if (wide) 170.dp else 104.dp), Modifier.fillMaxSize().dpadScroll(tv6), state = tv6,
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = if (tvBar != null) 0.dp else barSpace(), bottom = dockSpace()),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (tvBar != null) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(Gutter)) { tvBar() } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val head: @Composable () -> Unit = {
                Text(person.name, fontSize = if (wide) 34.sp else 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = jakarta())
                if (d != null && d.facts.isNotBlank()) Text(d.facts, fontSize = 13.sp, color = Muted, style = jakarta())
                if (d != null && d.bio.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(d.bio, fontSize = 14.sp, lineHeight = 20.sp, color = Soft, style = jakarta(), maxLines = if (more) 60 else if (wide) 6 else 4,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.tappableRow { more = !more })
                }
            }
            Column(Modifier.fillMaxWidth()) {
                if (wide) Row(verticalAlignment = Alignment.Top) {
                    Avatar(d?.person ?: person, 150.dp)
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) { head() }
                } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Avatar(d?.person ?: person, 120.dp)
                    Spacer(Modifier.height(14.dp))
                    head()
                }
                Spacer(Modifier.height(18.dp))
                if (d != null && d.known.isNotEmpty()) {
                    val movies = d.known.count { it.kind == Kind.MOVIE }
                    val shows = d.known.size - movies
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("All ${d.known.size}", filter == null) { filter = null }
                        if (movies > 0) Chip("Movies $movies", filter == Kind.MOVIE) { filter = Kind.MOVIE }
                        if (shows > 0) Chip("Shows $shows", filter == Kind.TV) { filter = Kind.TV }
                    }
                }
            }
        }
        when (val s = state) {
            Load.Loading -> items(6) { Box(Modifier.aspectRatio(2f / 3f).shimmer()) }
            is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { Failed(retry) }
            is Load.Done -> {
                val shown = s.value.known.filter { filter == null || it.kind == filter }
                if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Message("Nothing to show", "No titles found for this person.") }
                items(shown, key = { it.key }) { t -> CreditCard(t, s.value.roles[t.key].orEmpty()) { onOpen(t) } }
            }
        }
    }
}

/** A title on a person's page: poster, then name, year and the part they played. */
@Composable
fun CreditCard(t: Title, role: String, onClick: () -> Unit) {
    val wide = LocalWide.current
    Column(Modifier.fillMaxWidth()) {
        Box(if (wide) Modifier.tappableCard(RoundedCornerShape(12.dp), onClick) else Modifier.tappable(onClick)) { Poster(t, Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(8.dp))
        Text(t.name, fontSize = if (wide) 15.sp else 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = Soft, style = jakarta(),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(t.year, role.ifBlank { null }).joinToString("  ·  "), fontSize = 11.5.sp, color = Muted, style = jakarta(),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


/** iOS-style switch row. */
@Composable
fun ToggleRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).focusGlow(RoundedCornerShape(12.dp), grow = false).clickableNoRipple { onChange(!on) }
        .padding(vertical = 10.dp, horizontal = if (LocalWide.current) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = jakarta())
            Text(body, fontSize = 12.sp, color = Muted, style = jakarta())
        }
        Spacer(Modifier.width(12.dp))
        val x by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 2.dp, label = "toggle")
        Box(Modifier.width(51.dp).height(31.dp).clip(RoundedCornerShape(50)).background(if (on) Color(0xFF34C759) else Color.White.copy(alpha = .16f))) {
            Box(Modifier.padding(start = x, top = 2.dp).size(27.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White))
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
)

// ---------- Downloads ----------

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DownloadsScreen(onPlay: (DownloadInfo, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf(Downloads.all(context)) }
    LaunchedEffect(Unit) { while (true) { items = Downloads.all(context); delay(1000) } }
    val tv7 = rememberScrollState()
    ScrollList(tv7, PaddingValues(start = Gutter, end = Gutter, top = barSpace(), bottom = dockSpace()), narrow = true) {
        if (items.isEmpty()) item { Message("No downloads yet", "Save a movie or episode from the player to watch it offline.") }
        items(items, key = { it.request.id }) { d ->
            val info = DownloadInfo.from(d.request.data) ?: return@items
            val done = d.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
            val failed = d.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).tappableRow { if (done) onPlay(info, d.request.id) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Panel)) {
                    NetImage(Tmdb.img(info.title.backdrop ?: info.title.poster, "w300"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (done) Box(Modifier.align(Alignment.Center).size(32.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_play_fill), null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, style = jakarta(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val mb = d.bytesDownloaded / 1_048_576
                    Text(when {
                        done -> "Downloaded · $mb MB"
                        failed -> "Download failed"
                        else -> "Downloading ${d.percentDownloaded.toInt().coerceAtLeast(0)}% · $mb MB"
                    }, fontSize = 12.sp, color = if (failed) Pink else if (done) Color(0xFF5EE6A8) else Muted, style = jakarta())
                    if (!done && !failed) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator({ (d.percentDownloaded / 100f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)), color = Violet, trackColor = Glass)
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassIcon(R.drawable.ic_close, size = 34.dp) { Downloads.remove(context, d.request.id) }
            }
        }
    }
}

/**
 * A plain scrolling page that takes LazyColumn-style item {} calls. Every row exists at once,
 * so the TV remote can always find the next thing below and the page follows it.
 */
@Composable
private fun ScrollList(state: androidx.compose.foundation.ScrollState, padding: PaddingValues, narrow: Boolean = false, content: @Composable ListShim.() -> Unit) {
    val bar = LocalTopBar.current
    Column(Modifier.fillMaxSize().verticalScroll(state).dpadScroll(state)) {
        // On TV the top bar is part of the page (not floating over it), so Down from the tabs reaches the content.
        Box {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.then(if (narrow && LocalWide.current) Modifier.widthIn(max = 760.dp) else Modifier).padding(padding)) { ListShim.content() }
            }
            bar?.invoke()
        }
    }
}

private object ListShim {
    @Composable fun item(key: Any? = null, content: @Composable () -> Unit) { androidx.compose.runtime.key(key) { content() } }
    @Composable fun items(count: Int, content: @Composable (Int) -> Unit) { repeat(count) { content(it) } }
    @Composable fun <T> items(list: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) {
        list.forEach { androidx.compose.runtime.key(key?.invoke(it)) { content(it) } }
    }
}

/** Lets a full-width row ignore the grid's side padding. */
private fun Modifier.bleed(side: Dp) = layout { m, c ->
    val extra = (side * 2).roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}
