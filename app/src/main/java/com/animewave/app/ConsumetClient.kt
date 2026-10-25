package com.animewave.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Optional adapter for a user-configured, self-hosted Consumet-compatible API.
 * No public third-party instance is assumed or hard-coded.
 */
class ConsumetClient(
    baseUrl: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : AnimeSourceAdapter {
    private val base = normalizeBase(baseUrl)
    override val sourceId = "consumet"
    override val displayName = "Consumet API"

    override suspend fun searchAnime(title: String, alternateTitles: List<String>): List<SourceAnimeMatch> {
        val json = getJson("/anime/gogoanime/${Uri.encode(title)}")
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val item = results.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = item.optString("title").takeIf(String::isNotBlank) ?: return@mapNotNull null
            SourceAnimeMatch(
                sourceId = sourceId,
                sourceName = displayName,
                title = name,
                pageUrl = "$base/anime/gogoanime/info/${Uri.encode(id)}",
                year = item.optString("releaseDate").toIntOrNull(),
                episodeCount = null,
                seasonLabel = item.optString("subOrDub").takeIf(String::isNotBlank),
            )
        }
    }

    override suspend fun getSeasons(match: SourceAnimeMatch): List<AnimeSeason> {
        val id = match.pageUrl.substringAfterLast("/").takeIf(String::isNotBlank)
            ?: throw IOException("Missing API anime ID")
        val json = getJson("/anime/gogoanime/info/${Uri.encode(id)}")
        val episodes = json.optJSONArray("episodes") ?: return emptyList()
        val rows = (0 until episodes.length()).mapNotNull { i ->
            val item = episodes.optJSONObject(i) ?: return@mapNotNull null
            val episodeId = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val number = item.optInt("number").takeIf { it > 0 }
            SourceEpisode(
                id = "consumet:$episodeId",
                title = item.optString("title").takeIf(String::isNotBlank) ?: "Episode ${number ?: i + 1}",
                number = number,
                thumbnail = null,
                sourceId = sourceId,
                sourceName = displayName,
                pageUrl = match.pageUrl,
                playback = EpisodePlayback.Unavailable("Stream link is retrieved when you select this episode."),
            )
        }
        if (rows.isEmpty()) return emptyList()
        return listOf(
            AnimeSeason(
                id = "consumet:${match.title}",
                title = match.title,
                number = 1,
                year = match.year,
                format = match.seasonLabel,
                episodes = rows,
            )
        )
    }

    /** Returns the first direct HTTPS stream the API explicitly supplies for an episode. */
    suspend fun getPlayableUrl(episodeId: String): String {
        val json = getJson("/anime/gogoanime/watch/${Uri.encode(episodeId)}")
        val sources = json.optJSONArray("sources") ?: throw IOException("API returned no stream sources")
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val url = source.optString("url")
            val uri = runCatching { Uri.parse(url) }.getOrNull()
            if (uri?.scheme == "https" && !uri.host.isNullOrBlank()) return url
        }
        throw IOException("No direct HTTPS stream was returned for this episode")
    }

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(base + path)
            .header("Accept", "application/json")
            .header("User-Agent", "AnimeWave/1.0 (Android)")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Episode API returned HTTP ${response.code}")
            runCatching { JSONObject(body) }
                .getOrElse { throw IOException("Episode API returned invalid JSON") }
        }
    }

    companion object {
        fun normalizeBase(raw: String): String {
            val value = raw.trim().trimEnd('/')
            val uri = runCatching { Uri.parse(value) }.getOrNull()
            require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Enter the HTTPS base URL of your own Consumet-compatible API."
            }
            return value
        }
    }
}
