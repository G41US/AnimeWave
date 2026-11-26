package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Width of the TV menu when folded to icons; pages start to the right of it. */
val TvRailWidth = 76.dp

private class RailItem(val label: String, val icon: Int, val active: Boolean, val onClick: () -> Unit)

/**
 * TV menu down the left edge: soft icons grouped in the middle. When the remote moves onto it (◀),
 * it opens to show names; ▶ goes back to the page.
 */
@Composable
fun TvRail(tab: Tab, top: Screen?, onTab: (Tab) -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit, open: Boolean, onOpen: (Boolean) -> Unit) {
    val w by animateDpAsState(if (open) 270.dp else TvRailWidth, tween(220), label = "rail")
    val items = listOf(
        RailItem("Home", R.drawable.ic_home, top == null && tab == Tab.HOME) { onTab(Tab.HOME) },
        RailItem("Search", R.drawable.ic_search, top == null && tab == Tab.SEARCH) { onTab(Tab.SEARCH) },
        RailItem("Series", R.drawable.ic_tv, top == null && tab == Tab.TV) { onTab(Tab.TV) },
        RailItem("Movies", R.drawable.ic_movies, top == null && tab == Tab.MOVIES) { onTab(Tab.MOVIES) },
        RailItem("My List", R.drawable.ic_bookmark, top == null && tab == Tab.LIST) { onTab(Tab.LIST) },
        RailItem("Downloads", R.drawable.ic_download, top is Screen.Downloads, onDownloads),
        RailItem("Settings", R.drawable.ic_settings, top is Screen.Settings, onSettings),
    )
    Box(
        Modifier.fillMaxHeight().width(w)
            .background(if (open) Brush.horizontalGradient(0f to Ink, .72f to Ink.copy(alpha = .94f), 1f to Color.Transparent)
                else Brush.horizontalGradient(listOf(Ink, Ink.copy(alpha = .0f))))
            .onFocusChanged { onOpen(it.hasFocus) },
    ) {
        Column(Modifier.fillMaxSize().padding(top = 84.dp, bottom = 26.dp)) {
            Spacer(Modifier.weight(1f))
            items.forEach { RailButton(it, open) }
            Spacer(Modifier.weight(1f))
            Profile(open, onSettings)
        }
        // Movio's logo and name, always at the top-left.
        Box(Modifier.padding(start = 20.dp, top = 22.dp).wrapContentWidth(Alignment.Start, unbounded = true)) { Logo(size = 20, anchor = true, name = open) }
    }
}

@Composable
private fun RailButton(item: RailItem, open: Boolean) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // The page you're on: a short white bar at the screen edge.
        if (item.active) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(22.dp).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(Color.White))
        Row(
            Modifier.padding(start = 14.dp, end = if (open) 18.dp else 0.dp)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(48.dp))
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (focused) Color.White else Color.Transparent)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = item.onClick)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = when { focused -> Ink; item.active -> Color.White; else -> Color.White.copy(alpha = .45f) }
            Icon(painterResource(item.icon), null, tint = tint, modifier = Modifier.size(26.dp))
            if (open) {
                Spacer(Modifier.width(16.dp))
                Text(item.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (focused) Ink else if (item.active) Color.White else Soft,
                    style = jakarta(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Profile(open: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val initial = I18n.name.trim().firstOrNull()?.uppercase()
    Row(
        Modifier.padding(start = 14.dp, end = 12.dp).clip(RoundedCornerShape(26.dp))
            .background(if (focused) Color.White.copy(alpha = .14f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
            if (initial != null) Text(initial, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = jakarta())
            else Icon(painterResource(R.drawable.ic_settings), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (open) {
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(end = 8.dp)) {
                Text(I18n.name.ifBlank { "Profile" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, style = jakarta(), maxLines = 1)
                val lang = I18n.langs.firstOrNull { it.code == I18n.lang }?.native ?: "English"
                val subs = if (I18n.subLang == "off") t("No subtitles") else I18n.langs.firstOrNull { it.code == I18n.subLang }?.english?.let { tf("Subtitles {1}", t(it)) } ?: ""
                Text("$lang · $subs", fontSize = 12.sp, color = Muted, style = jakarta(), maxLines = 1)
            }
        }
    }
}

/** TV pages: the clock at the top-right (the logo sits in the menu). */
@Composable
fun TvHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, end = 40.dp, bottom = 8.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        TvClock()
    }
}

@Composable
fun TvClock() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Text(android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(now)), fontSize = 18.sp, fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = .85f), style = jakarta())
}
