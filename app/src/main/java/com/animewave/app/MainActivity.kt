package com.animewave.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

private val Ink = Color(0xFF0A0B10)
private val Panel = Color(0xFF151722)
private val Violet = Color(0xFF9B6DFF)
private val Muted = Color(0xFFB7B9C7)

private val Tabs = listOf("Home", "Discover", "My List", "Player")
private val Genres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Mystery", "Horror",
)
private val PickTypes = arrayOf(
    "video/*", "application/x-subrip", "text/vtt", "text/x-ssa", "application/octet-stream",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { AnimeWaveApp() }
    }
}

private class Loaded<T>(val value: T?, val failed: Boolean, val retry: () -> Unit)

private fun <T, R> Loaded<T>.map(f: (T) -> R) = Loaded(value?.let(f), failed, retry)

/** Runs [block] whenever [keys] change; exposes the result, a failure flag and a retry. */
@Composable
private fun <T> load(vararg keys: Any?, block: suspend () -> T): Loaded<T> {
    var attempt by remember { mutableIntStateOf(0) }
    var value by remember(*keys) { mutableStateOf<T?>(null) }
    var failed by remember(*keys) { mutableStateOf(false) }
    LaunchedEffect(*keys, attempt) {
        failed = false
        try {
            value = block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    return Loaded(value, failed) { attempt++ }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        toast(context, "No app on this device can open that link.")
    }
}

@Composable
fun AnimeWaveApp() {
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    var tab by rememberSaveable { mutableStateOf("Home") }
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf(Genres.first()) }
    var selected by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<LocalEpisode<Uri>>>(emptyList()) }
    var playing by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var askLink by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val tabFocus = remember { FocusRequester() }

    val prefs = remember { context.getSharedPreferences("animewave", Context.MODE_PRIVATE) }
    var saved by remember {
        mutableStateOf(prefs.getStringSet("myList", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet())
    }
    fun toggleSaved(id: Int) {
        saved = if (id in saved) saved - id else saved + id
        prefs.edit().putStringSet("myList", saved.map(Int::toString).toSet()).apply()
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val picked = pairEpisodes(uris.map { LocalFile(it, displayName(context, it)) })
        if (picked.isNotEmpty()) episodes = picked
        else if (uris.isNotEmpty()) toast(context, "No video files in that selection.")
    }
    val home = load(Unit) { AniList.home() }

    BackHandler(enabled = tab != "Home" || query.isNotEmpty()) { query = ""; tab = "Home" }
    LaunchedEffect(tab, selected, playing) {
        // Give the remote a starting point; skipped on phones so it doesn't jump the scroll.
        if (isTv && selected == null && playing == null) runCatching { tabFocus.requestFocus() }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Violet,
            onBackground = Color.White, onSurface = Color.White,
        )
    ) {
        val nowPlaying = playing
        val anime = selected
        when {
            nowPlaying != null -> PlayerScreen(nowPlaying.first, nowPlaying.second) { playing = null }
            anime != null -> Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
                DetailScreen(
                    anime, saved = anime.id in saved, isTv = isTv,
                    onToggleSave = { toggleSaved(anime.id) },
                    onBack = { selected = null },
                    onPlayOwn = { selected = null; tab = "Player" },
                )
            }
            else -> Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().verticalScroll(scroll)) {
                Header(tab, tabFocus) { tab = it; query = "" }
                if (tab == "Home" || tab == "Discover") SearchField(query) { query = it }
                val pick: (Anime) -> Unit = { selected = it }
                when {
                    query.isNotBlank() -> AnimeRow(
                        "RESULTS FOR “${query.trim()}”",
                        load(query) { delay(500); AniList.search(query.trim()) }, // typing debounce
                        onPick = pick,
                    )
                    tab == "Home" -> {
                        home.value?.get("trending")?.firstOrNull()?.let { FeaturedHero(it) { pick(it) } }
                        listOf(
                            "trending" to "TRENDING NOW", "airing" to "AIRING THIS SEASON",
                            "action" to "ACTION & ADVENTURE", "popular" to "ALL-TIME POPULAR",
                        ).forEach { (key, title) -> AnimeRow(title, home.map { it[key].orEmpty() }, onPick = pick) }
                    }
                    tab == "Discover" -> {
                        GenreChips(genre) { genre = it }
                        AnimeRow(genre.uppercase(), load(genre) { AniList.genre(genre) }, onPick = pick)
                    }
                    tab == "My List" -> AnimeRow(
                        "YOUR WATCHLIST",
                        load(saved) { if (saved.isEmpty()) emptyList() else AniList.byIds(saved) },
                        empty = "Nothing saved yet. Open any anime and tap + My List.",
                        onPick = pick,
                    )
                    else -> PlayerHome(
                        episodes,
                        onPickFiles = {
                            try {
                                pickFiles.launch(PickTypes)
                            } catch (e: ActivityNotFoundException) {
                                toast(context, "This device has no file picker. Use Play a stream link instead.")
                            }
                        },
                        onLink = { askLink = true },
                        onPlay = { i -> playing = episodes.map { it.toMediaItem() } to i },
                    )
                }
                Text(
                    "Anime info from AniList.co • Watch opens official streaming services",
                    color = Muted, fontSize = 11.sp, modifier = Modifier.padding(22.dp),
                )
            }
        }
        if (askLink) LinkDialog(onDismiss = { askLink = false }) { url ->
            askLink = false
            playing = listOf(MediaItem.fromUri(url)) to 0
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(tab: String, tabFocus: FocusRequester, onTab: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Violet, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text("A", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text("ANIME", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("WAVE", fontSize = 22.sp, fontWeight = FontWeight.Light, color = Violet, letterSpacing = 2.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tabs.forEach { item -> Chip(item, item == tab, if (item == tab) tabFocus else null, Role.Tab) { onTab(item) } }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, focus: FocusRequester? = null, role: Role = Role.Button, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val lit = active || focused
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        color = if (lit) Color.White else Muted,
        fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .background(if (lit) Violet.copy(alpha = .25f) else Color.Transparent)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        placeholder = { Text("Search any anime…", color = Muted) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet, unfocusedBorderColor = Color(0xFF343646),
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        ),
    )
}

@Composable
private fun GenreChips(genre: String, onPick: (String) -> Unit) {
    LazyRow(
        Modifier.padding(top = 14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(Genres) { g -> Chip(g, g == genre) { onPick(g) } }
    }
}

@Composable
private fun FeaturedHero(anime: Anime, onOpen: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(220.dp)
            .clip(RoundedCornerShape(22.dp)).background(anime.accent?.let { Color(it) } ?: Panel)
    ) {
        AsyncImage(
            model = anime.banner ?: anime.cover, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink.copy(alpha = .92f), Color.Transparent))))
        Column(Modifier.align(Alignment.CenterStart).padding(22.dp).fillMaxWidth(.75f)) {
            Text("#1 TRENDING", fontSize = 12.sp, color = Violet, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(anime.title, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(anime.genres.take(3).joinToString(" • "), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("View details") }
        }
    }
}

@Composable
private fun AnimeRow(title: String, loaded: Loaded<List<Anime>>, empty: String = "Nothing found.", onPick: (Anime) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(12.dp))
        val list = loaded.value
        val note = Modifier.padding(horizontal = 22.dp)
        when {
            loaded.failed -> Row(note, verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't load. Check your connection.", color = Muted)
                TextButton(onClick = loaded.retry) { Text("Retry") }
            }
            list == null -> Text("Loading…", color = Muted, modifier = note)
            list.isEmpty() -> Text(empty, color = Muted, modifier = note)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(list.distinctBy { it.id }, key = { it.id }) { anime -> AnimeCard(anime) { onPick(anime) } }
            }
        }
    }
}

