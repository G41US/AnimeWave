package com.animewave.app.stream

import com.animewave.app.BuildConfig
import com.animewave.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Subtitles for any source, found by TMDB id: Wyzie Subs when a key is set (best matches),
 * otherwise the subtitle list of Movio's direct stream service.
 */
object Subtitles {
    private val wanted = listOf("en", "fr", "es", "pt", "ar", "de", "it", "yo", "ha", "ig")

    private val found = java.util.concurrent.ConcurrentHashMap<String, List<Subtitle>>()

    suspend fun find(anime: Kind, anilist: Int, s: Int, e: Int): List<Subtitle> = withContext(Dispatchers.IO) {
        val (kind, t) = tmdbTarget(anime, anilist, e) ?: return@withContext emptyList()
        val (id, season, episode) = t
        val key = "${kind.api}:$id:$season:$episode"
        found[key]?.let { return@withContext it }
        lookup(kind, id, season, episode).also { if (it.isNotEmpty()) found[key] = it }
    }

    /**
     * Both lists, like the iPhone app: Wyzie's versions plus the "Alt" versions from the player service.
     * One of the English versions nearly always matches the stream; auto-sync picks it.
     */
    private suspend fun lookup(kind: Kind, id: Int, season: Int, episode: Int): List<Subtitle> = kotlinx.coroutines.coroutineScope {
        val w = async(Dispatchers.IO) { if (BuildConfig.WYZIE_KEY.isNotBlank()) try { wyzie(kind, id, season, episode) } catch (_: Exception) { emptyList() } else emptyList() }
        val p = async(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(25_000) { try { Source.subtitles(kind, id, season, episode) } catch (_: Exception) { emptyList() } } ?: emptyList() }
        val all = (w.await() + p.await()).distinctBy { it.url }
        // English first (Wyzie then Alt), then other languages.
        // The viewer's subtitle language first, then English, then the rest.
        all.sortedBy { if (it.lang == I18n.subLang) 0 else if (it.lang == "en") 1 else 2 }
    }

    private fun wyzie(kind: Kind, id: Int, season: Int, episode: Int): List<Subtitle> {
        val ep = if (kind == Kind.TV) "&season=$season&episode=$episode" else ""
        val c = URL("https://sub.wyzie.io/search?id=$id$ep&language=${wanted.joinToString(",")}&key=${BuildConfig.WYZIE_KEY}")
            .openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            if (c.responseCode !in 200..299) return emptyList()
            val a = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            // Several English versions (most downloaded first, plain before hearing-impaired):
            // the player listens to the film and keeps whichever lines up best.
            return (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("url").startsWith("https://") }
                .groupBy { it.optString("language") }
                .entries.sortedBy { (lang, _) -> wanted.indexOf(lang).let { if (it < 0) 99 else it } }
                .flatMap { (lang, list) ->
                    list.sortedWith(compareBy<org.json.JSONObject> { it.optBoolean("isHearingImpaired") }.thenByDescending { it.optLong("downloadCount") })
                        .take(if (lang == "en" || lang == I18n.subLang) 5 else 1)
                        .mapIndexed { i, o ->
                            val name = o.optString("display").ifBlank { Locale(lang).displayLanguage.ifBlank { lang } }
                            Subtitle(if (i == 0) name else "$name · ${i + 1}", lang, o.optString("url"), srt = o.optString("format").equals("srt", true))
                        }
                }
        } finally { c.disconnect() }
    }
}
