package com.animewave.app.aniyomi

import com.animewave.app.AnimeSeason
import com.animewave.app.AnimeSourceAdapter
import com.animewave.app.SourceAnimeMatch
import com.animewave.app.SourceEpisode
import com.animewave.app.EpisodePlayback
import com.animewave.app.playback.ResolvedAnimeStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Host-neutral boundary for the real Aniyomi v0.18.2.1 host implementation.
 *
 * The implementation must be backed by the pinned host's trusted extension loader and
 * source manager; a PackageManager inspector alone must never implement this interface.
 */
interface AniyomiHostRuntime {
    suspend fun installedSources(): List<HostAnimeSource>
    suspend fun search(sourceId: Long, query: String): List<HostAnimeMatch>
    suspend fun seasons(sourceId: Long, animeKey: String): List<HostAnimeSeason>
    suspend fun episodes(sourceId: Long, animeKey: String): List<HostAnimeEpisode>
    suspend fun resolveVideos(sourceId: Long, episodeKey: String): List<HostVideo>
}

data class HostAnimeSource(
    val id: Long,
    val name: String,
    val language: String,
)

data class HostAnimeMatch(
    val key: String,
    val title: String,
    val pageUrl: String,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val seasonLabel: String? = null,
)

data class HostAnimeSeason(
    val key: String,
    val title: String,
    val number: Int? = null,
    val year: Int? = null,
    val format: String? = null,
)

data class HostAnimeEpisode(
    val key: String,
    val title: String,
    val number: Int? = null,
    val thumbnailUrl: String? = null,
    val pageUrl: String,
)

data class HostVideo(
    val url: String,
    val quality: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Maps the real host's source API to AnimeWave's UI/player contracts.
 * Host-specific types stay inside the runtime implementation.
 */
class AniyomiCatalogAdapter(
    private val runtime: AniyomiHostRuntime,
    private val source: HostAnimeSource,
) : AnimeSourceAdapter {
    override val sourceId: String = source.id.toString()
    override val displayName: String = source.name
    private val animeKeysByPageUrl = ConcurrentHashMap<String, String>()
    private val knownEpisodeKeys = ConcurrentHashMap.newKeySet<String>()

    override suspend fun searchAnime(
        title: String,
        alternateTitles: List<String>,
    ): List<SourceAnimeMatch> {
        val queries = (listOf(title) + alternateTitles)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (queries.isEmpty()) return emptyList()

        return queries.flatMap { query ->
            runtime.search(source.id, query).map { match ->
                animeKeysByPageUrl[match.pageUrl] = match.key
                SourceAnimeMatch(
                    sourceId = sourceId,
                    sourceName = displayName,
                    title = match.title,
                    pageUrl = match.pageUrl,
                    year = match.year,
                    episodeCount = match.episodeCount,
                    seasonLabel = match.seasonLabel,
                )
            }
        }.distinctBy { it.pageUrl }
    }

    override suspend fun getSeasons(match: SourceAnimeMatch): List<AnimeSeason> {
        require(match.sourceId == sourceId) { "Anime match belongs to a different source." }
        val animeKey = animeKeysByPageUrl[match.pageUrl]
            ?: throw IllegalArgumentException("Anime match was not produced by this source adapter.")
        val seasons = runtime.seasons(source.id, animeKey)
        val episodes = runtime.episodes(source.id, animeKey)

        // If the source has no explicit season endpoint, expose its episodes as one source-backed
        // season. Do not invent episode counts or URLs.
        val actualSeasons = seasons.ifEmpty {
            listOf(HostAnimeSeason(key = animeKey, title = match.seasonLabel ?: match.title))
        }

        // Only episodes actually returned by this source's catalogue can be resolved later.
        knownEpisodeKeys.addAll(episodes.map { it.key })

        return actualSeasons.map { season ->
            val seasonEpisodes = episodes.filter { episode ->
                // Host adapters may return episode pages grouped by season in their key/page URL.
                // With no stable season relation in the common extension API, do not guess:
                // return the source's actual episode list rather than silently dropping entries.
                season.key == animeKey || episodeBelongsToSeason(episode, season)
            }.ifEmpty { if (actualSeasons.size == 1) episodes else emptyList() }

            AnimeSeason(
                id = season.key,
                title = season.title,
                number = season.number,
                year = season.year,
                format = season.format,
                episodes = seasonEpisodes.map { episode ->
                    SourceEpisode(
                        id = episode.key,
                        title = episode.title,
                        number = episode.number,
                        thumbnail = episode.thumbnailUrl,
                        sourceId = sourceId,
                        sourceName = displayName,
                        pageUrl = episode.pageUrl,
                        playback = EpisodePlayback.Unavailable(
                            "Resolve this episode through the Aniyomi host runtime before playback.",
                        ),
                    )
                },
            )
        }
    }

    /**
     * Resolve an episode using the host's video extractor, preserving source request headers.
     * The caller can pass the result to the existing Media3 resolver/player boundary.
     */
    suspend fun resolveEpisode(episodeKey: String): List<ResolvedAnimeStream> {
        require(episodeKey in knownEpisodeKeys) {
            "Episode must be discovered from this source before resolving its stream."
        }
        return runtime.resolveVideos(source.id, episodeKey)
            .filter { isHttpUrl(it.url) }
            .map { video ->
                ResolvedAnimeStream(
                    url = video.url,
                    headers = video.headers,
                    mimeType = video.mimeType,
                    title = video.quality,
                )
            }
    }

    private fun episodeBelongsToSeason(episode: HostAnimeEpisode, season: HostAnimeSeason): Boolean {
        // Only use an explicit, stable key convention from a host adapter. Never infer by title.
        return episode.key.startsWith("${season.key}#")
    }

    /**
     * Match Media3StreamBridge's network URL policy here too, so invalid candidates are
     * discarded before they cross the host-to-player boundary.
     */
    internal fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        (uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null
    }.getOrDefault(false)
}
