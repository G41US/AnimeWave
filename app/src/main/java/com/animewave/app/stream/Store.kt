package com.animewave.app.stream

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** What the viewer is watching: a title, plus season/episode for shows. */
data class Progress(val title: Title, val season: Int, val episode: Int)

/** My List and Continue Watching, kept on the device. */
class Store private constructor(context: Context) {
    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }

    private val prefs = context.getSharedPreferences("movio_store", Context.MODE_PRIVATE)

    val myList = mutableStateListOf<Title>().apply { addAll(load("my_list").map { it.first }) }
    val continueWatching = mutableStateListOf<Progress>().apply {
        addAll(load("continue").map { (t, o) -> Progress(t, o.optInt("s", 1), o.optInt("e", 1)) })
    }

    fun inList(t: Title) = myList.any { it.key == t.key }

    fun toggle(t: Title) {
        if (inList(t)) myList.removeAll { it.key == t.key } else myList.add(0, t)
        save("my_list", myList.map { toJson(it) })
    }

    fun watched(t: Title, season: Int = 1, episode: Int = 1) {
        continueWatching.removeAll { it.title.key == t.key }
        continueWatching.add(0, Progress(t, season, episode))
        while (continueWatching.size > 20) continueWatching.removeAt(continueWatching.lastIndex)
        save("continue", continueWatching.map { p -> toJson(p.title).put("s", p.season).put("e", p.episode) })
    }

    private val positions = context.getSharedPreferences("movio_positions", Context.MODE_PRIVATE)

    fun posKey(t: Title, season: Int, episode: Int) = if (t.kind == Kind.TV) "${t.key}:$season:$episode" else t.key

    /** Where playback stopped, so it can resume there. */
    fun savePosition(key: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val done = positionMs > durationMs - 90_000
        positions.edit().putString(key, if (done) "0:$durationMs" else "$positionMs:$durationMs").apply()
    }

    fun position(key: String): Pair<Long, Long>? = positions.getString(key, null)?.split(":")?.let {
        val p = it.getOrNull(0)?.toLongOrNull() ?: return null
        val d = it.getOrNull(1)?.toLongOrNull() ?: return null
        p to d
    }

    private val misc = context.getSharedPreferences("movio_misc", Context.MODE_PRIVATE)

    /** The source that last played this title, tried first next time. */
    fun sourceFor(t: Title): String? = misc.getString("src:${t.key}", null)
    fun setSourceFor(t: Title, name: String) = misc.edit().putString("src:${t.key}", name).apply()

    var subSize: Int
        get() = misc.getInt("sub_size", 1)
        set(v) = misc.edit().putInt("sub_size", v).apply()
    /** 0 none (outline), 1 shadow, 2 see-through box, 3 solid box */
    var subStyle: Int
        get() = misc.getInt("sub_style", 1)
        set(v) = misc.edit().putInt("sub_style", v).apply()
    var subColor: Int
        get() = misc.getInt("sub_color", 0)
        set(v) = misc.edit().putInt("sub_color", v).apply()
    var autoplayNext: Boolean
        get() = misc.getBoolean("autoplay", true)
        set(v) = misc.edit().putBoolean("autoplay", v).apply()
    /** Anime: English dub instead of Japanese with subtitles, when a dub exists. */
    var dub: Boolean
        get() = misc.getBoolean("dub", false)
        set(v) = misc.edit().putBoolean("dub", v).apply()
    var autoSkip: Boolean
        get() = misc.getBoolean("auto_skip", false)
        set(v) = misc.edit().putBoolean("auto_skip", v).apply()
    var alerts: Boolean
        get() = misc.getBoolean("alerts", true)
        set(v) = misc.edit().putBoolean("alerts", v).apply()

    fun lastSeen(showKey: String): String? = misc.getString("aired:$showKey", null)
    fun setLastSeen(showKey: String, v: String) = misc.edit().putString("aired:$showKey", v).apply()

    fun lastEpisode(t: Title): Progress? = continueWatching.firstOrNull { it.title.key == t.key }

    private fun toJson(t: Title) = JSONObject()
        .put("id", t.id).put("kind", t.kind.name).put("name", t.name).put("overview", t.overview)
        .put("poster", t.poster ?: "").put("backdrop", t.backdrop ?: "").put("year", t.year ?: "").put("rating", t.rating)

    private fun save(key: String, items: List<JSONObject>) =
        prefs.edit().putString(key, JSONArray(items).toString()).apply()

    private fun load(key: String): List<Pair<Title, JSONObject>> = try {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            Title(
                o.getInt("id"), Kind.valueOf(o.getString("kind")), o.getString("name"), o.optString("overview"),
                o.optString("poster").ifBlank { null }, o.optString("backdrop").ifBlank { null },
                o.optString("year").ifBlank { null }, o.optDouble("rating", 0.0),
            ) to o
        }
    } catch (_: Exception) { emptyList() }
}
