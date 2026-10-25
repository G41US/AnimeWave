package com.animewave.app

/**
 * Adapter for AniList's own curated streaming-episode links.
 *
 * This is intentionally not advertised as one of the twelve streaming providers: these
 * are metadata links supplied by AniList, and availability depends on what AniList lists.
 */
class AniListListedEpisodeAdapter : AnimeSourceAdapter {
    override val sourceId: String = "listed"
    override val displayName: String = "AniList metadata"

    override suspend fun searchAnime(
        title: String,
        alternateTitles: List<String>,
    ): List<SourceAnimeMatch> {
        return AniList.search(title)
            .flatMap { anime ->
                val candidates = listOfNotNull(anime.title)
                if (candidates.none { it.equals(title, ignoreCase = true) } &&
                    candidates.none { it.contains(title, ignoreCase = true) || title.contains(it, ignoreCase = true) }
                ) emptyList()
                else listOf(
                    SourceAnimeMatch(
                        sourceId = sourceId,
                        sourceName = displayName,
                        title = anime.title,
                        pageUrl = "https://anilist.co/anime/${anime.id}",
                        year = anime.year,
                        episodeCount = anime.episodes,
                        seasonLabel = anime.format,
                    )
                )
            }
    }

    override suspend fun getSeasons(match: SourceAnimeMatch): List<AnimeSeason> {
        val id = Regex("""/anime/(\\d+)""").find(match.pageUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return emptyList()
        val anime = AniList.byIds(listOf(id)).values.flatten().firstOrNull() ?: return emptyList()
        if (anime.streamingEpisodes.isEmpty()) return emptyList()

        val sourceEpisodes = anime.streamingEpisodes.mapIndexed { index, episode ->
            SourceEpisode(
                id = "anilist:$id:$index",
                title = episode.title,
                number = Regex("""(?:episode|ep\\.?|#)\\s*(\\d+)""", RegexOption.IGNORE_CASE)
                    .find(episode.title)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                thumbnail = episode.thumbnail,
                sourceId = sourceId,
                sourceName = episode.site.ifBlank { displayName },
                pageUrl = episode.url,
                playback = EpisodePlayback.EmbeddedPage(episode.url),
            )
        }

        return listOf(
            AnimeSeason(
                id = "anilist:$id",
                title = anime.title,
                number = 1,
                year = anime.year,
                format = anime.format,
                episodes = sourceEpisodes,
            )
        )
    }
}
