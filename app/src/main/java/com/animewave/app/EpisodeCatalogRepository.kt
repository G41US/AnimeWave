package com.animewave.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Coordinates source adapters without treating a failed source as proof that an anime
 * is unavailable there. Adapters must be registered only after their parsing behaviour
 * has been verified against the source's real pages.
 */
class EpisodeCatalogRepository(
    private val adapters: List<AnimeSourceAdapter>,
) {
    suspend fun load(
        title: String,
        alternateTitles: List<String> = emptyList(),
    ): EpisodeCatalogState {
        if (adapters.isEmpty()) {
            return EpisodeCatalogState.Failed(
                "Episode discovery is not configured yet. No source has been checked."
            )
        }

        val sourceResults = supervisorScope {
            adapters.map { adapter ->
                async {
                    try {
                        val matches = adapter.searchAnime(title, alternateTitles)
                        val best = matches.maxByOrNull { match ->
                            titleMatchScore(title, match.title, alternateTitles)
                        }
                        if (best == null) SourceResult(adapter.displayName, emptyList(), null)
                        else SourceResult(adapter.displayName, adapter.getSeasons(best), null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        SourceResult(adapter.displayName, emptyList(), error.message ?: "Source check failed")
                    }
                }
            }.awaitAll()
        }

        val seasons = sourceResults.flatMap { it.seasons }
            .groupBy { season ->
                listOf(
                    season.title.trim().lowercase(),
                    season.year?.toString().orEmpty(),
                    season.format.orEmpty().lowercase(),
                ).joinToString("|")
            }
            .values
            .map { duplicates ->
                val first = duplicates.first()
                first.copy(
                    episodes = duplicates.flatMap { it.episodes }
                        .distinctBy { episode ->
                            "${episode.sourceId}|${episode.number ?: episode.id}|${episode.pageUrl}"
                        }
                        .sortedWith(compareBy<SourceEpisode> { it.number ?: Int.MAX_VALUE }.thenBy { it.title })
                )
            }
            .sortedWith(compareBy<AnimeSeason> { it.number ?: Int.MAX_VALUE }.thenBy { it.year ?: Int.MAX_VALUE }.thenBy { it.title })

        val unavailable = sourceResults.filter { it.error != null }.map { it.sourceName }
        if (seasons.isEmpty() && unavailable.size == adapters.size) {
            return EpisodeCatalogState.Failed(
                "Couldn't retrieve episode listings from the configured sources."
            )
        }
        return EpisodeCatalogState.Ready(
            seasons = seasons,
            checkedSources = adapters.size,
            unavailableSources = unavailable,
        )
    }

    private fun titleMatchScore(
        requested: String,
        candidate: String,
        alternates: List<String>,
    ): Int {
        val request = normalize(requested)
        val value = normalize(candidate)
        if (value == request) return 100
        if (alternates.any { normalize(it) == value }) return 95
        if (value.contains(request) || request.contains(value)) return 60
        return alternates.maxOfOrNull { alternate ->
            val normalized = normalize(alternate)
            if (normalized.isNotEmpty() && (value.contains(normalized) || normalized.contains(value))) 50 else 0
        } ?: 0
    }

    private fun normalize(value: String) =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private data class SourceResult(
        val sourceName: String,
        val seasons: List<AnimeSeason>,
        val error: String?,
    )
}
