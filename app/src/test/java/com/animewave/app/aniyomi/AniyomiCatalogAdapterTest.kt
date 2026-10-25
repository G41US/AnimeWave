package com.animewave.app.aniyomi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniyomiCatalogAdapterTest {
    @Test
    fun searchMapsHostResultsAndDeduplicatesPageUrls() = runBlocking {
        val runtime = FakeRuntime()
        val adapter = AniyomiCatalogAdapter(runtime, HostAnimeSource(42L, "Example", "en"))

        val results = adapter.searchAnime("Example Anime", listOf("Example Anime", "Alt Title"))

        assertEquals(2, runtime.queries.size)
        assertEquals(listOf("Example Anime", "Alt Title"), runtime.queries)
        assertEquals(1, results.size)
        assertEquals("Example Anime", results.single().title)
        assertEquals("https://source.example/anime/1", results.single().pageUrl)
    }

    @Test
    fun resolvePreservesHeadersAndRejectsNonHttpUrls() = runBlocking {
        val runtime = FakeRuntime().apply {
            videos = listOf(
                HostVideo(
                    url = "https://cdn.example/video.m3u8",
                    quality = "1080p",
                    mimeType = "application/x-mpegURL",
                    headers = mapOf("Referer" to "https://source.example/"),
                ),
                HostVideo(url = "file:///etc/passwd"),
                HostVideo(url = "javascript:alert(1)"),
            )
        }
        val adapter = AniyomiCatalogAdapter(runtime, HostAnimeSource(42L, "Example", "en"))
        val match = adapter.searchAnime("Example Anime").single()
        adapter.getSeasons(match)

        val streams = adapter.resolveEpisode("episode-1")

        assertEquals(1, streams.size)
        assertEquals("https://cdn.example/video.m3u8", streams.single().url)
        assertEquals("https://source.example/", streams.single().headers["Referer"])
        assertEquals("application/x-mpegURL", streams.single().mimeType)
    }

    @Test
    fun catalogMapsEpisodesWithoutInventingPlayableUrls() = runBlocking {
        val runtime = FakeRuntime()
        val adapter = AniyomiCatalogAdapter(runtime, HostAnimeSource(42L, "Example", "en"))
        val match = adapter.searchAnime("Example Anime").single()

        val seasons = adapter.getSeasons(match)

        assertEquals(1, seasons.size)
        assertEquals(1, seasons.single().episodes.size)
        assertTrue(seasons.single().episodes.single().playback is com.animewave.app.EpisodePlayback.Unavailable)
        assertEquals("https://source.example/episode/1", seasons.single().episodes.single().pageUrl)
    }

    @Test
    fun rejectsStreamResolutionForEpisodeNotInCatalogue() = runBlocking {
        val runtime = FakeRuntime()
        val adapter = AniyomiCatalogAdapter(runtime, HostAnimeSource(42L, "Example", "en"))

        val error = runCatching { adapter.resolveEpisode("unseen-episode") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
    private class FakeRuntime : AniyomiHostRuntime {
        val queries = mutableListOf<String>()
        var videos: List<HostVideo> = listOf(HostVideo(url = "https://cdn.example/video.mp4"))

        override suspend fun installedSources() = listOf(HostAnimeSource(42L, "Example", "en"))

        override suspend fun search(sourceId: Long, query: String): List<HostAnimeMatch> {
            queries += query
            return listOf(HostAnimeMatch("anime-1", "Example Anime", "https://source.example/anime/1"))
        }

        override suspend fun seasons(sourceId: Long, animeKey: String) =
            listOf(HostAnimeSeason(animeKey, "Season 1", number = 1))

        override suspend fun episodes(sourceId: Long, animeKey: String) =
            listOf(HostAnimeEpisode("episode-1", "Episode 1", 1, null, "https://source.example/episode/1"))

        override suspend fun resolveVideos(sourceId: Long, episodeKey: String) = videos
    }
}
