package com.animewave.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Renders screens to PNGs (app/build/reports/paparazzi) without a device.
 * Optional: point ANIMEWAVE_SNAPSHOT_DIR at a folder holding an AniList response (data.json)
 * and artwork (img/<md5 of url>) to render real titles and images; otherwise sample data is used.
 */
class ScreenshotTest {
    private val tv = DeviceConfig.PIXEL_5.copy(
        screenWidth = 1920, screenHeight = 1080, xdpi = 320, ydpi = 320,
        density = Density.XHIGH, orientation = ScreenOrientation.LANDSCAPE,
    )

    @get:Rule val paparazzi = Paparazzi(deviceConfig = tv, theme = "android:Theme.Material.NoActionBar")

    private val dir = System.getenv("ANIMEWAVE_SNAPSHOT_DIR")?.let(::File)
    private val data: Map<String, List<Anime>> =
        dir?.resolve("data.json")?.takeIf { it.exists() }?.let { parseResponse(it.readText()) } ?: sample()

    private val images: (String?) -> Painter? = { url ->
        url?.let { dir?.resolve("img/" + md5(it)) }?.takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()?.let(::BitmapPainter)
    }

    /** Renders a still frame: inspection mode makes entrance and loop animations start finished. */
    private fun snap(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { content() }
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sample() = mapOf(
        "trending" to (1..10).map { i ->
            Anime(
                i, "Sample Anime With A Longer Title $i", null, null, null, 0xFF6247AA,
                listOf("Action", "Fantasy"), 2026, "TV", 12, 70 + i,
                "A young hero discovers a hidden world above the clouds and must choose who to trust.",
                listOf("Crunchyroll" to "https://example.com"),
                (1..4).map { Episode("Episode $it - The Beginning", null, "https://example.com/$it", "Crunchyroll") },
            )
        },
    )

    private fun home() = Loaded(mapOf(
        "trending" to data.getValue("trending"),
        "airing" to (data["airing"] ?: data.getValue("trending").reversed()),
        "action" to data.getValue("trending").shuffled(java.util.Random(1)),
        "popular" to data.getValue("trending").reversed(),
    ), false) {}

    @Composable
    private fun Frame(wide: Boolean, tab: String, scroll: Int = 0, content: @Composable ColumnScope.() -> Unit) {
        CompositionLocalProvider(LocalImageOverride provides images) {
            AnimeWaveTheme(wide) { Shell(tab, {}, remember { FocusRequester() }, remember { ScrollState(scroll) }, content = content) }
        }
    }

    @Composable
    private fun HomeShot(wide: Boolean, scroll: Int = 0) = Frame(wide, "Home", scroll) {
        HomeContent(home(), setOf(data.getValue("trending").first().id), FocusRequester(), {}, {}, {}, onPlayClassic = {})
    }

    @Composable
    private fun DetailShot(wide: Boolean) = AnimeWaveTheme(wide) {
        CompositionLocalProvider(LocalImageOverride provides images) {
            Column(Modifier.fillMaxSize().background(Ink)) {
                DetailContent(data.getValue("trending")[1], false, FocusRequester(), {}, {}, {}, {}, "listed", {}, {}, {})
            }
        }
    }

    @Test fun tvHome() = snap { HomeShot(wide = true) }
    @Test fun tvHomeRows() = snap { HomeShot(wide = true, scroll = 1300) }
    @Test fun tvDetail() = snap { DetailShot(wide = true) }
    @Test fun tvDiscover() = snap {
        Frame(true, "Discover") { ScreenTitle("Discover", "The most popular anime in every genre"); GenreChips("Action") {}; AnimeGrid(home().map { it.getValue("trending") }) {} }
    }

    @Test fun tvDetailEpisodes() = snap {
        AnimeWaveTheme(true) {
            CompositionLocalProvider(LocalImageOverride provides images) {
                Column(Modifier.fillMaxSize().background(Ink).verticalScroll(remember { ScrollState(1000) })) {
                    DetailContent(data.getValue("trending")[1], false, FocusRequester(), {}, {}, {}, {}, "listed", {}, {}, {})
                }
            }
        }
    }

    @Test fun tvSettings() = snap {
        Frame(true, "Settings") {
            ScreenTitle("Settings")
            SettingsScreen("1.0.12", UpdateState.Downloading(UpdateInfo(14, "1.0.14", "Redesign UI"), .42f), {}, {}, {})
        }
    }

    @Test fun phoneSettings() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap {
            CompositionLocalProvider(LocalImageOverride provides images) {
                AnimeWaveTheme(false) {
                    Shell("Settings", {}, remember { FocusRequester() }, remember { ScrollState(0) }, update = true) {
                        ScreenTitle("Settings")
                        SettingsScreen("1.0.12", UpdateState.Available(UpdateInfo(14, "1.0.14", "Redesign UI: cinematic hero, Top 10")), {}, {}, {})
                    }
                }
            }
        }
    }

