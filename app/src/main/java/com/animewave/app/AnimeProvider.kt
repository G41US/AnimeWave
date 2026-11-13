package com.animewave.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

data class ProviderEpisode(
    val providerId: String,
    val providerName: String,
    val animeId: Int,
    val title: String,
    val episodeNumber: Int?,
    val thumbnail: String?,
    val pageUrl: String,
)

data class ProviderSubtitle(
    val url: String,
    val language: String? = null,
    val label: String? = null,
    val mimeType: String? = null,
    val isDefault: Boolean = false,
)

data class ProviderStream(
    val url: String,
    val providerName: String,
    val rightsConfirmed: Boolean,
    val mimeType: String? = null,
    val quality: String? = null,
    val subtitles: List<ProviderSubtitle> = emptyList(),
)

interface AnimeProvider {
    val id: String
    val displayName: String
    suspend fun searchEpisodes(anime: Anime): List<ProviderEpisode>
    suspend fun resolve(episode: ProviderEpisode): ProviderStream? = null
}

data class ProviderStatus(
    val providerId: String,
    val providerName: String,
    val available: Boolean,
    val episodeCount: Int,
    val message: String? = null,
)

data class ProviderEpisodeGroup(
    val key: String,
    val episodeNumber: Int?,
    val title: String,
    val episodes: List<ProviderEpisode>,
)

data class ProviderDiscoveryResult(
    val groups: List<ProviderEpisodeGroup>,
    val providers: List<ProviderStatus>,
) {
    companion object {
        fun empty() = ProviderDiscoveryResult(emptyList(), emptyList())
    }
}

object EpisodeMatcher {
    private val episodePattern = Regex("""(?i)\b(?:episode|ep|e)\s*[-_.#]?\s*(\d{1,4})\b""")
    private val seasonPattern = Regex("""(?i)\b\d{1,2}x(\d{1,3})\b""")

    fun episodeNumber(title: String): Int? =
        episodePattern.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: seasonPattern.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun normalize(title: String): String = title.lowercase()
        .replace(episodePattern, " ")
        .replace(seasonPattern, " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")
}

class AniListStreamingProvider : AnimeProvider {
    override val id = "anilist"
    override val displayName = "AniList listed episodes"

    override suspend fun searchEpisodes(anime: Anime): List<ProviderEpisode> =
        anime.streamingEpisodes.map { ep ->
            ProviderEpisode(
                providerId = id,
                providerName = ep.site.ifBlank { displayName },
                animeId = anime.id,
                title = ep.title,
                episodeNumber = EpisodeMatcher.episodeNumber(ep.title),
                thumbnail = ep.thumbnail,
                pageUrl = ep.url,
            )
        }
}

object ProviderManager {
    private const val TIMEOUT_MS = 12_000L
    private val concurrency = Semaphore(3)
    private val adapters = CopyOnWriteArrayList<AnimeProvider>().apply {
        add(AniListStreamingProvider())
    }

    fun register(provider: AnimeProvider) {
        require(provider.id.isNotBlank())
        if (adapters.none { it.id == provider.id }) adapters.add(provider)
    }

    fun unregister(providerId: String) {
        if (providerId != "anilist") adapters.removeAll { it.id == providerId }
    }

    fun providers(): List<AnimeProvider> = adapters.toList()

    suspend fun discover(anime: Anime): ProviderDiscoveryResult = coroutineScope {
        val results = adapters.map { provider ->
            async(Dispatchers.IO) {
                concurrency.withPermit {
                    try {
                        val episodes = withTimeoutOrNull(TIMEOUT_MS) { provider.searchEpisodes(anime) }
                        val result: Pair<ProviderStatus, List<ProviderEpisode>> = if (episodes == null) {
                            ProviderStatus(provider.id, provider.displayName, false, 0, "Timed out") to emptyList()
                        } else {
                            val valid = episodes.filter {
                                it.animeId == anime.id && it.providerId == provider.id && safeHttps(it.pageUrl)
                            }
                            ProviderStatus(provider.id, provider.displayName, true, valid.size) to valid
                        }
                        result
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        ProviderStatus(provider.id, provider.displayName, false, 0, error.message ?: "Unavailable") to emptyList()
                    }
                }
            }
        }.awaitAll()

        val statuses = results.map { it.first }
        val episodes = results.flatMap { it.second }
        val groups = episodes.groupBy { ep ->
            ep.episodeNumber?.let { "episode:" + it } ?: "title:" + EpisodeMatcher.normalize(ep.title)
        }.map { (key, variants) ->
            val sorted = variants.sortedBy { it.providerName.lowercase() }
            ProviderEpisodeGroup(
                key = key,
                episodeNumber = sorted.firstNotNullOfOrNull { it.episodeNumber },
                title = sorted.first().title,
                episodes = sorted,
            )
        }.sortedWith(compareBy<ProviderEpisodeGroup> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })

        ProviderDiscoveryResult(groups, statuses)
    }

    suspend fun resolve(episode: ProviderEpisode): ProviderStream? {
        val provider = adapters.firstOrNull { it.id == episode.providerId } ?: return null
        val stream = withContext(Dispatchers.IO) { provider.resolve(episode) } ?: return null
        val mimeAllowed = stream.mimeType == null || stream.mimeType in setOf(
            "application/x-mpegURL", "application/vnd.apple.mpegurl", "application/dash+xml", "video/mp4",
        )
        return stream.takeIf { it.rightsConfirmed && safeHttps(it.url) && mimeAllowed }
    }

    private fun safeHttps(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
