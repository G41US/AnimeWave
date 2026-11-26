package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First launch: your name, the app's language and the subtitle language. Asked once; changeable in Settings. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(I18n.name) }
    val wide = LocalWide.current
    Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = if (wide) 560.dp else 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_tile), null, Modifier.size(44.dp))
                Spacer(Modifier.weight(1f))
                // Progress: three dots, the current one long.
                repeat(3) { i ->
                    Box(Modifier.padding(start = 6.dp).height(6.dp).width(if (i == step) 22.dp else 6.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= step) Color.White else Color.White.copy(alpha = .25f)))
                }
            }
            Spacer(Modifier.height(30.dp))
            AnimatedContent(step, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "setup") { s ->
                Column {
                    when (s) {
                        0 -> {
                            Heading("Welcome to AnimeWave", "Let's set things up. It only takes a moment.")
                            Spacer(Modifier.height(26.dp))
                            Text("What should we call you?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Soft, style = jakarta())
                            Spacer(Modifier.height(10.dp))
                            val focus = remember { FocusRequester() }
                            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Glass)
                                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty()) Text("Your name", color = Muted, fontSize = 17.sp, style = jakarta())
                                BasicTextField(name, { if (it.length <= 24) name = it }, singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(Violet),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { I18n.name = name; step = 1 }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
                            }
                            if (!wide) LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) { } }
                            Spacer(Modifier.height(26.dp))
                            PrimaryButton("Continue", R.drawable.ic_next, Modifier.fillMaxWidth()) { I18n.name = name; step = 1 }
                        }
                        1 -> {
                            Heading("App language", "Menus, buttons and film descriptions will use this language.")
                            Spacer(Modifier.height(20.dp))
                            // Each language written in itself, so anyone can find theirs.
                            I18n.langs.forEach { l ->
                                Choice(l.native, if (l.code == "en") null else l.english.let { t(it) }.takeIf { it != l.native }, I18n.lang == l.code) {
                                    I18n.lang = l.code
                                    if (I18n.subLang == "en" || I18n.langs.any { it.code == I18n.subLang }) I18n.subLang = l.code
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            Buttons({ step = 0 }) { step = 2 }
                        }
                        else -> {
                            Heading("Subtitle language", "AnimeWave will turn these subtitles on by default.")
                            Spacer(Modifier.height(20.dp))
                            I18n.langs.forEach { l -> Choice(t(l.english), l.native.takeIf { t(l.english) != it }, I18n.subLang == l.code) { I18n.subLang = l.code } }
                            Choice(t("No subtitles"), null, I18n.subLang == "off") { I18n.subLang = "off" }
                            Spacer(Modifier.height(22.dp))
                            Buttons({ step = 1 }, "Start watching") {
                                I18n.name = name.trim(); I18n.setupDone = true; I18n.save(); onDone()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = jakarta())
    Spacer(Modifier.height(8.dp))
    Text(body, fontSize = 15.sp, lineHeight = 21.sp, color = Muted, style = jakarta())
}

@Composable
private fun Choice(label: String, sub: String?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (on) Violet.copy(alpha = .2f) else Glass)
            .border(1.5.dp, if (on) Violet else Color.Transparent, RoundedCornerShape(16.dp))
            .focusGlow(RoundedCornerShape(16.dp), grow = false)
            .clickableNoRipple(onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, style = jakarta())
            if (sub != null) Text(sub, fontSize = 13.sp, color = Muted, style = jakarta())
        }
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(if (on) Violet else Color.Transparent)
            .border(2.dp, if (on) Violet else Color.White.copy(alpha = .3f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            if (on) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun Buttons(onBack: () -> Unit, next: String = "Continue", onNext: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Back", null, Modifier.weight(1f), onClick = onBack)
        PrimaryButton(next, R.drawable.ic_next, Modifier.weight(2f), onClick = onNext)
    }
}

/** "Good evening, Marvelous": shown for a moment when Movio opens. */
@Composable
fun BoxScope.Greeting() {
    val text = remember { I18n.greeting() } ?: return
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); shown = true; kotlinx.coroutines.delay(3200); shown = false }
    androidx.compose.animation.AnimatedVisibility(shown, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp),
        enter = fadeIn(tween(300)) + androidx.compose.animation.slideInVertically { -it / 2 },
        exit = fadeOut(tween(400))) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, style = jakarta(), textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Panel.copy(alpha = .92f))
                .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

/** Settings: name, app language and subtitle language. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCard(onLanguage: () -> Unit) {
    Card {
        Label("Profile")
        Spacer(Modifier.height(10.dp))
        var name by remember { mutableStateOf(I18n.name) }
        Text("Name", fontSize = 13.sp, color = Muted, style = jakarta())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Glass).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (name.isEmpty()) Text("Your name", color = Muted, fontSize = 16.sp, style = jakarta())
            BasicTextField(name, { if (it.length <= 24) { name = it; I18n.name = it; I18n.save() } }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Violet), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Text("App language", fontSize = 13.sp, color = Muted, style = jakarta())
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            I18n.langs.forEach { l -> Chip(l.native, I18n.lang == l.code) { if (I18n.lang != l.code) { I18n.lang = l.code; I18n.save(); onLanguage() } } }
        }
        Spacer(Modifier.height(16.dp))
        Text("Subtitle language", fontSize = 13.sp, color = Muted, style = jakarta())
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            I18n.langs.forEach { l -> Chip(t(l.english), I18n.subLang == l.code) { I18n.subLang = l.code; I18n.save() } }
            Chip(t("No subtitles"), I18n.subLang == "off") { I18n.subLang = "off"; I18n.save() }
        }
    }
}
