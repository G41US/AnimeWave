package com.animewave.app

data class LocalFile<T>(val ref: T, val name: String)
data class LocalEpisode<T>(val video: LocalFile<T>, val subtitle: LocalFile<T>?)

private val videoExts = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "3gp", "flv", "wmv", "mpg", "mpeg")
private val subtitleExts = setOf("srt", "vtt", "ass", "ssa")

fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

/** Sorts "Ep 2" before "Ep 10" by zero-padding every number in the name. */
fun naturalKey(name: String) = name.lowercase().replace(Regex("\\d+")) { it.value.padStart(10, '0') }

/**
 * Turns picked files into an ordered episode list. A subtitle is matched to the video
 * with the same base name ("Ep 1.mkv" + "Ep 1.srt" or "Ep 1.en.srt"); a lone video
 * picked with a lone subtitle are paired whatever their names. Other files are ignored.
 */
fun <T> pairEpisodes(files: List<LocalFile<T>>): List<LocalEpisode<T>> {
    val videos = files.filter { extension(it.name) in videoExts }.sortedBy { naturalKey(it.name) }
    val subs = files.filter { extension(it.name) in subtitleExts }
    return videos.map { v ->
        val base = v.name.substringBeforeLast('.').lowercase()
        val sub = subs.firstOrNull { it.name.substringBeforeLast('.').lowercase() == base }
            ?: subs.firstOrNull { it.name.lowercase().startsWith("$base.") }
            ?: subs.singleOrNull()?.takeIf { videos.size == 1 }
        LocalEpisode(v, sub)
    }
}
