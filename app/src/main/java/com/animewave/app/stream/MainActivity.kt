package com.animewave.app.stream

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data class Details(val title: Title) : Screen
    data class People(val person: Person) : Screen
    data object Settings : Screen
    data object Downloads : Screen
}

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<Title?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        I18n.init(this)
        AdBlock.warm(this)
        Tmdb.diskDir = File(filesDir, "tmdb").apply { mkdirs() }
        // Load the browser engine now, so the first Play doesn't pay for it.
        window.decorView.post { try { android.webkit.WebView(this).destroy() } catch (_: Exception) { } }
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                // TV boxes: lighter bitmaps and a quicker fade keep scrolling smooth.
                .crossfade(if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) 150 else 300)
                .allowRgb565(packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK))
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizePercent(.04).build() }
                .build()
        )
        NewEpisodesWorker.schedule(this)
        handle(intent)
        setContent { App(openRequest) }
    }

    /** Whatever the remote is on, Up/Down that goes nowhere scrolls the page instead. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> TvScroll.nudge(true)
            android.view.KeyEvent.KEYCODE_DPAD_UP -> TvScroll.nudge(false)
            else -> false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A new-episode notification opens that show's page. */
    private fun handle(intent: Intent?) {
        val id = intent?.getIntExtra("open_id", 0) ?: 0
        val kind = intent?.getStringExtra("open_kind") ?: return
        if (id > 0) openRequest.value = Store.get(this).myList.firstOrNull { it.id == id && it.kind.name == kind }
            ?: Title(id, Kind.valueOf(kind), "", "", null, null, null, 0.0)
    }
}

