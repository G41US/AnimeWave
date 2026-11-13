package com.animewave.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackRecord(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

object PlaybackHistoryStore {
    private const val PREFS = "animewave_playback"
    private const val KEY = "continue_watching"
    private const val MAX_RECORDS = 30
    private const val COMPLETE_TOLERANCE_MS = 30_000L

    fun list(context: Context): List<PlaybackRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val uri = item.optString("uri")
                val title = item.optString("title")
                if (uri.isBlank() || title.isBlank()) return@mapNotNull null
                PlaybackRecord(uri, title, item.optLong("positionMs").coerceAtLeast(0),
                    item.optLong("durationMs").coerceAtLeast(0), item.optLong("updatedAt"))
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun record(context: Context, uriValue: String, title: String, positionMs: Long, durationMs: Long) {
        val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return
        if (uri.scheme !in setOf("https", "content") || uriValue.isBlank() || title.isBlank()) return
        val records = list(context).filterNot { it.uri == uriValue }.toMutableList()
        if (durationMs > 0 && positionMs >= (durationMs * 0.95).toLong()) {
            save(context, records)
            return
        }
        records.add(0, PlaybackRecord(uriValue, title.take(180), positionMs.coerceAtLeast(0),
            durationMs.coerceAtLeast(0), System.currentTimeMillis()))
        save(context, records.take(MAX_RECORDS))
    }

    fun remove(context: Context, uri: String) = save(context, list(context).filterNot { it.uri == uri })
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(context: Context, records: List<PlaybackRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().put("uri", record.uri).put("title", record.title)
                .put("positionMs", record.positionMs).put("durationMs", record.durationMs)
                .put("updatedAt", record.updatedAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
