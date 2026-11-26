package com.animewave.app.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Movio's words in the viewer's language (English, French, Spanish, Portuguese, Arabic),
 * plus the few things the first-time setup asks: name, app language and subtitle language.
 * The word list lives in assets/i18n.json (built from i18n/build.py, shared with the iPhone and web apps).
 */
object I18n {
    data class Lang(val code: String, val native: String, val english: String, val tmdb: String)

    val langs = listOf(
        Lang("en", "English", "English", "en-US"),
        Lang("fr", "Français", "French", "fr-FR"),
        Lang("es", "Español", "Spanish", "es-ES"),
        Lang("pt", "Português", "Portuguese", "pt-BR"),
        Lang("ar", "العربية", "Arabic", "ar-SA"),
    )

    /** The app's language; reading it inside a screen makes that screen redraw when it changes. */
    var lang by mutableStateOf("en")
    var name by mutableStateOf("")
    /** "en", "fr", "es", "pt", "ar", or "off". */
    var subLang by mutableStateOf("en")
    var setupDone by mutableStateOf(true)

    val rtl get() = lang == "ar"
    val tmdbLang get() = langs.firstOrNull { it.code == lang }?.tmdb ?: "en-US"

    private var table: Map<String, Map<String, String>> = emptyMap()
    private var patterns: Map<String, List<Pair<Regex, String>>> = emptyMap()
    private val cache = HashMap<String, String>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("movio_profile", Context.MODE_PRIVATE)
        prefs = p
        setupDone = p.getBoolean("setup_done", false)
        lang = p.getString("lang", null) ?: java.util.Locale.getDefault().language.takeIf { l -> langs.any { it.code == l } } ?: "en"
        subLang = p.getString("sub_lang", null) ?: lang
        name = p.getString("name", "") ?: ""
        try {
            val o = JSONObject(context.assets.open("i18n.json").bufferedReader().use { it.readText() })
            table = o.keys().asSequence().associateWith { l -> o.getJSONObject(l).let { m -> m.keys().asSequence().associateWith { m.getString(it) } } }
            // "Season {1}" also matches "Season 3": numbers fill the gaps.
            patterns = table.mapValues { (_, m) ->
                m.keys.filter { '{' in it }.sortedByDescending { it.length }.map { k ->
                    Regex("^" + Regex("\\{\\d\\}").split(k).joinToString("(\\d[\\d.,:]*)") { Regex.escape(it) } + "$") to k
                }
            }
        } catch (_: Exception) { }
    }

    fun save() {
        prefs?.edit()?.putString("lang", lang)?.putString("sub_lang", subLang)?.putString("name", name.trim())?.putBoolean("setup_done", setupDone)?.apply()
        cache.clear()
    }

    /** [s] in the app's language (unchanged if it isn't one of Movio's words). */
    fun t(s: String): String {
        val l = lang
        if (l == "en" || s.isEmpty()) return s
        val m = table[l] ?: return s
        m[s]?.let { return it }
        if (s.none { it.isDigit() }) return s
        val ck = "$l\u0000$s"
        cache[ck]?.let { return it }
        var out = s
        for ((re, key) in patterns[l].orEmpty()) {
            val hit = re.matchEntire(s) ?: continue
            out = fill(m[key] ?: break, hit.groupValues.drop(1)); break
        }
        if (cache.size > 2000) cache.clear()
        cache[ck] = out
        return out
    }

    /** A sentence with names in it, e.g. tf("Because you watched {1}", title). */
    fun tf(key: String, vararg args: Any): String = fill(if (lang == "en") key else table[lang]?.get(key) ?: key, args.map { it.toString() })

    private fun fill(tpl: String, args: List<String>): String {
        var r = tpl
        args.forEachIndexed { i, a -> r = r.replace("{${i + 1}}", a) }
        return r
    }

    /** "Good evening, Marvelous", or plain "Hi" style when no name was given. */
    fun greeting(): String? {
        val n = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return tf(when (h) { in 5..11 -> "Good morning, {1}"; in 12..16 -> "Good afternoon, {1}"; else -> "Good evening, {1}" }, n)
    }
}

fun t(s: String) = I18n.t(s)

/** Row names with a title or genre in them ("Because you watched X", "Popular Action"). */
fun rowTitle(name: String): String {
    if (I18n.lang == "en") return name
    t(name).let { if (it != name) return it }
    for (p in listOf("Because you watched ", "Highly rated ", "Popular ", "More ")) {
        if (name.startsWith(p)) {
            val rest = name.removePrefix(p)
            return tf(p + "{1}", if (p == "Because you watched ") rest else t(rest))
        }
    }
    return name
}
fun tf(key: String, vararg args: Any) = I18n.tf(key, *args)