@Composable
fun App(openRequest: androidx.compose.runtime.MutableState<Title?>) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val scope = rememberCoroutineScope()
    val isTv = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        (context.getSystemService(android.app.UiModeManager::class.java)?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) }
    val wide = isTv || LocalConfiguration.current.screenWidthDp >= 840
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Screen>() }
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var apk by remember { mutableStateOf<File?>(null) }
    var splash by remember { mutableStateOf(true) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(splash) {
        if (splash) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 && store.alerts &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(stack.size) { if (stack.isNotEmpty()) Boot.ready = true }
    // No internet at launch: go straight to Downloads.
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val online = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!online && Downloads.all(context).isNotEmpty()) stack.add(Screen.Downloads)
    }
    LaunchedEffect(openRequest.value) { openRequest.value?.let { stack.add(Screen.Details(it)); openRequest.value = null } }

    val top = stack.lastOrNull()
    BackHandler(enabled = top != null || tab != Tab.HOME) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = Tab.HOME
    }

    fun open(t: Title) { stack.add(Screen.Details(t)) }
    fun play(t: Title) {
        val last = store.lastEpisode(t)
        if (t.kind == Kind.TV && last != null) PlayerActivity.start(context, t, last.season, last.episode)
        else PlayerActivity.start(context, t, 1, 1)
    }
    fun trailer(key: String) {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key"))
        try { context.startActivity(app) } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")))
        }
    }
    fun onUpdate() {
        apk?.let { if (it.exists()) { Updates.install(context, it); return } }
        scope.launch {
            update = UpdateState.Checking
            update = try {
                val info = withContext(Dispatchers.IO) { Updates.latest() }
                if (info == null) UpdateState.UpToDate else {
                    update = UpdateState.Downloading(info.versionName, 0)
                    val file = withContext(Dispatchers.IO) {
                        Updates.download(context, info) { pct -> scope.launch { update = UpdateState.Downloading(info.versionName, pct) } }
                    }
                    apk = file
                    if (!Updates.install(context, file)) Toast.makeText(context, t("Allow AnimeWave to install updates, then tap Install update"), Toast.LENGTH_LONG).show()
                    UpdateState.Ready(info.versionName)
                }
            } catch (e: Exception) { UpdateState.Failed(e.message ?: "Couldn't check for updates. Try again.") }
        }
    }

    // TV is watched from across the room: everything a size up.
    val base = androidx.compose.ui.platform.LocalDensity.current
    val density = if (isTv) androidx.compose.ui.unit.Density(base.density * 1.15f, base.fontScale) else base
    val dir = if (I18n.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides PlainScroll, LocalWide provides wide, LocalTv provides isTv, androidx.compose.ui.platform.LocalDensity provides density,
        androidx.compose.ui.platform.LocalLayoutDirection provides dir) {
    // First launch: name, language and subtitles before anything loads, so it all comes in the right language.
    if (!I18n.setupDone) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            SetupScreen { }
            if (splash) Splash { splash = false }
        }
        return@CompositionLocalProvider
    }
        val bar: @Composable () -> Unit = {
                TopBar(
                    title = when {
                        top is Screen.Settings -> "Settings"
                        top is Screen.Downloads -> "Downloads"
                        top is Screen.Details || top is Screen.People -> null
                        tab == Tab.SEARCH -> "Search"
                        tab == Tab.LIST -> "My List"
                        else -> null
                    },
                    onBack = if (top != null) ({ stack.removeAt(stack.lastIndex) }) else null,
                    settingsActive = top is Screen.Settings,
                    tab = if (top == null) tab else null,
                    onTab = { t -> stack.clear(); tab = t },
                    onSettings = { if (top is Screen.Settings) stack.removeAt(stack.lastIndex) else stack.add(Screen.Settings) },
                )
        }
        var railOpen by remember { mutableStateOf(false) }
        // TV: Right from the menu goes back to where you were on the page.
        val pageFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        Box(Modifier.fillMaxSize().background(Ink)) {
            // TV: pages sit to the right of the left menu; the clock heads each page.
            Box(Modifier.fillMaxSize().then(if (isTv) Modifier.focusRequester(pageFocus).focusRestorer() else Modifier)) {
            CompositionLocalProvider(LocalTopBar provides if (isTv) ({ TvHeader() }) else null) {
            AnimatedContent(top ?: tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "screen") { s ->
                when (s) {
                    is Screen.Details -> DetailsScreen(s.title, store, ::open, { p -> stack.add(Screen.People(p)) }, ::trailer) { t, se, ep -> PlayerActivity.start(context, t, se, ep) }
                    is Screen.People -> PersonScreen(s.person, ::open)
                    Screen.Downloads -> DownloadsScreen { info, id -> PlayerActivity.start(context, info.title, info.season, info.episode, offline = id) }
                    Screen.Settings -> SettingsScreen(
                        Updates.installedName(context), update, AdBlock.size, store,
                        onLanguage = { (context as? android.app.Activity)?.recreate() },
                        onDownloads = { stack.add(Screen.Downloads) },
                        onAlerts = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onUpdate = ::onUpdate,
                    ) {
                        context.cacheDir.resolve("images").deleteRecursively()
                        WebStorage.getInstance().deleteAllData()
                        Toast.makeText(context, t("Cache cleared"), Toast.LENGTH_SHORT).show()
                    }
                    Tab.HOME -> FeedScreen(null, store, ::open, ::play) { stack.add(Screen.Downloads) }
                    Tab.MOVIES -> FeedScreen(Kind.MOVIE, store, ::open, ::play) { stack.add(Screen.Downloads) }
                    Tab.TV -> FeedScreen(Kind.TV, store, ::open, ::play) { stack.add(Screen.Downloads) }
                    Tab.SEARCH -> SearchScreen(::open)
                    Tab.LIST -> MyListScreen(store, ::open) { stack.add(Screen.Downloads) }
                    else -> {}
                }
            }
            }
            }
            if (isTv) {
                // Menu open: the page fades back.
                androidx.compose.animation.AnimatedVisibility(railOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .6f)))
                }
                TvRail(tab, top, onTab = { t -> stack.clear(); tab = t }, onDownloads = { if (top !is Screen.Downloads) stack.add(Screen.Downloads) },
                    onSettings = { if (top !is Screen.Settings) stack.add(Screen.Settings) }, open = railOpen, onOpen = { railOpen = it },
                    onLeave = { runCatching { pageFocus.requestFocus() } })
            }
            if (!isTv) bar()
            if (!wide) Dock(if (top == null) tab else null, { t -> stack.clear(); tab = t }, Modifier.align(Alignment.BottomCenter))

            if (splash) Splash { splash = false } else Greeting()
        }
    }
}