    @Test fun tvIntro() = snap { AnimeWaveTheme(true) { IntroContent(1f) } }
    @Test fun tvIntroMid() = snap { AnimeWaveTheme(true) { IntroContent(.38f) } }

    @Test fun phoneIntro() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap { AnimeWaveTheme(false) { IntroContent(1f) } }
    }

    @Test fun phoneHome() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap { HomeShot(wide = false) }
    }

    @Test fun phoneHomeRows() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap { HomeShot(wide = false, scroll = 1700) }
    }

    @Test fun phoneDetailEpisodes() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        val anime = data.getValue("trending").firstOrNull { it.streamingEpisodes.isNotEmpty() } ?: data.getValue("trending").first()
        snap {
            AnimeWaveTheme(false) {
                CompositionLocalProvider(LocalImageOverride provides images) {
                    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(remember { ScrollState(1500) })) {
                        DetailContent(anime, true, FocusRequester(), {}, {}, {}, {}, "listed", {}, {}, {})
                    }
                }
            }
        }
    }

    @Test fun phoneSearch() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap {
            CompositionLocalProvider(LocalImageOverride provides images) {
                AnimeWaveTheme(false) {
                    Shell("Search", {}, remember { FocusRequester() }, remember { ScrollState(0) }) {
                        ScreenTitle("Search")
                        SearchBox("") {}
                        SearchSuggestions(listOf("Frieren", "One Piece", "Solo Leveling"), data.getValue("trending").take(8), {}, {}, {})
                    }
                }
            }
        }
    }

    @Test fun phoneDiscover() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap {
            CompositionLocalProvider(LocalImageOverride provides images) {
                AnimeWaveTheme(false) {
                    Shell("Discover", {}, remember { FocusRequester() }, remember { ScrollState(0) }) {
                        ScreenTitle("Discover", "The most popular anime in every genre")
                        GenreChips("Action") {}
                        GenreShowcase("Action", data.getValue("action").first()) {}
                        AnimeGrid(home().map { it.getValue("action").drop(1) }) {}
                    }
                }
            }
        }
    }

    @Test fun phoneLoading() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap {
            AnimeWaveTheme(false) {
                Shell("Home", {}, remember { FocusRequester() }, remember { ScrollState(0) }) {
                    HomeContent(Loaded(null, false) {}, emptySet(), FocusRequester(), {}, {}, {})
                }
            }
        }
    }

    private fun controls(wide: Boolean) = snap {
        CompositionLocalProvider(LocalImageOverride provides images) {
            AnimeWaveTheme(wide) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    Art(data.getValue("trending")[2].banner ?: data.getValue("trending")[2].coverXL, Modifier.fillMaxSize())
                    PlayerControls(
                        title = data.getValue("trending")[2].title + " — Episode 6", subtitle = "Crunchyroll",
                        isPlaying = true, buffering = false, positionMs = 754_000, durationMs = 1_420_000, bufferedMs = 960_000,
                        hasPrevious = true, hasNext = true, playFocus = remember { FocusRequester() },
                        onBack = {}, onPlayPause = {}, onSeekTo = {}, onSkip = {}, onPrevious = {}, onNext = {}, onSubtitles = {}, onQuality = {},
                    )
                    SkipBubble(10, Modifier.align(androidx.compose.ui.Alignment.CenterEnd).padding(end = 56.dp))
                }
            }
        }
    }

    @Test fun tvPlayer() = controls(wide = true)

    @Test fun phonePlayer() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(orientation = ScreenOrientation.LANDSCAPE, screenWidth = 2340, screenHeight = 1080))
        controls(wide = false)
    }

    @Test fun phoneDetail() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        snap { DetailShot(wide = false) }
    }
}
