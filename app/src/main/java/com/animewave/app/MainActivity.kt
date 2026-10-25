package com.animewave.app

import android.app.ActivityManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.Coil
import coil.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val PickTypes = arrayOf(
    "video/*", "application/x-subrip", "text/vtt", "text/x-ssa", "application/octet-stream",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // icon on dark background the instant the app is tapped
        super.onCreate(savedInstanceState)
        val lowRam = getSystemService(ActivityManager::class.java).isLowRamDevice ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) // TV boxes: little RAM, slow GPUs
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .crossfade(200)
                .allowRgb565(lowRam) // half the memory per image; artwork has no transparency
                .respectCacheHeaders(false) // keep posters on disk instead of re-downloading
                .build()
        )
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
    val retry: () -> Unit = remember { { attempt++ } }
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
    return Loaded(value, failed, retry)
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

private fun openLink(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
        toast(context, "This link cannot be opened in AnimeWave.")
        return
    }
    try {
        context.startActivity(
            Intent(context, EmbeddedPlayerActivity::class.java)
                .putExtra(EmbeddedPlayerActivity.EXTRA_URL, url)
                .putExtra(EmbeddedPlayerActivity.EXTRA_TITLE, "ANIMEWAVE PLAYER")
        )
    } catch (e: ActivityNotFoundException) {
        toast(context, "AnimeWave could not open this source.")
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
fun Shell(
    tab: String, onTab: (String) -> Unit, tabFocus: FocusRequester, scroll: ScrollState,
    update: Boolean = false, content: @Composable ColumnScope.() -> Unit,
) {
    val wide = LocalWide.current
    Scaffold(
        containerColor = Ink, contentColor = Color.White,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (!wide) BottomTabs(tab, onTab) },
    ) { inner ->
        // The top bar lives inside the scrolling page (not floating over it) so the TV remote can move
        // Down from the tabs into the content. On Home, HomeContent overlays it on the hero itself.
        val bar: @Composable () -> Unit = { TopBar(tab, tabFocus, update = update, onTab = onTab) }
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(scroll).imePadding()) {
            if (tab == "Home") CompositionLocalProvider(LocalTopBar provides bar) { content() }
            else { bar(); content() }
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
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
    var showIntro by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val tabFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }

    val prefs = remember { context.getSharedPreferences("animewave", Context.MODE_PRIVATE) }
    var preferredSource by remember { mutableStateOf(prefs.getString("preferredSource", "listed") ?: "listed") }
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
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    fun checkForUpdate(quiet: Boolean) = scope.launch {
        update = UpdateState.Checking
        update = try {
            val info = Updates.latest()
            if (info.versionCode > Updates.installedCode(context)) UpdateState.Available(info) else UpdateState.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't check for updates. Check your connection and try again.")
        }
    }
    fun downloadUpdate(info: UpdateInfo) = scope.launch {
        update = UpdateState.Downloading(info, 0f)
        update = try {
            val apk = Updates.download(context) { p -> update = UpdateState.Downloading(info, p) }
            if (!Updates.install(context, apk)) toast(context, "Allow AnimeWave to install apps, then come back and tap Install update.")
            UpdateState.Ready(info, apk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateState.Failed("Download failed: ${e.message ?: "unknown error"}. Try again.")
        }
    }
    LaunchedEffect(Unit) { checkForUpdate(quiet = true) }
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
      Box(Modifier.fillMaxSize()) {
        val nowPlaying = playing
        val anime = selected
        when {
            nowPlaying != null -> PlayerScreen(nowPlaying.first, nowPlaying.second) { playing = null }
            anime != null -> {
                BackHandler { selected = null }
                LaunchedEffect(anime.id) { if (isTv) runCatching { detailFocus.requestFocus() } }
                val catalog = load(anime.id) {
                    EpisodeCatalogRepository(listOf(AniListListedEpisodeAdapter())).load(anime.title)
                }
                Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())) {
                    DetailContent(
                        anime, saved = anime.id in saved, watchFocus = detailFocus,
                        onBack = { selected = null }, onWatch = ::watch, onOpen = { openLink(context, it) },
                        selectedSourceId = preferredSource,
                        catalogState = catalog.value,
                        onSourceSelected = { sourceId ->
                            preferredSource = sourceId
                            prefs.edit().putString("preferredSource", sourceId).apply()
                        },
                        onPlayEpisode = { ep ->
                            val stream = OfficialPlaybackRegistry.find(anime.id, ep.title)
                            if (stream != null) {
                                val item = MediaItem.Builder()
                                    .setUri(stream.url)
                                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("${anime.title} — ${ep.title}").build())
                                    .build()
                                playing = listOf(item) to 0
                            } else {
                                // The card represents a real AniList-listed episode page. Open that
                                // exact page; never substitute the selected provider's homepage.
                                if (preferredSource != "listed") {
                                    val source = AnimeSourceRegistry.byId(preferredSource)
                                    toast(context, "Opening the listed episode link. ${source.displayName} episode search is not connected yet.")
                                }
                                openLink(context, ep.url)
                            }
                        },
                        onToggleSave = { toggleSaved(anime.id) }, onPlayOwn = { selected = null; tab = "Player" },
                    )
                    Spacer(Modifier.navigationBarsPadding().height(24.dp))
                }
            }
            else -> Shell(
                tab, { tab = it }, tabFocus, scroll,
                update = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready,
            ) {
                val pick: (Anime) -> Unit = { selected = it }
                when (tab) {
                    "Home" -> HomeContent(
                        home, saved, heroFocus, onPick = pick,
                        onWatch = { watch(it.watchLinks.firstOrNull()?.second) },
                        onToggleSave = { toggleSaved(it.id) },
                    )
                    "Discover" -> {
                        ScreenTitle("Discover", "The most popular anime in every genre")
                        GenreChips(genre) { genre = it }
                        AnimeGrid(load(genre) { AniList.genre(genre) }, onPick = pick)
                    }
                    "Search" -> {
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
                        ScreenTitle("My List", if (saved.isEmpty()) null else "${saved.size} saved")
                        AnimeGrid(
                            load(saved) { if (saved.isEmpty()) emptyList() else AniList.byIds(saved) },
                            empty = "Nothing saved yet. Open any anime and tap My List.", onPick = pick,
                        )
                    }
                    "Settings" -> {
                        ScreenTitle("Settings")
                        SettingsScreen(
                            version = remember { Updates.installedName(context) },
                            state = update,
                            onCheck = { checkForUpdate(quiet = false) },
                            onDownload = { downloadUpdate(it) },
                            onInstall = { apk ->
                                if (!Updates.install(context, apk)) toast(context, "Allow AnimeWave to install apps, then tap Install update again.")
                            },
                        )
                    }
                    else -> {
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
        // Title screen on top while the catalog loads underneath.
        AnimatedVisibility(showIntro, exit = fadeOut(tween(450))) {
            IntroScreen(ready = { home.value != null || home.failed }) { showIntro = false }
        }
      }
    }
}
