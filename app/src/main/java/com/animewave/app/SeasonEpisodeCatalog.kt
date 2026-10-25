package com.animewave.app

/**
 * Normalized catalogue contract for season/episode discovery.
 *
 * Metadata and playable availability are deliberately separate: an episode listed by a
 * metadata provider is not proof that a streaming source hosts it. Source adapters should
 * return only episodes they actually found on that source.
 */
data class AnimeSeason(
    val id: String,
    val title: String,
    val number: Int?,
    val year: Int?,
    val format: String?,
    val episodes: List<SourceEpisode>,
)

data class SourceEpisode(
    val id: String,
    val title: String,
    val number: Int?,
    val thumbnail: String?,
    val sourceId: String,
    val sourceName: String,
    val pageUrl: String,
    val playback: EpisodePlayback,
)

sealed interface EpisodePlayback {
    /** Open the episode page in AnimeWave's embedded WebView. */
    data class EmbeddedPage(val url: String) : EpisodePlayback

    /** Native Media3 is allowed only for verified, permitted direct media URLs. */
    data class DirectMedia(val url: String, val mimeType: String? = null) : EpisodePlayback

    /** The source was discovered but cannot currently be opened from this app. */
    data class Unavailable(val reason: String) : EpisodePlayback
}

data class SourceAnimeMatch(
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val pageUrl: String,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val seasonLabel: String? = null,
)

/**
 * Each streaming source needs a real adapter. Implementations must search that source,
 * parse its actual season/episode pages, and report unavailable/error states instead of
 * manufacturing episode links or counts.
 */
interface AnimeSourceAdapter {
    val sourceId: String
    val displayName: String

    suspend fun searchAnime(title: String, alternateTitles: List<String> = emptyList()): List<SourceAnimeMatch>

    suspend fun getSeasons(match: SourceAnimeMatch): List<AnimeSeason>
}

/** User-facing state for the season and episode browser. */
sealed interface EpisodeCatalogState {
    data object Loading : EpisodeCatalogState
    data class Ready(
        val seasons: List<AnimeSeason>,
        val checkedSources: Int,
        val unavailableSources: List<String> = emptyList(),
    ) : EpisodeCatalogState
    data class Failed(val message: String) : EpisodeCatalogState
}
