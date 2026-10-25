package com.animewave.app

/**
 * Curated registry of direct video streams that publishers explicitly permit AnimeWave to play.
 *
 * Do not put ordinary web pages, YouTube watch URLs, extracted platform URLs, or unverified
 * aggregator links here. Add an entry only after verifying publisher permission and regional
 * availability. The list intentionally starts empty: AniList episode URLs are web pages, not
 * native-player stream URLs.
 */
data class OfficialEpisodeStream(
    val animeId: Int,
    val episodeTitle: String,
    val url: String,
    val publisher: String,
    val availableCountries: Set<String> = emptySet(),
)

object OfficialPlaybackRegistry {
    // Add reviewed entries here as publishers provide permitted direct HLS (.m3u8) or video URLs.
    private val streams: List<OfficialEpisodeStream> = emptyList()

    fun find(animeId: Int, episodeTitle: String): OfficialEpisodeStream? {
        val match = streams.firstOrNull {
            it.animeId == animeId && normalize(it.episodeTitle) == normalize(episodeTitle)
        } ?: return null

        val uri = runCatching { java.net.URI(match.url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || match.publisher.isBlank()) return null
        if (match.availableCountries.isNotEmpty() && "NG" !in match.availableCountries) return null
        val path = uri.path.orEmpty().lowercase()
        if (!path.endsWith(".m3u8") && !path.endsWith(".mp4") && !path.endsWith(".mpd")) return null
        return match
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")
}
