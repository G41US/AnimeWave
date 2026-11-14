package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * A verified, authorized stream candidate for one logical episode.
 * Candidate discovery never extracts media from arbitrary web pages.
 */
data class ResolvedPlaybackCandidate(
    val episode: ProviderEpisode,
    val stream: ProviderStream,
)

/**
 * Resolves every provider listing attached to an episode group, removes duplicate media URLs,
 * and ranks the remaining candidates. This is the engine-side foundation for a future source
 * picker and bounded player failover UI.
 */
object PlaybackCandidateEngine {
    suspend fun resolve(group: ProviderEpisodeGroup): List<ResolvedPlaybackCandidate> =
        coroutineScope {
            group.episodes.map { episode ->
                async(Dispatchers.IO) {
                    ProviderManager.resolve(episode)?.let { ResolvedPlaybackCandidate(episode, it) }
                }
            }.awaitAll()
                .filterNotNull()
                .distinctBy { normalizeUrl(it.stream.url) }
                .sortedWith(
                    compareByDescending<ResolvedPlaybackCandidate> { qualityRank(it.stream.quality) }
                        .thenByDescending { it.stream.subtitles.size }
                        .thenByDescending { mimeRank(it.stream.mimeType) }
                        .thenBy { it.stream.providerName.lowercase() }
                )
        }

    /** Returns the next untried candidate, allowing callers to implement bounded failover. */
    fun nextUntried(
        candidates: List<ResolvedPlaybackCandidate>,
        attemptedUrls: Set<String>,
    ): ResolvedPlaybackCandidate? {
        val attempted = attemptedUrls.mapTo(mutableSetOf(), ::normalizeUrl)
        return candidates.firstOrNull { normalizeUrl(it.stream.url) !in attempted }
    }

    private fun normalizeUrl(value: String): String =
        value.trim().substringBefore('#').removeSuffix("/").lowercase()

    private fun qualityRank(value: String?): Int {
        val text = value.orEmpty().lowercase()
        val height = Regex("""(2160|1440|1080|720|576|480|360)p?""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return height ?: when {
            "4k" in text || "uhd" in text -> 2160
            "full hd" in text || "fhd" in text -> 1080
            "hd" in text -> 720
            else -> 0
        }
    }

    private fun mimeRank(value: String?): Int = when (value?.substringBefore(';')?.trim()?.lowercase()) {
        "application/vnd.apple.mpegurl", "application/x-mpegurl" -> 3
        "application/dash+xml" -> 2
        "video/mp4" -> 1
        else -> 0
    }
}
