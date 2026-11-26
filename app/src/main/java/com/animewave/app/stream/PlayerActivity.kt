package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.media.AudioManager
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.core.content.res.ResourcesCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * Plays a title in Movio's own player from one of many sources. Movio's direct source asks the
 * stream service by TMDB id; any other source's page loads out of sight and the stream its player
 * requests is caught and played natively, so the source's player and its ads never show. If a
 * source can't be caught, Movio moves to the next one, and finally shows the source's page.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        fun start(context: Context, t: Title, season: Int, episode: Int, offline: String? = null) {
            Store.get(context).watched(t, season, episode)
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra("id", t.id).putExtra("kind", t.kind.name).putExtra("name", t.name)
                    .putExtra("s", season).putExtra("e", episode).putExtra("offline", offline)
                    .putExtra("art", t.backdrop ?: t.poster)
            )
        }
    }

    private enum class Phase { FINDING, NATIVE, WEB, FAILED }

    private data class Stream(val url: String, val headers: Map<String, String>, val hls: Boolean)

    private lateinit var webView: WebView
    private lateinit var playerView: PlayerView
    private lateinit var fullscreen: FrameLayout
    private lateinit var exo: ExoPlayer
    private var customView: View? = null
    private val main = Handler(Looper.getMainLooper())
    private val feelJs by lazy { assets.open("feel.js").bufferedReader().use { it.readText() } }
    private val sniffJs by lazy { assets.open("sniff.js").bufferedReader().use { it.readText() } }
    private val playerJs by lazy { assets.open("player.js").bufferedReader().use { it.readText() } }
    private val store by lazy { Store.get(this) }
    private val prefs by lazy { getSharedPreferences("movio_player", MODE_PRIVATE) }

    private var phase by mutableStateOf(Phase.FINDING)
    private var label by mutableStateOf("")
    private var fill by mutableStateOf(false)
    private var webBar by mutableStateOf(true)
    private var pip by mutableStateOf(false)
    private var sourcesOpen by mutableStateOf(false)
    private val tvDevice by lazy {
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            getSystemService(android.app.UiModeManager::class.java)?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    private var sourceIndex by mutableIntStateOf(0)
    private var status by mutableStateOf("Loading")
    private val failedSources = mutableSetOf<Int>()
    private var controlsVisible by mutableStateOf(true)
    private var episodesList by mutableStateOf<List<Episode>>(emptyList())
    private var episodeCount = 0
    private var seasonCount = 0
    private var aspect by mutableIntStateOf(0)
    private var subSizeState by mutableIntStateOf(1)
    private var subStyleState by mutableIntStateOf(1)
    private var subColorState by mutableIntStateOf(0)
    private var art: String? = null
    private var subOffsetState by mutableLongStateOf(0L)
    private var downloadLabelState by mutableStateOf<String?>(null)
    private var offlineId: String? = null
    private var resumedAt by mutableLongStateOf(0L)
    private var segments by mutableStateOf<List<Segment>>(emptyList())
    private var segmentsFor = ""

    private var id = 0
    private var kind = Kind.MOVIE
    private var name = ""
    private var season = 1
    private var episode = 1
    private var title: Title? = null
    /** Expected length from TMDB, to spot a source playing the wrong title. */
    @Volatile private var runtimeMs = 0L

    // Per-attempt state.
    private val candidates = mutableListOf<Stream>()
    private val rejected = mutableSetOf<String>()
    private var trying: Stream? = null
    private var attempt = 0
    private var auto = true
    private var autoTried = 0
    private var resolveJob: Job? = null
    private var subtitleTracks: List<Subtitle> = emptyList()
    private var subtitleJob: Job? = null
    private val pageSubs = linkedSetOf<String>()
    @Volatile private var pageHost = ""

    private val source get() = Sources.all[sourceIndex]

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        I18n.init(this)
        super.onCreate(savedInstanceState)
        AdBlock.warm(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        immersive()

        id = intent.getIntExtra("id", 0)
        kind = Kind.valueOf(intent.getStringExtra("kind") ?: "MOVIE")
        name = intent.getStringExtra("name").orEmpty()
        season = intent.getIntExtra("s", 1)
        episode = intent.getIntExtra("e", 1)
        title = store.continueWatching.firstOrNull { it.title.id == id && it.title.kind == kind }?.title
        updateLabel()
        offlineId = intent.getStringExtra("offline")
        subSizeState = store.subSize; subStyleState = store.subStyle; subColorState = store.subColor; art = intent.getStringExtra("art")
        if (kind == Kind.TV) lifecycleScope.launch { loadEpisodeInfo() }
        lifecycleScope.launch {
            runtimeMs = try {
                val t = title ?: Title(id, kind, name, "", null, null, null, 0.0)
                if (kind == Kind.MOVIE) (Tmdb.details(t).runtime ?: 0) * 60_000L
                else (Tmdb.episodes(id, season).firstOrNull { it.number == episode }?.runtime ?: 0) * 60_000L
            } catch (_: Exception) { 0L }
        }

        // Our audio tap (for lining up subtitles) goes first in the audio chain.
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(androidx.media3.exoplayer.audio.TeeAudioProcessor(speech)))
                    .build()
        }.setEnableDecoderFallback(true)
        // Start as soon as ~1 s is buffered instead of 2.5 s.
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_000).build()
        exo = ExoPlayer.Builder(this, renderers).setLoadControl(load).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) onReady() }
                override fun onPlayerError(error: PlaybackException) { onStreamFailed() }
                override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) speech.seeked(new.positionMs)
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) learnIntro(old.positionMs, new.positionMs)
                }
                override fun onAudioSessionIdChanged(audioSessionId: Int) { applyNight() }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { pickOwnSubtitle(tracks) }
            })
        }

        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        webView = WebView(this)
        playerView = PlayerView(this).apply {
            useController = false
            player = exo
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            // Bars around the picture are always black, never whatever sits behind the player.
            setBackgroundColor(android.graphics.Color.BLACK)
            setKeepContentOnPlayerReset(true)
            visibility = View.GONE
        }
        // Registered after PlayerView's own listener, so this filtered set is what shows:
        // drops lines that are ads baked into subtitle files ("Visit xyz.ru to watch…").
        exo.addListener(object : Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                // Movio draws external subtitles itself (so their timing can be fixed live); stream ones pass through.
                if (ownCues != null) { shownCues = null; renderOwnSubs() }
                else playerView.subtitleView?.setCues(cueGroup.cues.filterNot { c -> c.text?.let { SubAd.containsMatchIn(it) } == true })
            }
        })
        lifecycleScope.launch { while (true) { kotlinx.coroutines.delay(100); speech.clock = exo.currentPosition; renderOwnSubs() } }
        nightOn = prefs.getBoolean("night", false); applyNight()
        // A stream that stops loading without an error: after 20 s stuck, recover like a failure.
        lifecycleScope.launch {
            var stuck = 0; var smooth = 0
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val waiting = phase == Phase.NATIVE && exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING
                stuck = if (waiting) stuck + 1 else 0
                smooth = if (exo.isPlaying) smooth + 1 else 0
                if (smooth >= 60) reconnects = 0
                if (stuck >= 20) { stuck = 0; onStreamFailed() }
            }
        }
        lifecycleScope.launch {
            var rounds = 0
            while (true) {
                // Often at first (quick fix), then once a minute to catch slow drift.
                kotlinx.coroutines.delay(if (rounds < 20) 15_000 else 60_000)
                if (phase != Phase.NATIVE || !exo.isPlaying) continue
                rounds++
                syncSubtitles()
            }
        }
        applySubtitleStyle()
        fullscreen =FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(android.graphics.Color.BLACK) }
        val overlay = ComposeView(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        root.addView(fullscreen, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        overlay.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            val density = if (tvDevice) androidx.compose.ui.unit.Density(base.density * 1.15f, base.fontScale) else base
            androidx.compose.runtime.CompositionLocalProvider(LocalTv provides tvDevice, androidx.compose.ui.platform.LocalDensity provides density) { Overlay() }
        }

        setupWebView()
        val off = offlineId
        if (off != null) playOffline(off) else {
            val t = title
            val perTitle = t?.let { store.sourceFor(it) }
            // Per-title pick, then the one you chose, then whichever source has been fastest lately.
            // Shows: your pick for this show, else Club first. Films: your pick, your default, else the fastest.
            // Shows: your pick for this anime, else Wave first. Look up the other catalogues' ids first.
            lifecycleScope.launch {
                runCatching { AnimeMap.get(id) }
                start(Sources.index(perTitle ?: order().firstOrNull()?.let { Sources.all[it].name }), automatic = true)
            }
        }
        lifecycleScope.launch { while (true) { refreshDownloadLabel(); kotlinx.coroutines.delay(2000) } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    sourcesOpen -> sourcesOpen = false
                    customView != null -> webView.webChromeClient?.onHideCustomView()
                    else -> finish()
                }
            }
        })
    }

    // ---------- UI ----------

    @Composable
    private fun Overlay() {
        if (pip) return
        Box(Modifier.fillMaxSize()) {
            when (phase) {
                Phase.NATIVE -> {
                    PlayerControls(exo, host, controlsVisible) { controlsVisible = it; placeSubtitles() }
                    if (resumedAt > 0) Row(
                        Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(start = 24.dp, bottom = if (controlsVisible) 112.dp else 28.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xF00E1017)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Resumed at ${clock(resumedAt)}", color = Soft, fontSize = 13.sp, style = jakarta())
                        Spacer(Modifier.width(10.dp))
                        Text("Start over", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = jakarta(),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).playerGlow(RoundedCornerShape(8.dp)).clickable { exo.seekTo(0); resumedAt = 0L; store.savePosition(posKey(), 0, exo.duration.coerceAtLeast(1)) }
                                .padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                Phase.WEB -> AnimatedVisibility(webBar, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                            .safeDrawingPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIcon(R.drawable.ic_back, size = 42.dp) { finish() }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = jakarta(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${source.name} · web player", color = Muted, fontSize = 12.sp, style = jakarta())
                        }
                        if (kind == Kind.TV) { GhostButton("Next", R.drawable.ic_next) { nextEpisode() }; Spacer(Modifier.width(10.dp)) }
                        GhostButton("Sources", R.drawable.ic_servers) { sourcesOpen = true }
                    }
                }
                Phase.FINDING, Phase.FAILED -> Box(Modifier.fillMaxSize().background(Ink).clipToBounds()) {
                    if (art != null) NetImage(Tmdb.img(art, "w1280"), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().kenBurns())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink.copy(alpha = .55f), .5f to Ink.copy(alpha = .7f), 1f to Ink)))
                Column(
                    Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    if (phase == Phase.FAILED) {
                        Message("Can't play right now", "None of the sources could play this. Check your connection or pick a source.", "Try again") {
                            failedSources.clear(); autoTried = 0; start(Sources.fallbackOrder.first(), automatic = true)
                        }
                    } else {
                        Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, style = jakarta(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 40.dp))
                        Spacer(Modifier.height(18.dp))
                        LoadingBar()
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Soft, fontSize = 13.sp, style = jakarta())
                    }
                }
                }
            }
            if (phase == Phase.FINDING || phase == Phase.FAILED) {
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(R.drawable.ic_back, size = 42.dp) { finish() }
                    Spacer(Modifier.weight(1f))
                    GlassIcon(R.drawable.ic_servers, size = 42.dp) { sourcesOpen = true }
                }
            }
            AnimatedVisibility(
                sourcesOpen, modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
            ) { SourcesPanel() }
        }
    }

    @Composable
    private fun SourcesPanel() {
        Column(Modifier.fillMaxHeight().width(300.dp).background(Color(0xF2101219)).safeDrawingPadding().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Sources")
                Spacer(Modifier.weight(1f))
                GlassIcon(R.drawable.ic_close, size = 34.dp) { sourcesOpen = false }
            }
            Spacer(Modifier.height(4.dp))
            Text("If one doesn't play, try another.", color = Muted, fontSize = 12.sp, style = jakarta())
            Spacer(Modifier.height(10.dp))
            val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (sourceIndex - 2).coerceAtLeast(0))
            val pick = remember { androidx.compose.ui.focus.FocusRequester() }
            // Remote: start on the source that's playing now.
            LaunchedEffect(Unit) { if (tvDevice) { kotlinx.coroutines.delay(250); runCatching { pick.requestFocus() } } }
            LazyColumn(state = list) {
                itemsIndexed(Sources.all) { i, s ->
                    val selected = i == sourceIndex
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .then(if (selected) androidx.compose.ui.Modifier.focusRequester(pick) else androidx.compose.ui.Modifier)
                            .playerGlow(RoundedCornerShape(12.dp), grow = false).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Violet.copy(alpha = .28f) else Color.Transparent)
                            .border(1.dp, if (selected) Violet.copy(alpha = .6f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { sourcesOpen = false; start(i, automatic = false) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, style = jakarta())
                            when {
                                i in failedSources -> Text("Didn't play this title", color = Pink.copy(alpha = .8f), fontSize = 11.sp, style = jakarta())
                                s.web -> Text("Web player", color = Muted, fontSize = 11.sp, style = jakarta())
                                else -> Text("AnimeWave player", color = Color(0xFF5EE6A8), fontSize = 11.sp, style = jakarta())
                            }
                        }
                        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    private fun updateLabel() { label = if (kind == Kind.TV) "$name · Episode $episode" else name }
    private fun posKey() = title?.let { store.posKey(it, season, episode) } ?: "${kind.api}:$id${if (kind == Kind.TV) ":$season:$episode" else ""}"

    // ---------- Sources ----------

    /** Play from source [index]. Automatic starts move on to the next source by themselves. */
    private fun start(index: Int, automatic: Boolean, save: Boolean = true, useCache: Boolean = true) {
        cacheAllowed = useCache
        // Only save when staying on the same episode (e.g. switching source); episode changes save before moving.
        if (save && phase == Phase.NATIVE) savePosition()
        resumedAt = 0L
        auto = automatic
        if (!automatic) { autoTried = 0; prefs.edit().putString("source2", Sources.all[index].name).apply() }
        sourceIndex = index
        startedAt = SystemClock.uptimeMillis()
        attempt++
        val segKey = "$season:$episode"
        if (segKey != segmentsFor) {
            segmentsFor = segKey; segments = emptyList()
            lifecycleScope.launch { val found = Segments.find(kind, id, season, episode); if (segmentsFor == segKey) segments = withLearned(found) }
        }
        val token = attempt
        candidates.clear(); rejected.clear(); trying = null; subtitleTracks = emptyList(); pageSubs.clear()
        resolveJob?.cancel()
        subtitleJob?.cancel()
        if (!source.direct) subtitleJob = lifecycleScope.launch {
            val found = Subtitles.find(kind, id, season, episode)
            if (token != attempt || found.isEmpty()) return@launch
            val had = subtitleTracks.isNotEmpty()
            subtitleTracks = found
            // Stream already playing without them: reload in place so the subtitles show up.
            if (phase == Phase.NATIVE && !had) trying?.let { s -> val pos = exo.currentPosition; play(s); exo.seekTo(pos) }
        }
        exo.stop(); exo.clearMediaItems()
        playerView.visibility = View.GONE
        phase = Phase.FINDING
        status = if (autoTried > 0) "Finding the best stream" else "Loading"
        webView.stopLoading()
        // Played this before on this source? Its stream link usually still works: start straight away.
        if (offlineId == null && cacheAllowed) savedLink(index)?.let { (st, subs) ->
            fromCache = true
            if (subtitleTracks.isEmpty()) subtitleTracks = subs
            status = "Resuming"
            trying = st
            play(st)
            return
        }
        fromCache = false
        if (source.direct) {
            pageHost = ""
            webView.loadUrl("about:blank")
            resolveJob = lifecycleScope.launch {
                try {
                    val p = Source.resolve(kind, id, season, episode)
                    if (token != attempt) return@launch
                    subtitleTracks = p.subtitles
                    trying = Stream(p.url, p.headers, hls = true)
                    play(trying!!)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (token == attempt) sourceFailed()
                }
            }
            return
        }

        val url = source.url(kind, id, season, episode)
        // This source doesn't carry the title (it isn't in its catalogue): move on.
        if (url.isEmpty()) { auto = true; sourceFailed(); return }
        pageHost = android.net.Uri.parse(url).host.orEmpty()
        webView.onResume()
        webView.visibility = View.VISIBLE
        webView.loadUrl(url, mapOf("Referer" to "https://braflix.at/"))
        if (source.web) { showWeb(reload = false); return }
        // Many players wait for a tap on play; tap the middle for the viewer.
        listOf(2_500L, 5_000L, 8_000L).forEach { d -> main.postDelayed({ if (token == attempt && phase == Phase.FINDING && trying == null) tapCentre() }, d) }
        main.postDelayed({ if (token == attempt && phase == Phase.FINDING && trying == null) sourceFailed() }, 12_000)
    }

    /** This source couldn't play: try the next one, or show its page when it was picked by hand. */
    private fun sourceFailed() {
        failedSources.add(sourceIndex)
        score(sourceIndex, 30_000)
        if (auto && autoTried < 5) {
            autoTried++
            val next = order().firstOrNull { it !in failedSources }
            if (next != null) { start(next, automatic = true); return }
        }
        if (source.direct) { phase = Phase.FAILED; return }
        showWeb(reload = false)
    }

    private var startedAt = 0L

    // ---------- Skip intro for shows nobody has timed ----------

    /** You skipped ahead early in an episode: remember it as this show's intro for the next episodes. */
    private fun learnIntro(from: Long, to: Long) {
        if (kind != Kind.TV || phase != Phase.NATIVE) return
        if (segments.any { it.type == "intro" }) return
        val jump = to - from
        if (from > 6 * 60_000 || jump !in 20_000..150_000) return
        prefs.edit().putString("intro:$id", "${(from - 3_000).coerceAtLeast(0)},$to").apply()
    }

    private fun withLearned(found: List<Segment>): List<Segment> {
        if (kind != Kind.TV || found.any { it.type == "intro" }) return found
        val (a, b) = prefs.getString("intro:$id", null)?.split(",")?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 2 } ?: return found
        return (found + Segment("learned", a, b)).sortedBy { it.startMs }
    }

    // ---------- Night mode: squeeze loud bits, lift quiet dialogue ----------

    private var nightOn by mutableStateOf(false)
    private var night: android.media.audiofx.DynamicsProcessing? = null

    private fun applyNight() {
        try { night?.release() } catch (_: Exception) {}
        night = null
        if (!nightOn || android.os.Build.VERSION.SDK_INT < 28) return
        val session = exo.audioSessionId.takeIf { it != C.AUDIO_SESSION_ID_UNSET && it != 0 } ?: return
        try {
            val cfg = android.media.audiofx.DynamicsProcessing.Config.Builder(
                android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, true, 1, false, 0, true,
            ).build()
            val dp = android.media.audiofx.DynamicsProcessing(0, session, cfg)
            for (ch in 0 until 2) {
                val band = dp.getMbcBandByChannelIndex(ch, 0)
                band.isEnabled = true
                band.cutoffFrequency = 20_000f
                band.attackTime = 5f; band.releaseTime = 120f
                band.ratio = 4f; band.threshold = -30f; band.kneeWidth = 6f
                band.noiseGateThreshold = -90f; band.expanderRatio = 1f
                band.preGain = 0f; band.postGain = 12f
                dp.setMbcBandByChannelIndex(ch, 0, band)
                val lim = dp.getLimiterByChannelIndex(ch)
                lim.isEnabled = true; lim.threshold = -3f; lim.ratio = 10f; lim.attackTime = 1f; lim.releaseTime = 60f; lim.postGain = 0f
                dp.setLimiterByChannelIndex(ch, lim)
            }
            dp.setEnabled(true)
            night = dp
        } catch (_: Exception) {
            Toast.makeText(this, t("Night mode isn't supported on this device"), Toast.LENGTH_SHORT).show()
            nightOn = false
        }
    }
    private var fromCache = false
    private var cacheAllowed = true
    private var reconnects = 0

    /** Stream links that worked, per episode/film and source, kept for 6 hours. */
    private val links by lazy { getSharedPreferences("movio_links", MODE_PRIVATE) }
    private fun linkKey(i: Int = sourceIndex) = "${posKey()}|${Sources.all[i].name}"

    private fun saveLink(st: Stream) {
        val o = org.json.JSONObject().put("url", st.url).put("hls", st.hls).put("t", System.currentTimeMillis())
            .put("h", org.json.JSONObject(st.headers as Map<*, *>))
            .put("subs", org.json.JSONArray().apply { subtitleTracks.forEach { put(org.json.JSONObject().put("l", it.label).put("g", it.lang).put("u", it.url).put("s", it.srt)) } })
        links.edit().putString(linkKey(), o.toString()).apply()
    }

    private fun dropLink() { links.edit().remove(linkKey()).apply() }

    private fun savedLink(i: Int): Pair<Stream, List<Subtitle>>? = try {
        links.getString(linkKey(i), null)?.let { org.json.JSONObject(it) }
            ?.takeIf { System.currentTimeMillis() - it.getLong("t") < 6 * 3_600_000L }
            ?.let { o ->
                val h = o.getJSONObject("h").let { j -> j.keys().asSequence().associateWith { j.getString(it) } }
                val subs = o.optJSONArray("subs")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { Subtitle(it.getString("l"), it.getString("g"), it.getString("u"), it.getBoolean("s")) } } ?: emptyList()
                Stream(o.getString("url"), h, o.getBoolean("hls")) to subs
            }
    } catch (_: Exception) { null }

    /** Average time each source takes to start (failures count as slow), so the quickest are tried first. */
    private fun score(i: Int, ms: Long) {
        val k = "speed:${Sources.all[i].name}"
        val old = prefs.getLong(k, -1L)
        prefs.edit().putLong(k, if (old < 0) ms else (old * 2 + ms) / 3).apply()
    }

    private fun fastest(): List<Int> = Sources.fallbackOrder.sortedBy { prefs.getLong("speed:${Sources.all[it].name}", 12_000L) }

    /** Anime goes Wave (made for anime) → Club → Nero → Peach → Flixify, then the rest by speed. */
    private fun order(): List<Int> {
        val fixed = listOf("Wave", "Club", "Nero", "Peach", "Flixify").map { Sources.index(it) }.distinct()
        return fixed + fastest().filter { it !in fixed }
    }

    private fun tapCentre() {
        val x = webView.width / 2f; val y = webView.height / 2f
        val t = SystemClock.uptimeMillis()
        webView.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        webView.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun showWeb(reload: Boolean) {
        if (phase == Phase.NATIVE) savePosition()
        exo.pause()
        playerView.visibility = View.GONE
        if (reload) {
            if (source.direct) { start(Sources.fallbackOrder.first(), automatic = false); return }
            attempt++
            candidates.clear(); trying = null
            exo.stop()
            webView.onResume()
            webView.loadUrl(source.url(kind, id, season, episode), mapOf("Referer" to "https://braflix.at/"))
        }
        phase = Phase.WEB
        webView.visibility = View.VISIBLE
        webBar = true
        main.postDelayed({ if (phase == Phase.WEB) webBar = false }, 5000)
    }

    private fun onCandidate(s: Stream) {
        if (s.url in rejected || candidates.any { it.url == s.url }) return
        candidates.add(s)
        if (trying == null && phase != Phase.NATIVE) tryNext()
    }

    private fun tryNext() {
        val next = candidates.sortedByDescending { it.hls }.firstOrNull { it.url !in rejected } ?: run { trying = null; return }
        trying = next
        status = "Almost ready"
        val token = attempt
        lifecycleScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(1_200) { subtitleJob?.join() }
            if (token == attempt && trying == next) play(next)
        }
    }

    private fun play(s: Stream) {
        val cookie = CookieManager.getInstance().getCookie(s.url)
        val headers = s.headers.filterKeys { k -> k.lowercase() in setOf("referer", "origin", "user-agent", "accept-language") }.toMutableMap()
        if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie
        val ua = headers.remove("User-Agent") ?: headers.remove("user-agent") ?: webView.settings.userAgentString
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        val pageTracks = pageSubs.filter { u -> subtitleTracks.none { it.url == u } }.take(4).mapIndexed { i, u -> Subtitle("Source ${i + 1}", "und", u) }
        val subs = (subtitleTracks + pageTracks).map { t ->
            val local = shiftedSubs[t.url]
            MediaItem.SubtitleConfiguration.Builder(if (local != null) android.net.Uri.fromFile(java.io.File(local)) else android.net.Uri.parse(t.url))
                .setId(t.url)
                .setMimeType(if (t.srt) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage(t.lang.takeIf { it != "und" }).setLabel(t.label)
                .build()
        }
        val item = MediaItem.Builder().setUri(s.url)
            .setMimeType(if (s.hls) MimeTypes.APPLICATION_M3U8 else null)
            .setSubtitleConfigurations(subs)
            .build()
        if (s.url != speechUrl) { speechUrl = s.url; speech.reset(); fitFor.clear() }
        speech.seeked(0)
        exo.setMediaSource(DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this, http)).createMediaSource(item))
        exo.prepare()
        lastHeaders = headers
        previewSource = { DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this, http)).createMediaSource(MediaItem.Builder().setUri(s.url).setMimeType(if (s.hls) MimeTypes.APPLICATION_M3U8 else null).build()) }
        releasePreview()
    }

    private val shiftedSubs = mutableMapOf<String, String>()
    private var lastHeaders: Map<String, String> = emptyMap()

    /** Short clips from a page are ads; anything real takes the screen and the page is shut down. */
    private fun onReady() {
        val s = trying ?: return
        if (phase == Phase.NATIVE) return
        val d = exo.duration
        if (offlineId != null) { phase = Phase.NATIVE; playerView.visibility = View.VISIBLE; store.position(posKey())?.let { (p, _) -> if (p > 30_000) exo.seekTo(p) }; exo.play(); return }
        val wrong = (!source.direct && d != C.TIME_UNSET && d in 1 until 150_000) ||
            (runtimeMs > 0 && d != C.TIME_UNSET && d > 0 && kotlin.math.abs(d - runtimeMs).toDouble() / runtimeMs > 0.3)
        if (wrong && fromCache) { fromCache = false; exo.stop(); dropLink(); start(sourceIndex, auto, save = false, useCache = false); return }
        if (!source.direct && d != C.TIME_UNSET && d in 1 until 150_000) {
            rejected.add(s.url); trying = null; exo.stop(); tryNext(); return
        }
        val rt = runtimeMs
        if (rt > 0 && d != C.TIME_UNSET && d > 0 && kotlin.math.abs(d - rt).toDouble() / rt > 0.3) {
            // Far from the real runtime: this source is playing a different title.
            rejected.add(s.url); trying = null; exo.stop()
            if (candidates.any { it.url !in rejected }) tryNext() else sourceFailed()
            return
        }
        phase = Phase.NATIVE
        score(sourceIndex, SystemClock.uptimeMillis() - startedAt)
        playerView.visibility = View.VISIBLE
        store.position(posKey())?.let { (p, dur) -> if (p > 30_000 && (dur <= 0 || p < dur - 60_000)) { exo.seekTo(p); resumedAt = p; main.postDelayed({ resumedAt = 0L }, 7000) } }
        // Subtitles in the language picked at setup (or none).
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(I18n.subLang.takeIf { it != "off" } ?: "en")
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, I18n.subLang == "off").build()
        exo.play()
        failedSources.remove(sourceIndex)
        // A show only remembers a source you picked yourself, so it keeps starting on Club.
        if (offlineId == null && !auto) store.setSourceFor(title ?: Title(id, kind, name, "", null, null, null, 0.0), source.name)
        if (offlineId == null) saveLink(s)
        reconnects = 0
        controlsVisible = true
        placeSubtitles()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.visibility = View.INVISIBLE
    }

    private fun onStreamFailed() {
        // Remembered link expired: forget it and find a fresh one the normal way.
        if (fromCache && phase != Phase.NATIVE) { fromCache = false; dropLink(); start(sourceIndex, auto, save = false, useCache = false); return }
        if (phase == Phase.NATIVE) {
            // Mid-film: reconnect to the same stream twice, then move to another source at the same spot.
            val pos = exo.currentPosition
            if (reconnects < 2) {
                reconnects++
                Toast.makeText(this, t("Reconnecting…"), Toast.LENGTH_SHORT).show()
                exo.prepare(); exo.seekTo(pos); exo.play()
                return
            }
            reconnects = 0
            trying?.let { rejected.add(it.url) }
            trying = null
            dropLink()
            savePosition()
            Toast.makeText(this, t("Switching to another source…"), Toast.LENGTH_SHORT).show()
            auto = true; autoTried = 0
            sourceFailed(); return
        }
        trying?.let { rejected.add(it.url) }
        trying = null
        if (source.direct) { sourceFailed(); return }
        if (candidates.any { it.url !in rejected }) tryNext()
    }

    private fun nextEpisode() {
        savePosition()
        if (episodeCount in 1..episode && season < seasonCount) { season += 1; episode = 1; lifecycleScope.launch { loadEpisodeInfo() } }
        else episode += 1
        updateLabel()
        title?.let { store.watched(it, season, episode) }
        failedSources.clear()
        autoTried = 0
        start(sourceIndex, automatic = true, save = false)
    }

    private fun savePosition() {
        if (phase != Phase.NATIVE) return
        val dur = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
        val credits = segments.firstOrNull { it.type == "credits" }?.startMs?.takeIf { it > dur / 2 }
        // Reaching the credits counts as finished, so it won't "resume" into them.
        val pos = if (credits != null && exo.currentPosition >= credits) dur else exo.currentPosition
        store.savePosition(posKey(), pos, dur)
    }

    private suspend fun loadEpisodeInfo() {
        try {
            episodesList = Tmdb.episodes(id, season)
            episodeCount = episodesList.size.takeIf { it > 0 } ?: Tmdb.episodeCount(id, season)
            seasonCount = Tmdb.seasonCount(id)
        } catch (_: Exception) { }
    }

    private fun playEpisode(e: Int) {
        savePosition()
        episode = e
        updateLabel()
        title?.let { store.watched(it, season, episode) }
        failedSources.clear(); autoTried = 0
        start(sourceIndex, automatic = true, save = false)
    }

    /** Plays a finished download straight from the phone. */
    private fun playOffline(id: String) {
        val d = Downloads.find(this, id) ?: run { phase = Phase.FAILED; return }
        val info = DownloadInfo.from(d.request.data)
        val sub = info?.subtitle?.let { path ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(java.io.File(path)))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage("en").setLabel("English").build()
        }
        val item = d.request.toMediaItem().buildUpon().setSubtitleConfigurations(listOfNotNull(sub)).build()
        speechUrl = null; speech.reset(); speech.seeked(0)
        exo.setMediaSource(DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this, Downloads.offlineFactory(this))).createMediaSource(item))
        exo.prepare()
        previewSource = { DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this, Downloads.offlineFactory(this))).createMediaSource(d.request.toMediaItem()) }
        trying = Stream(d.request.uri.toString(), emptyMap(), hls = true)
        status = "Playing from Downloads"
        lifecycleScope.launch { segments = withLearned(Segments.find(kind, this@PlayerActivity.id, season, episode)) }
    }

    private fun refreshDownloadLabel() {
        val t = title ?: return
        val d = Downloads.find(this, Downloads.idFor(t, season, episode))
        downloadLabelState = when {
            offlineId != null -> "Downloaded"
            d == null -> null
            d.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> "Downloaded"
            d.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED -> "Retry download"
            else -> "Downloading ${d.percentDownloaded.toInt().coerceAtLeast(0)}%"
        }
    }

    private fun startDownload() {
        val t = title ?: Title(id, kind, name, "", null, null, null, 0.0)
        val s = trying ?: return
        val existing = Downloads.find(this, Downloads.idFor(t, season, episode))
        if (existing != null && existing.state != androidx.media3.exoplayer.offline.Download.STATE_FAILED) {
            Toast.makeText(this, t(if (existing.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) "Already downloaded" else "Already downloading"), Toast.LENGTH_SHORT).show()
            return
        }
        val english = subtitleTracks.firstOrNull { it.lang == I18n.subLang } ?: subtitleTracks.firstOrNull { it.lang == "en" } ?: subtitleTracks.firstOrNull()
        Downloads.notificationChannel(this)
        Downloads.start(this, DownloadInfo(t, season, episode, lastHeaders, null), s.url, s.hls, english?.url) { err ->
            Toast.makeText(this, t(err ?: "Downloading — see Downloads"), Toast.LENGTH_SHORT).show()
            refreshDownloadLabel()
        }
    }

    private fun applySubtitleStyle() {
        val sv: SubtitleView = playerView.subtitleView ?: return
        val face = ResourcesCompat.getFont(this, R.font.jakarta_semibold)
        val fg = SubColors[subColorState].let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
        val (bg, edge) = when (subStyleState) {
            0 -> android.graphics.Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_OUTLINE
            1 -> android.graphics.Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            2 -> 0x8C000000.toInt() to CaptionStyleCompat.EDGE_TYPE_NONE
            else -> android.graphics.Color.BLACK to CaptionStyleCompat.EDGE_TYPE_NONE
        }
        sv.setStyle(CaptionStyleCompat(fg, bg, android.graphics.Color.TRANSPARENT, edge, android.graphics.Color.BLACK, face))
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * listOf(.85f, 1f, 1.25f, 1.5f)[subSizeState])
        sv.setApplyEmbeddedStyles(false)
    }

    /** Subtitles sit higher while the controls are showing so they never collide. */
    private fun placeSubtitles() {
        playerView.subtitleView?.setBottomPaddingFraction(if (controlsVisible && phase == Phase.NATIVE) .24f else SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    }

    /** Manual timing on top of the automatic sync; applies instantly, no reload. */
    private fun shiftSubtitles(ms: Long) {
        subOffsetState = ms
        if (ownCues == null) Toast.makeText(this, t("Timing works on added subtitles; pick one in CC"), Toast.LENGTH_SHORT).show()
        shownCues = null
    }

    // ---------- Movio's own subtitle drawing (external subtitles) ----------

    private val speech = SpeechTrack()
    private var speechUrl: String? = null
    private var ownCues: List<SubCue>? = null
    private var ownKey: String? = null
    private var ownFit = SubAligner.Fit(1.0, 0)
    private val fitFor = HashMap<String, SubAligner.Fit>()
    private val candidateCues = java.util.concurrent.ConcurrentHashMap<String, List<SubCue>>()
    var syncStatus by mutableStateOf("Listening to the film…")

    /**
     * Listens to the film, tries every English subtitle version against it, and keeps the one that
     * lines up best, with any frame-rate or delay mismatch corrected.
     */
    private suspend fun syncSubtitles() {
        val key = ownKey ?: run { syncStatus = "Pick a subtitle in CC"; return }
        if (speech.heard == 0 && exo.currentPosition > 60_000) { syncStatus = "Can't hear the film on this device; use Timing"; return }
        if (speech.heard < 600) { syncStatus = "Listening to the film…"; return }
        val cands = allSubs.filter { it.url == key || it.lang == (I18n.subLang.takeIf { l -> l != "off" } ?: "en") || it.lang == "und" }.take(10)
        val dir = java.io.File(cacheDir, "subs").apply { mkdirs() }
        val results = cands.mapNotNull { sub ->
            val cues = candidateCues[sub.url] ?: SubLoad.cues(dir, sub, lastHeaders).also { candidateCues[sub.url] = it }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { SubAligner.evaluate(cues, speech) }?.let { sub to it }
        }
        if (key != ownKey) return
        val current = results.firstOrNull { it.first.url == key }?.second
        val best = results.filter { it.second.sure }.maxByOrNull { it.second.quality }
        if (best != null && best.first.url != key && best.second.quality > (current?.quality ?: -1.0) + 0.08) {
            fitFor[best.first.url] = best.second.fit
            if (selectSub(best.first)) { syncStatus = "Switched to a better-matching version"; return }
        }
        val c = current ?: run { syncStatus = "Checking… (needs a few minutes of talking)"; return }
        if (!c.sure) { syncStatus = if (ownFit == SubAligner.Fit(1.0, 0)) "Checking… (needs a few minutes of talking)" else describe(ownFit); return }
        val changed = c.fit.scale != ownFit.scale || kotlin.math.abs(c.fit.offset - ownFit.offset) >= 300
        if (changed) { ownFit = c.fit; fitFor[key] = c.fit; shownCues = null }
        syncStatus = describe(c.fit)
    }

    private fun describe(f: SubAligner.Fit): String {
        val drift = when {
            kotlin.math.abs(f.scale - 1.0) < 0.0005 -> null
            f.scale > 1.03 || f.scale < 0.97 -> "frame rate fixed"
            else -> "slow drift fixed"
        }
        val shift = if (kotlin.math.abs(f.offset) >= 300) "%+.1fs".format(f.offset / 1000f) else null
        return "In sync" + (listOfNotNull(drift, shift).takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")") ?: "")
    }

    private fun matches(f: androidx.media3.common.Format, s: Subtitle) = f.id?.endsWith(s.url) == true || (f.label != null && f.label == s.label)

    private fun selectSub(s: Subtitle): Boolean {
        val g = exo.currentTracks.groups.firstOrNull { gr -> gr.type == C.TRACK_TYPE_TEXT && (0 until gr.length).any { matches(gr.getTrackFormat(it), s) } } ?: return false
        val i = (0 until g.length).first { matches(g.getTrackFormat(it), s) }
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        return true
    }
    private var shownCues: List<SubCue>? = null
    private val allSubs get() = subtitleTracks + pageSubs.filter { u -> subtitleTracks.none { it.url == u } }.take(4).mapIndexed { i, u -> Subtitle("Source ${i + 1}", "und", u) }

    /** When the selected subtitle is one Movio added, load its lines so we can time them ourselves. */
    private fun pickOwnSubtitle(tracks: androidx.media3.common.Tracks) {
        val g = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        val f = g?.let { gr -> (0 until gr.length).firstOrNull { gr.isTrackSelected(it) }?.let { gr.getTrackFormat(it) } }
        val sub = f?.let { fmt -> allSubs.firstOrNull { s -> fmt.id?.endsWith(s.url) == true } ?: allSubs.firstOrNull { it.label == fmt.label } }
        if (sub == null && f != null) syncStatus = "Built-in subtitles: use Timing if needed"
        if (sub == null) { ownCues = null; ownKey = null; shownCues = null; return }
        if (sub.url == ownKey) return
        ownKey = sub.url; ownCues = null; ownFit = fitFor[sub.url] ?: SubAligner.Fit(1.0, 0); shownCues = null
        syncStatus = fitFor[sub.url]?.let { describe(it) } ?: if (speech.heard < 600) "Listening to the film…" else "Checking…"
        lifecycleScope.launch {
            val cues = SubLoad.cues(java.io.File(cacheDir, "subs").apply { mkdirs() }, sub, lastHeaders)
            if (ownKey == sub.url && cues.isNotEmpty()) { ownCues = cues; renderOwnSubs() }
        }
    }

    private fun renderOwnSubs() {
        val cues = ownCues ?: return
        val view = playerView.subtitleView ?: return
        val off = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val t = ((exo.currentPosition - subOffsetState - ownFit.offset) / ownFit.scale).toLong()
        val now = if (off) emptyList() else cues.filter { t >= it.start && t < it.end && !SubAd.containsMatchIn(it.text) }
        if (now == shownCues) return
        shownCues = now
        // Lines that overlap in time become one block (stacked), never drawn on top of each other.
        val text = now.takeLast(2).map { it.text }.distinct().joinToString("\n")
        view.setCues(if (text.isEmpty()) emptyList() else listOf(androidx.media3.common.text.Cue.Builder().setText(text).build()))
    }

    // ---------- Seek preview: a tiny muted second player at the lowest quality ----------

    private var previewSource: (() -> androidx.media3.exoplayer.source.MediaSource)? = null
    private var previewPlayer: ExoPlayer? = null
    private var previewTarget = -1L
    private val previewTexture by lazy { android.view.TextureView(this) }
    private val previewSeek = Runnable { previewPlayer?.let { if (previewTarget >= 0) it.seekTo(previewTarget) } }

    private fun preview(pos: Long?) {
        if (pos == null) { main.removeCallbacks(previewSeek); previewPlayer?.pause(); return }
        val p = previewPlayer ?: run {
            val src = previewSource ?: return
            ExoPlayer.Builder(this).build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(426, 240).setForceLowestBitrate(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                volume = 0f
                playWhenReady = false
                setVideoTextureView(previewTexture)
                setMediaSource(src())
                prepare()
                previewPlayer = this
            }
        }
        previewTarget = pos
        main.removeCallbacks(previewSeek)
        main.postDelayed(previewSeek, if (p.playbackState == Player.STATE_BUFFERING) 220 else 90)
    }

    private fun releasePreview() {
        main.removeCallbacks(previewSeek)
        previewPlayer?.release()
        previewPlayer = null
    }

    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private val host = object : PlayerHost {
        override val label get() = this@PlayerActivity.label
        override val source get() = if (offlineId != null) "Downloaded" else this@PlayerActivity.source.name
        override val isTv get() = kind == Kind.TV
        override val season get() = this@PlayerActivity.season
        override val episode get() = this@PlayerActivity.episode
        override val episodes get() = episodesList
        override val hasNext get() = kind == Kind.TV && offlineId == null && (episodeCount == 0 || episode < episodeCount || season < seasonCount)
        override val aspectName get() = listOf("Fit", "Fill", "Stretch")[aspect]
        override val downloadLabel get() = downloadLabelState
        override val subSize get() = subSizeState
        override val subStyle get() = subStyleState
        override val subColor get() = subColorState
        override val subOffset get() = subOffsetState
        override val subSync get() = syncStatus
        override val autoplay get() = store.autoplayNext
        override val autoSkip get() = store.autoSkip
        override val segments get() = this@PlayerActivity.segments
        override val nightMode get() = nightOn
        override fun toggleNightMode() { nightOn = !nightOn; prefs.edit().putBoolean("night", nightOn).apply(); applyNight() }
        override val previewView: View? get() = if (previewSource != null) previewTexture else null
        override fun preview(posMs: Long?) = this@PlayerActivity.preview(posMs)
        override fun back() = finish()
        override fun next() = nextEpisode()
        override fun web() = showWeb(reload = true)
        override fun sources() { sourcesOpen = true }
        override fun download() = startDownload()
        override fun playEpisode(e: Int) = this@PlayerActivity.playEpisode(e)
        override fun cycleAspect() {
            aspect = (aspect + 1) % 3
            playerView.resizeMode = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)[aspect]
        }
        override fun brightness(delta: Float): Float {
            val lp = window.attributes
            val cur = if (lp.screenBrightness < 0) .5f else lp.screenBrightness
            lp.screenBrightness = (cur + delta).coerceIn(.02f, 1f)
            window.attributes = lp
            return lp.screenBrightness
        }
        private var vol = -1f
        override fun volume(delta: Float): Float {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (vol < 0) vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            vol = (vol + delta).coerceIn(0f, 1f)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * max).toInt(), 0)
            return vol
        }
        override fun setSubSize(i: Int) { subSizeState = i; store.subSize = i; applySubtitleStyle() }
        override fun setSubStyle(i: Int) { subStyleState = i; store.subStyle = i; applySubtitleStyle() }
        override fun setSubColor(i: Int) { subColorState = i; store.subColor = i; applySubtitleStyle() }
        override fun setSubOffset(ms: Long) = shiftSubtitles(ms)
    }

    /** Media keys work anywhere in the player; the arrows and OK are handled by the controls. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && phase == Phase.NATIVE && !sourcesOpen) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { if (exo.isPlaying) exo.pause() else exo.play(); controlsVisible = true; return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exo.seekTo(exo.currentPosition + 10_000); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0)); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun immersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && phase == Phase.WEB) {
            webBar = true
            main.removeCallbacksAndMessages("bar")
            main.postAtTime({ webBar = false }, "bar", SystemClock.uptimeMillis() + 4000)
        }
        return super.dispatchTouchEvent(ev)
    }

    // ---------- Page ----------

    /** Called from every frame of a source page (sniff.js / player.js). */
    inner class Bridge {
        @JavascriptInterface
        fun stream(url: String, page: String) = found(url, page, hls = true)

        @JavascriptInterface
        fun video(url: String, page: String) = found(url, page, hls = false)

        @JavascriptInterface
        fun state(s: String) {}

        @JavascriptInterface
        fun playing() {
            main.postDelayed({ if (phase == Phase.FINDING && trying == null && !source.direct) showWeb(reload = false) }, 2500)
        }

        private fun found(url: String, page: String, hls: Boolean) {
            val host = android.net.Uri.parse(url).host ?: return
            if (AdBlock.blocks(host)) return
            val p = android.net.Uri.parse(page)
            val origin = "${p.scheme}://${p.host}"
            val headers = mapOf("Referer" to "$origin/", "Origin" to origin)
            main.post { val token = attempt; if (phase == Phase.FINDING || phase == Phase.WEB) if (token == attempt) onCandidate(Stream(url, headers, hls)) }
        }
    }

    private val empty get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun sameSite(a: String, b: String): Boolean {
        fun root(h: String) = h.split('.').takeLast(2).joinToString(".")
        return a.isNotEmpty() && b.isNotEmpty() && root(a) == root(b)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(false)
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.setOnLongClickListener { true }
        webView.addJavascriptInterface(Bridge(), "MovioPlayer")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, feelJs, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(webView, sniffJs, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(webView, playerJs, setOf("*"))
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == null || url == "about:blank") return
                view?.evaluateJavascript(feelJs, null)
                view?.evaluateJavascript(playerJs, null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true && phase == Phase.FINDING && !source.direct) sourceFailed()
            }

            // Every request from every frame passes here: ads are dropped, video playlists are caught.
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return null
                val host = uri.host ?: return null
                if (!sameSite(host, pageHost) && AdBlock.blocks(host)) return empty
                val u = uri.toString()
                val path = (uri.path ?: "").lowercase()
                if ((path.endsWith(".vtt") || path.endsWith(".srt")) && !AdBlock.blocks(host)) main.post { if (phase == Phase.FINDING) pageSubs.add(u) }
                if (u.contains(".m3u8", ignoreCase = true) || Regex("/(stream|master|playlist|manifest)(\\.\\w+)?$").containsMatchIn(path) ||
                    (path.contains("/hls/") && !Regex("\\.(ts|m4s|mp4|aac|key|jpg|jpeg|png|webp|gif|vtt|js|css)$").containsMatchIn(path))) {
                    val headers = request.requestHeaders.orEmpty()
                    main.post { if (phase == Phase.FINDING || phase == Phase.WEB) onCandidate(Stream(u, headers, hls = true)) }
                }
                return null
            }

            // The page may follow its own redirects but never jump to another site (ad redirects).
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val h = request?.url?.host.orEmpty()
                if (AdBlock.blocks(h)) return true
                if (request?.isForMainFrame == false) return false
                if (sameSite(h, pageHost)) return false
                if (request?.isRedirect == true && request.hasGesture() == false) { pageHost = h; return false }
                return true
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                (view?.parent as? ViewGroup)?.removeView(view)
                view?.destroy()
                if (phase != Phase.NATIVE) finish()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) return
                customView = view
                fullscreen.addView(view, FrameLayout.LayoutParams(-1, -1))
                fullscreen.visibility = View.VISIBLE
                immersive()
            }

            override fun onHideCustomView() {
                val v = customView ?: return
                fullscreen.removeView(v)
                customView = null
                fullscreen.visibility = View.GONE
                immersive()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (phase == Phase.NATIVE && exo.isPlaying || phase == Phase.WEB)) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) { }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)) exo.pause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        try { night?.release() } catch (_: Exception) {}
        savePosition()
        main.removeCallbacksAndMessages(null)
        releasePreview()
        exo.release()
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) { }
        super.onDestroy()
    }
}


/** Thin sweeping gradient bar for the loading screen. */
@Composable
private fun LoadingBar() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "load")
    val x by t.animateFloat(-.4f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100)), label = "x")
    Box(Modifier.width(180.dp).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .15f))) {
        Box(Modifier.offset(x = 180.dp * x).width(72.dp).height(3.dp).background(Brand, RoundedCornerShape(50)))
    }
}

/** A web address inside a subtitle line means it's an ad, not dialogue. */
private val SubAd = Regex("(?i)(https?://|www\\.|\\b[a-z0-9-]{2,}\\.(ru|com|net|org|io|tv|to|me|xyz|cc|info|co|top|live|site|online|club|vip)\\b)")
