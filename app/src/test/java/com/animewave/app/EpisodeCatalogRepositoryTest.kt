package com.animewave.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeCatalogRepositoryTest {
    @Test
    fun mergesEpisodesFromMultipleAdaptersAndKeepsSourceIdentity() = runBlocking {
        val first = FakeAdapter(
            displayName = "Source A",
            episodes = listOf(episode("a", "Source A", 2), episode("a", "Source A", 1)),
        )
        val second = FakeAdapter(
            displayName = "Source B",
            episodes = listOf(episode("b", "Source B", 1)),
        )

        val result = EpisodeCatalogRepository(listOf(first, second)).load("Example Anime")

        assertTrue(result is EpisodeCatalogState.Ready)
        val ready = result as EpisodeCatalogState.Ready
        assertEquals(2, ready.checkedSources)
        assertEquals(listOf(1, 1, 2), ready.seasons.single().episodes.mapNotNull { it.number }.sorted())
        assertEquals(setOf("a", "b"), ready.seasons.single().episodes.map { it.sourceId }.toSet())
    }

    @Test
    fun reportsEmptyConfigurationWithoutInventingResults() = runBlocking {
        val result = EpisodeCatalogRepository(emptyList()).load("Example Anime")
        assertTrue(result is EpisodeCatalogState.Failed)
    }

    private fun episode(sourceId: String, sourceName: String, number: Int) = SourceEpisode(
        id = "$sourceId-$number",
        title = "Episode $number",
        number = number,
        thumbnail = null,
        sourceId = sourceId,
        sourceName = sourceName,
        pageUrl = "https://example.org/$sourceId/$number",
        playback = EpisodePlayback.Unavailable("No stream in repository test"),
    )

    private class FakeAdapter(
        override val displayName: String,
        private val episodes: List<SourceEpisode>,
    ) : AnimeSourceAdapter {
        override val sourceId = displayName.lowercase().replace(" ", "-")

        override suspend fun searchAnime(
            title: String,
            alternateTitles: List<String>,
        ) = listOf(
            SourceAnimeMatch(
                sourceId = sourceId,
                sourceName = displayName,
                title = title,
                pageUrl = "https://example.org/anime",
            )
        )

        override suspend fun getSeasons(match: SourceAnimeMatch) = listOf(
            AnimeSeason(
                id = "example-season",
                title = "Example Anime",
                number = 1,
                year = 2026,
                format = "TV",
                episodes = episodes,
            )
        )
    }
}
