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
    val seasonNumber: Int? = null,
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
    /** Non-secret HTTP metadata supplied by an authorized provider integration. Never persist or log. */
    val requestHeaders: Map<String, String> = emptyMap(),
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
    val seasonNumber: Int? = null,
)

data class ProviderDiscoveryResult(
    val groups: List<ProviderEpisodeGroup>,
    val providers: List<ProviderStatus>,
) {
    companion object {
        fun empty() = ProviderDiscoveryResult(emptyList(), emptyList())
    }
}

data class EpisodeIdentity(
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

object EpisodeMatcher {
    private val seasonEpisodePattern =
        Regex("""(?i)\bS(?:eason)?\s*(\d{1,2})\s*[-_. ]*\s*E(?:pisode)?\s*(\d{1,3})\b""")
    private val seasonXPattern = Regex("""(?i)\b(\d{1,2})\s*x\s*(\d{1,3})\b""")
    private val episodePattern = Regex("""(?i)\b(?:episode|ep|e)\s*[-_.#]?\s*(\d{1,4})\b""")
    private val seasonOnlyPattern = Regex("""(?i)\bseason\s*\d{1,2}\b""")

    fun identity(title: String): EpisodeIdentity {
        val seasonEpisode = seasonEpisodePattern.find(title)
        if (seasonEpisode != null) return EpisodeIdentity(
            seasonEpisode.groupValues[1].toIntOrNull(),
            seasonEpisode.groupValues[2].toIntOrNull(),
        )
        val seasonX = seasonXPattern.find(title)
        if (seasonX != null) return EpisodeIdentity(
            seasonX.groupValues[1].toIntOrNull(),
            seasonX.groupValues[2].toIntOrNull(),
        )
        return EpisodeIdentity(null, episodePattern.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull())
    }

    fun seasonNumber(title: String): Int? = identity(title).seasonNumber
    fun episodeNumber(title: String): Int? = identity(title).episodeNumber

    fun normalize(title: String): String = title.lowercase()
        .replace(seasonEpisodePattern, " ")
        .replace(seasonXPattern, " ")
        .replace(episodePattern, " ")
        .replace(seasonOnlyPattern, " ")
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
        add(KitsuEpisodeProvider())
        add(InternetArchiveProvider())
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
            .map { episode ->
                val identity = EpisodeMatcher.identity(episode.title)
                episode.copy(
                    seasonNumber = episode.seasonNumber ?: identity.seasonNumber,
                    episodeNumber = episode.episodeNumber ?: identity.episodeNumber,
                )
            }
            .distinctBy { it.providerId + "|" + it.pageUrl.lowercase() }
        val groups = episodes.groupBy { ep ->
            val season = ep.seasonNumber?.toString() ?: "unknown"
            val number = ep.episodeNumber
            if (number != null) "season:$season:episode:$number"
            else {
                val normalizedTitle = EpisodeMatcher.normalize(ep.title)
                "season:$season:title:" + normalizedTitle.ifBlank { ep.pageUrl.lowercase() }
            }
        }.map { (key, variants) ->
            val sorted = variants.sortedBy { it.providerName.lowercase() }
            ProviderEpisodeGroup(
                key = key,
                episodeNumber = sorted.firstNotNullOfOrNull { it.episodeNumber },
                title = sorted.first().title,
                episodes = sorted,
                seasonNumber = sorted.firstNotNullOfOrNull { it.seasonNumber },
            )
        }.sortedWith(
            compareBy<ProviderEpisodeGroup> { it.seasonNumber ?: Int.MAX_VALUE }
                .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )

        ProviderDiscoveryResult(groups, statuses)
    }

    suspend fun resolve(episode: ProviderEpisode): ProviderStream? {
        val provider = adapters.firstOrNull { it.id == episode.providerId } ?: return null
        val stream = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                withContext(Dispatchers.IO) { provider.resolve(episode) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        val allowedMimeTypes = setOf(
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/dash+xml",
            "video/mp4",
        )
        val normalizedMime = stream.mimeType?.substringBefore(';')?.trim()?.lowercase()
        val mimeAllowed = normalizedMime == null || normalizedMime in allowedMimeTypes
        if (!stream.rightsConfirmed || !safeHttps(stream.url) || !mimeAllowed) return null
        val safeHeaders = sanitizeRequestHeaders(stream.requestHeaders)
        return stream.copy(requestHeaders = safeHeaders)
    }

    /**
     * Only harmless request context is accepted. Cookies, authorization credentials and
     * arbitrary headers are deliberately excluded; integrations must use permitted public
     * playback interfaces and must not smuggle account secrets into the player.
     */
    private fun sanitizeRequestHeaders(headers: Map<String, String>): Map<String, String> {
        val allowed = setOf("user-agent", "referer", "origin", "accept", "accept-language")
        return headers.entries.mapNotNull { (name, value) ->
            val key = name.trim().lowercase()
            val cleanValue = value.trim()
            if (key in allowed && cleanValue.isNotEmpty() && cleanValue.length <= 2048 &&
                !cleanValue.contains('\r') && !cleanValue.contains('\n')
            ) name.trim() to cleanValue else null
        }.toMap()
    }

    private fun safeHttps(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