@Composable
private fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(132.dp)
            .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = anime.cover, contentDescription = anime.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                .background(anime.accent?.let { Color(it) } ?: Panel)
                .border(3.dp, if (focused) Color.White else Color.Transparent, shape),
        )
        Spacer(Modifier.height(8.dp))
        Text(anime.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(anime.year?.toString(), anime.genres.firstOrNull()).joinToString(" • "), fontSize = 11.sp, color = Muted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(
    anime: Anime, saved: Boolean, isTv: Boolean,
    onToggleSave: () -> Unit, onBack: () -> Unit, onPlayOwn: () -> Unit,
) {
    val context = LocalContext.current
    val watchFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(anime.id) { if (isTv) runCatching { watchFocus.requestFocus() } }
    val official = anime.watchLinks.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("‹  Back", color = Violet, fontWeight = FontWeight.Bold) }
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)).background(anime.accent?.let { Color(it) } ?: Panel)) {
            AsyncImage(model = anime.banner ?: anime.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .92f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(anime.genres.take(3).joinToString(" • ").uppercase(), color = Color.White.copy(alpha = .8f), letterSpacing = 2.sp, fontSize = 11.sp)
                Text(anime.title, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            listOfNotNull(
                anime.year?.toString(), anime.format?.replace('_', ' '),
                anime.episodes?.let { "$it episodes" }, anime.score?.let { "★ $it%" },
            ).joinToString("  •  "),
            color = Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(anime.synopsis, color = Color(0xFFE0E1E8), fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (official != null) openLink(context, official.second)
                    else toast(context, "No official stream listed for this title. You can play your own files from the Player tab.")
                },
                modifier = Modifier.focusRequester(watchFocus),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
            ) { Text(official?.let { "▶  Watch on ${it.first}" } ?: "▶  Watch") }
            OutlinedButton(onClick = onToggleSave) { Text(if (saved) "✓  In My List" else "+  My List") }
            OutlinedButton(onClick = onPlayOwn) { Text("Play my own file") }
        }
        if (anime.watchLinks.size > 1) {
            Spacer(Modifier.height(18.dp))
            Text("ALSO ON", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                anime.watchLinks.drop(1).forEach { (site, url) -> OutlinedButton(onClick = { openLink(context, url) }) { Text(site) } }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("EPISODES", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        if (anime.streamingEpisodes.isEmpty()) {
            Text("No episode links listed on AniList for this title yet.", color = Muted)
        } else {
            // Shown in AniList's order; each opens the episode on the official site.
            anime.streamingEpisodes.take(50).forEach { ep ->
                ListRow(ep.title, ep.site, ep.thumbnail) { openLink(context, ep.url) }
            }
            if (anime.streamingEpisodes.size > 50) {
                Text("+ ${anime.streamingEpisodes.size - 50} more on ${anime.streamingEpisodes.first().site}", color = Muted)
            }
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String?, thumbnail: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Text("▶", color = Violet, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerHome(episodes: List<LocalEpisode<Uri>>, onPickFiles: () -> Unit, onLink: () -> Unit, onPlay: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("PLAY YOUR OWN VIDEOS", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick one or more episodes from this device. Select .srt, .vtt or .ass files named like the video " +
                "(Ep 1.mkv + Ep 1.srt) and subtitles load automatically. Built-in subtitles show under the player's CC button.",
            color = Muted, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPickFiles, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Open videos") }
            OutlinedButton(onClick = onLink) { Text("Play a stream link") }
        }
        if (episodes.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("EPISODES (${episodes.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(8.dp))
            episodes.forEachIndexed { i, ep ->
                ListRow(ep.video.name, ep.subtitle?.let { "CC: ${it.name}" }) { onPlay(i) }
            }
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val clean = url.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play a stream link") },
        text = {
            Column {
                Text("Paste an HTTPS video or HLS (.m3u8) link you have permission to play.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
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
