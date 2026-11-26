package com.animewave.app.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Subtitle timing fix: downloads a subtitle once, then writes copies shifted by the chosen offset,
 * so subtitles that run early or late (common on camrips) can be lined up with the picture.
 */
object SubtitleSync {
    private val time = Regex("""(\d{1,2}):(\d{2}):(\d{2})[.,](\d{3})""")
    private val shortTime = Regex("""(?<![\d:])(\d{2}):(\d{2})\.(\d{3})""")

    fun srtToVtt(srt: String): String =
        "WEBVTT\n\n" + srt.replace("\r", "").replace(Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})"""), "$1.$2")

    private fun fmt(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        return "%02d:%02d:%02d.%03d".format(v / 3_600_000, v / 60_000 % 60, v / 1000 % 60, v % 1000)
    }

    fun shift(vtt: String, offsetMs: Long): String = vtt.lines().joinToString("\n") { line ->
        if ("-->" !in line) line else {
            val full = time.replace(line) { m ->
                val (h, mi, se, ms) = m.destructured
                fmt(h.toLong() * 3_600_000 + mi.toLong() * 60_000 + se.toLong() * 1000 + ms.toLong() + offsetMs)
            }
            shortTime.replace(full) { m ->
                val (mi, se, ms) = m.destructured
                fmt(mi.toLong() * 60_000 + se.toLong() * 1000 + ms.toLong() + offsetMs)
            }
        }
    }

    /** Local, shifted copy of [sub] as a file path; the original is fetched once and cached. */
    suspend fun shifted(dir: File, sub: Subtitle, headers: Map<String, String>, offsetMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            val name = sub.url.hashCode().toUInt().toString(16)
            val base = File(dir, "$name.vtt")
            if (!base.exists()) {
                val text = if (sub.url.startsWith("/")) File(sub.url).readText() else {
                    val c = URL(sub.url).openConnection() as HttpURLConnection
                    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                    c.connectTimeout = 10_000; c.readTimeout = 20_000
                    c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
                }
                base.writeText(if (text.trimStart().startsWith("WEBVTT")) text else srtToVtt(text))
            }
            if (offsetMs == 0L) return@withContext base.absolutePath
            File(dir, "$name.$offsetMs.vtt").apply { writeText(shift(base.readText(), offsetMs)) }.absolutePath
        } catch (_: Exception) { null }
    }
}
