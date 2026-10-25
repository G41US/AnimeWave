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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

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

/** Theme + text colour. Without LocalContentColor, plain Text renders black on the dark background. */
@Composable
fun AnimeWaveTheme(wide: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, secondary = Pink, background = Ink, surface = Panel,
            onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White,
        )
    ) {
        CompositionLocalProvider(LocalWide provides wide, LocalContentColor provides Color.White, content = content)
    }
}

/** Scrolling page with the floating top bar, plus bottom tabs on phones. */
@Composable
fun Shell(tab: String, onTab: (String) -> Unit, tabFocus: FocusRequester, scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    val wide = LocalWide.current
    Scaffold(
        containerColor = Ink, contentColor = Color.White,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (!wide) BottomTabs(tab, onTab) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).imePadding()) {
                content()
                Spacer(Modifier.navigationBarsPadding().height(32.dp))
            }
            TopBar(tab, tabFocus, scrolled = scroll.value > 150, onTab = onTab)
        }
    }
}

@Composable
fun AnimeWaveApp() {
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val wide = isTv || LocalConfiguration.current.screenWidthDp >= 840
    var tab by rememberSaveable { mutableStateOf("Home") }
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf(Genres.first()) }
    var selected by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<LocalEpisode<Uri>>>(emptyList()) }
    var playing by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var askLink by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val tabFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }

    val prefs = remember { context.getSharedPreferences("animewave", Context.MODE_PRIVATE) }
    var saved by remember {
        mutableStateOf(prefs.getStringSet("myList", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet())
    }
    fun toggleSaved(id: Int) {
        saved = if (id in saved) saved - id else saved + id
        prefs.edit().putStringSet("myList", saved.map(Int::toString).toSet()).apply()
    }
    fun watch(url: String?) {
        if (url != null) openLink(context, url)
        else toast(context, "No official stream listed for this title. You can play your own files from the Player tab.")
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val picked = pairEpisodes(uris.map { LocalFile(it, displayName(context, it)) })
        if (picked.isNotEmpty()) episodes = picked
        else if (uris.isNotEmpty()) toast(context, "No video files in that selection.")
    }
    val home = load(Unit) { AniList.home() }
    val homeReady = home.value != null

    BackHandler(enabled = tab != "Home") { tab = "Home" }
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    LaunchedEffect(tab, selected, playing, homeReady) {
        // Give the remote a starting point; skipped on phones so nothing gets a stray highlight.
        if (isTv && selected == null && playing == null) {
            runCatching { (if (tab == "Home" && homeReady) heroFocus else tabFocus).requestFocus() }
        }
    }

    AnimeWaveTheme(wide) {
        val nowPlaying = playing
        val anime = selected
        when {
            nowPlaying != null -> PlayerScreen(nowPlaying.first, nowPlaying.second) { playing = null }
            anime != null -> {
                BackHandler { selected = null }
                LaunchedEffect(anime.id) { if (isTv) runCatching { detailFocus.requestFocus() } }
                Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())) {
                    DetailContent(
                        anime, saved = anime.id in saved, watchFocus = detailFocus,
                        onBack = { selected = null }, onWatch = ::watch, onOpen = { openLink(context, it) },
                        onToggleSave = { toggleSaved(anime.id) }, onPlayOwn = { selected = null; tab = "Player" },
                    )
                    Spacer(Modifier.navigationBarsPadding().height(24.dp))
                }
            }
            else -> Shell(tab, { tab = it }, tabFocus, scroll) {
                val pick: (Anime) -> Unit = { selected = it }
                when (tab) {
                    "Home" -> HomeContent(
                        home, saved, heroFocus, onPick = pick,
                        onWatch = { watch(it.watchLinks.firstOrNull()?.second) },
                        onToggleSave = { toggleSaved(it.id) },
                    )
                    "Discover" -> {
                        TopSpace()
                        ScreenTitle("Discover", "The most popular anime in every genre")
                        GenreChips(genre) { genre = it }
                        AnimeGrid(load(genre) { AniList.genre(genre) }, onPick = pick)
                    }
                    "Search" -> {
                        TopSpace()
                        ScreenTitle("Search")
                        SearchBox(query) { query = it }
                        if (query.isBlank()) {
                            Text(
                                "Search every anime on AniList by title.", color = Muted, fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = if (wide) 48.dp else 20.dp, vertical = 12.dp),
                            )
                        } else {
                            AnimeGrid(load(query) { delay(500); AniList.search(query.trim()) }, onPick = pick) // typing debounce
                        }
                    }
                    "My List" -> {
                        TopSpace()
                        ScreenTitle("My List", if (saved.isEmpty()) null else "${saved.size} saved")
                        AnimeGrid(
                            load(saved) { if (saved.isEmpty()) emptyList() else AniList.byIds(saved) },
                            empty = "Nothing saved yet. Open any anime and tap My List.", onPick = pick,
                        )
                    }
                    else -> {
                        TopSpace()
                        ScreenTitle("Player", "Your own videos and stream links")
                        PlayerHome(
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
                }
            }
        }
        if (askLink) LinkDialog(onDismiss = { askLink = false }) { url ->
            askLink = false
            playing = listOf(MediaItem.fromUri(url)) to 0
        }
    }
}
