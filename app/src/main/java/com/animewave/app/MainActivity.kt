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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
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

private data class PlayerLaunch(val items: List<MediaItem>, val startIndex: Int, val startPositionMs: Long = 0L, val requestHeaders: Map<String, String> = emptyMap(), val fallbacks: List<PlayerFallback> = emptyList())

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
        ),
        typography = AppTypography,
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
        // Pages run underneath the floating phone dock; the end spacer keeps the last row clear of it.
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()).verticalScroll(scroll).imePadding()) {
                if (tab == "Home") CompositionLocalProvider(LocalTopBar provides bar) { content() }
                else { bar(); content() }
                if (wide) Spacer(Modifier.navigationBarsPadding().height(32.dp))
                else Spacer(Modifier.height(inner.calculateBottomPadding() + 12.dp))
            }
            if (!wide) {
                // Past the hero on Home (or the header elsewhere) a solid bar takes over at the top.
                val density = LocalDensity.current
                val screenH = LocalConfiguration.current.screenHeightDp.dp
                val threshold = with(density) {
                    (if (tab == "Home") (screenH * 0.78f).coerceIn(520.dp, 700.dp) - 90.dp else 70.dp).toPx()
                }
                val showBar by remember(threshold) { derivedStateOf { scroll.value > threshold } }
                StickyBar(showBar, update, tab == "Settings", onTab)
            }
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
    var playing by remember { mutableStateOf<PlayerLaunch?>(null) }
    var playbackChoices by remember { mutableStateOf<List<ResolvedPlaybackCandidate>?>(null) }
    var askLink by remember { mutableStateOf(false) }
    var showIntro by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val tabFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }

    val prefs = remember { context.getSharedPreferences("animewave", Context.MODE_PRIVATE) }
    var preferredSource by remember { mutableStateOf(prefs.getString("preferredSource", "animepahe") ?: "animepahe") }
    var saved by remember {
        mutableStateOf(prefs.getStringSet("myList", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet())
    }
    var continueWatching by remember { mutableStateOf(PlaybackHistoryStore.list(context)) }
    var recentSearches by remember {
        mutableStateOf(prefs.getString("recentSearches", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    }
    fun rememberSearch(text: String) {
        val q = text.trim().take(60)
        if (q.isEmpty()) return
        recentSearches = (listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) }).take(8)
        prefs.edit().putString("recentSearches", recentSearches.joinToString("\n")).apply()
    }
    fun recordPlayback(uri: String, title: String, positionMs: Long, durationMs: Long, artwork: String?, key: String?) {
        PlaybackHistoryStore.record(context, uri, title, positionMs, durationMs, artwork, key)
        continueWatching = PlaybackHistoryStore.list(context)
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
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
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
    val providerDiscovery = load(selected?.id) {
        selected?.let { ProviderManager.discover(it) } ?: ProviderDiscoveryResult.empty()
    }
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
            nowPlaying != null -> PlayerScreen(
                items = nowPlaying.items,
                startIndex = nowPlaying.startIndex,
                startPositionMs = nowPlaying.startPositionMs,
                requestHeaders = nowPlaying.requestHeaders,
                fallbacks = nowPlaying.fallbacks,
                onExit = { playing = null },
                onProgress = ::recordPlayback,
            )
            anime != null -> {
                BackHandler { selected = null }
                LaunchedEffect(anime.id) { if (isTv) runCatching { detailFocus.requestFocus() } }
                Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())) {
                    DetailContent(
                        anime, saved = anime.id in saved, watchFocus = detailFocus,
                        onBack = { selected = null }, onWatch = ::watch, onOpen = { openLink(context, it) },
                        selectedSourceId = preferredSource,
                        onSourceSelected = { sourceId ->
                            preferredSource = sourceId
                            prefs.edit().putString("preferredSource", sourceId).apply()
                        },
                        onPlayEpisode = { ep ->
                            val approved = OfficialPlaybackRegistry.find(anime.id, ep.title)
                            if (approved != null) {
                                val item = MediaItem.Builder()
                                    .setUri(approved.url)
                                    .setMediaMetadata(mediaInfo(anime.title + " — " + ep.title, anime.coverXL, ep.url))
                                    .build()
                                playing = PlayerLaunch(listOf(item), 0)
                            } else {
                                val group = providerDiscovery.value?.groups?.firstOrNull { candidateGroup ->
                                    candidateGroup.episodes.any { it.pageUrl == ep.url }
                                }
                                scope.launch {
                                    val candidates = try {
                                        group?.let { PlaybackCandidateEngine.resolve(it) }.orEmpty()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                    when {
                                        candidates.size == 1 -> {
                                            val candidate = candidates.first()
                                            val resolved = candidate.stream
                                            val builder = MediaItem.Builder()
                                                .setUri(resolved.url)
                                                .setMediaMetadata(mediaInfo(anime.title + " — " + ep.title + " · " + resolved.providerName, anime.coverXL, ep.url))
                                                .setSubtitleConfigurations(SubtitleManager.configurations(resolved.subtitles))
                                            resolved.mimeType?.let { builder.setMimeType(it) }
                                            playing = PlayerLaunch(listOf(builder.build()), 0, requestHeaders = resolved.requestHeaders)
                                        }
                                        candidates.size > 1 -> playbackChoices = candidates
                                        preferredSource == "listed" -> openLink(context, ep.url)
                                        else -> {
                                            val source = AnimeSourceRegistry.byId(preferredSource)
                                            toast(context, "Searching " + source.displayName + " for " + anime.title + " — " + ep.title)
                                            openLink(context, AnimeSourceRegistry.searchUrl(preferredSource, anime.title, ep.title))
                                        }
                                    }
                                }
                            }
                        },
                        onToggleSave = { toggleSaved(anime.id) }, onPlayOwn = { selected = null; tab = "Player" },
                        providerDiscovery = providerDiscovery.value,
                        progress = continueWatching.mapNotNull { r -> r.key?.let { it to r.progress } }.toMap(),
                        discovering = providerDiscovery.value == null && !providerDiscovery.failed,
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
                        continueWatching = continueWatching,
                        onResume = { record ->
                            val item = MediaItem.Builder().setUri(record.uri)
                                .setMediaMetadata(mediaInfo(record.title, record.artwork, record.key))
                                .build()
                            playing = PlayerLaunch(listOf(item), 0, record.positionMs)
                        },
                        // Public-domain native playback test, shown as a Home card instead of a bare button.
                        onPlayClassic = {
                            val testUrl = "https://upload.wikimedia.org/wikipedia/commons/transcoded/4/a/Sanae_Yamamoto-_My_Big_Emergency_%281936%29.ogv/Sanae_Yamamoto-_My_Big_Emergency_%281936%29.ogv.360p.mp4"
                            val testItem = MediaItem.Builder()
                                .setUri(testUrl)
                                .setMimeType("video/mp4")
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle("My Big Emergency (1936) — Public Domain Anime Test")
                                        .setArtist("Sanae Yamamoto · Wikimedia Commons")
                                        .build()
                                )
                                .build()
                            playing = PlayerLaunch(listOf(testItem), 0)
                        },
                    )
                    "Discover" -> {
                        ScreenTitle("Discover", "The most popular anime in every genre")
                        GenreChips(genre) { genre = it }
                        val genreList = load(genre) { AniList.genre(genre) }
                        if (!genreList.failed) GenreShowcase(genre, genreList.value?.firstOrNull(), pick)
                        AnimeGrid(genreList.map { it.drop(1) }, onPick = pick)
                    }
                    "Search" -> {
                        ScreenTitle("Search")
                        SearchBox(query) { query = it }
                        if (query.isBlank()) {
                            SearchSuggestions(
                                recent = recentSearches,
                                trending = home.value?.get("trending").orEmpty().take(8),
                                onRecent = { query = it },
                                onClearRecent = { recentSearches = emptyList(); prefs.edit().remove("recentSearches").apply() },
                                onPick = pick,
                            )
                        } else {
                            AnimeGrid(
                                load(query) { delay(500); AniList.search(query.trim()) }, // typing debounce
                                empty = "No matches for “${query.trim()}”. Check the spelling or try the English or Japanese title.",
                                onPick = { rememberSearch(query); pick(it) },
                            )
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
                            onPlay = { i -> playing = PlayerLaunch(episodes.map { it.toMediaItem() }, i) },
                        )
                    }
                }
            }
        }
        if (askLink) LinkDialog(onDismiss = { askLink = false }) { url ->
            askLink = false
            playing = PlayerLaunch(listOf(MediaItem.fromUri(url)), 0)
        }
        // Title screen on top while the catalog loads underneath.
        AnimatedVisibility(showIntro, exit = fadeOut(tween(450))) {
            IntroScreen(ready = { home.value != null || home.failed }) { showIntro = false }
        }
        playbackChoices?.let { candidates ->
            PlaybackSourceDialog(
                candidates = candidates,
                onDismiss = { playbackChoices = null },
                onSelect = { candidate ->
                    playbackChoices = null
                    val stream = candidate.stream
                    val builder = MediaItem.Builder()
                        .setUri(stream.url)
                        .setMediaMetadata(mediaInfo((selected?.title ?: "Anime") + " — " + candidate.episode.title + " · " + stream.providerName, selected?.coverXL, candidate.episode.pageUrl))
                        .setSubtitleConfigurations(SubtitleManager.configurations(stream.subtitles))
                    stream.mimeType?.let { builder.setMimeType(it) }
                    val fallbackSources = candidates
                        .filterNot { it == candidate }
                        .map { alternate ->
                            val alternateStream = alternate.stream
                            val alternateItem = MediaItem.Builder()
                                .setUri(alternateStream.url)
                                .setMediaMetadata(mediaInfo((selected?.title ?: "Anime") + " — " + alternate.episode.title + " · " + alternateStream.providerName, selected?.coverXL, alternate.episode.pageUrl))
                                .setSubtitleConfigurations(SubtitleManager.configurations(alternateStream.subtitles))
                                .apply { alternateStream.mimeType?.let { setMimeType(it) } }
                                .build()
                            PlayerFallback(alternateItem, alternateStream.requestHeaders)
                        }
                    playing = PlayerLaunch(
                        listOf(builder.build()), 0,
                        requestHeaders = stream.requestHeaders,
                        fallbacks = fallbackSources,
                    )
                },
            )
        }
      }
    }
}

/** Player metadata: title plus the poster for Continue Watching and the episode page it belongs to. */
fun mediaInfo(title: String, artwork: String?, episodeKey: String?, artist: String? = null): androidx.media3.common.MediaMetadata =
    androidx.media3.common.MediaMetadata.Builder()
        .setTitle(title)
        .apply {
            artwork?.takeIf { it.startsWith("https://") }?.let { setArtworkUri(Uri.parse(it)) }
            artist?.let { setArtist(it) }
            episodeKey?.let { setExtras(android.os.Bundle().apply { putString(EPISODE_KEY, it) }) }
        }
        .build()

const val EPISODE_KEY = "animewave.episodeKey"
