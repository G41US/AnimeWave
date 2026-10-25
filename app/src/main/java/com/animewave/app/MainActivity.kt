package com.animewave.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0A0B10)
private val Panel = Color(0xFF151722)
private val Violet = Color(0xFF9B6DFF)
private val Muted = Color(0xFFB7B9C7)

data class Anime(
    val title: String,
    val subtitle: String,
    val genre: String,
    val year: String,
    val accent: Color,
    val synopsis: String,
    val watchUrl: String = ""
)

private val demoAnime = listOf(
    Anime("Skybound", "SEASON 1 • 12 EPISODES", "Adventure", "2025", Color(0xFF6247AA),
        "A young cartographer discovers a hidden world above the clouds. Demo listing: connect a licensed catalog provider to load real artwork and metadata."),
    Anime("Neon Ronin", "SEASON 2 • 24 EPISODES", "Action", "2024", Color(0xFFB33B65),
        "In a rain-soaked future, a wandering swordsman uncovers a city-wide conspiracy."),
    Anime("Moonlit Academy", "SEASON 1 • 10 EPISODES", "Fantasy", "2025", Color(0xFF2C6D87),
        "New students at an unusual academy learn that the moon holds ancient secrets."),
    Anime("Paper Stars", "MOVIE • 1H 48M", "Drama", "2023", Color(0xFFB17A3A),
        "Two friends reconnect through a box of letters left beneath an old observatory."),
    Anime("Mecha Frontier", "SEASON 1 • 18 EPISODES", "Sci-Fi", "2024", Color(0xFF3B7B65),
        "A rookie pilot joins a frontier crew protecting remote colonies."),
    Anime("Spirit Market", "SEASON 1 • 13 EPISODES", "Supernatural", "2025", Color(0xFF7651A8),
        "A night market opens only to those who have lost something precious.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnimeWaveApp() }
    }
}

@Composable
fun AnimeWaveApp() {
    var tab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Anime?>(null) }
    var saved by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    val filtered = demoAnime.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.genre.contains(query, ignoreCase = true)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Violet,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(Violet, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("A", fontSize = 23.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("ANIME", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("WAVE", fontSize = 23.sp, fontWeight = FontWeight.Light, color = Violet, letterSpacing = 2.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Home", "Discover", "My List").forEach { item ->
                            NavChip(item, tab == item) { tab = item }
                        }
                    }
                }

                if (selected == null) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 245.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF292044), Color(0xFF151722), Ink)), RoundedCornerShape(24.dp))
                            .padding(26.dp)
                    ) {
                        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f)) {
                            Text("YOUR NEXT\nANIME OBSESSION", fontSize = 12.sp, color = Violet, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Find your\nnext world.", fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(10.dp))
                            Text("Explore action, fantasy, romance and more.", color = Muted, fontSize = 14.sp)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { tab = "Discover" }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                                Text("Explore anime")
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        placeholder = { Text("Search anime or genre…", color = Muted) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet, unfocusedBorderColor = Color(0xFF343646),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(22.dp))
                    if (tab == "My List") {
                        AnimeSection("YOUR WATCHLIST", filtered.filter { saved.contains(it.title) }, onPick = { selected = it })
                    } else {
                        AnimeSection(if (tab == "Discover") "DISCOVER ANIME" else "TRENDING NOW", filtered, onPick = { selected = it })
                        AnimeSection("ACTION & ADVENTURE", filtered.filter { it.genre in listOf("Action", "Adventure", "Sci-Fi") }, onPick = { selected = it })
                        AnimeSection("NEW & NOTEWORTHY", filtered.sortedByDescending { it.year }, onPick = { selected = it })
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Catalog demo • Connect a licensed metadata and streaming provider in Settings/source code.",
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                    )
                } else {
                    val anime = selected!!
                    Column(Modifier.padding(24.dp)) {
                        Text("‹  BACK TO BROWSE", color = Violet, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { selected = null }.padding(bottom = 20.dp))
                        Box(Modifier.fillMaxWidth().height(230.dp).background(
                            Brush.linearGradient(listOf(anime.accent, Panel, Ink)), RoundedCornerShape(22.dp)
                        )) {
                            Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                                Text(anime.genre.uppercase(), color = Color.White.copy(alpha = .8f), letterSpacing = 2.sp, fontSize = 11.sp)
                                Text(anime.title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("${anime.year}  •  ${anime.subtitle}", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(anime.synopsis, color = Color(0xFFE0E1E8), fontSize = 15.sp, lineHeight = 23.sp)
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                if (anime.watchUrl.startsWith("https://")) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(anime.watchUrl)))
                                } else {
                                    android.widget.Toast.makeText(context, "Add an authorized HTTPS stream URL for this title in the catalog.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                                Text("▶  Watch")
                            }
                            OutlinedButton(onClick = {
                                saved = if (saved.contains(anime.title)) saved - anime.title else saved + anime.title
                            }) { Text(if (saved.contains(anime.title)) "✓  Saved" else "+  My List") }
                        }
                        Spacer(Modifier.height(26.dp))
                        Text("EPISODES", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Episode lists and playable streams appear when a licensed provider is connected.", color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavChip(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (active || focused) Color.White else Muted,
        fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(if (active || focused) Violet.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun AnimeSection(title: String, items: List<Anime>, onPick: (Anime) -> Unit) {
    Column(Modifier.padding(top = 26.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp,
            modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(13.dp))
        if (items.isEmpty()) {
            Text("Nothing here yet — save anime from Discover.", color = Muted, modifier = Modifier.padding(horizontal = 22.dp))
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                items(items, key = { it.title }) { anime ->
                    AnimeCard(anime, onClick = { onPick(anime) })
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(145.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().height(184.dp)
                .background(Brush.linearGradient(listOf(anime.accent, anime.accent.copy(alpha = .55f), Panel)), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text(anime.genre.uppercase(), fontSize = 9.sp, color = Color.White.copy(alpha = .9f), letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.TopStart))
            Text(anime.title.split(" ").joinToString("\n"), fontSize = 22.sp, lineHeight = 23.sp,
                fontWeight = FontWeight.Black, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart))
            if (focused) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)))
        }
        Spacer(Modifier.height(9.dp))
        Text(anime.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${anime.year} • ${anime.genre}", fontSize = 11.sp, color = Muted, maxLines = 1)
    }
}
