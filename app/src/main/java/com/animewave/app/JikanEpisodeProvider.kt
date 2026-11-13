package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Jikan is a read-only MyAnimeList metadata mirror.
 * It contributes episode names/numbers only; it never returns playable media.
 */
class JikanEpisodeProvider : AnimeProvider {
    override val id = "jikan"
    override val displayName = "Jikan episode catalogue"

    override suspend fun searchEpisodes(anime: Anime): List<ProviderEpisode> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(anime.title, Charsets.UTF_8.name())
        val search = getJson("https://api.jikan.moe/v4/anime?q=$query&limit=5&sfw")
        val matches = search.optJSONArray("data") ?: return@withContext emptyList()
        val target = normalize(anime.title)
        var selectedId: Int? = null
        var selectedScore = 0

        for (i in 0 until matches.length()) {
            val item = matches.optJSONObject(i) ?: continue
            val titles = listOf(
                item.optString("title").orEmpty(),
                item.optString("title_english").orEmpty(),
                item.optString("title_japanese").orEmpty(),
            ).filter { it.isNotBlank() && it != "null" }
            val score = titles.maxOfOrNull { candidate ->
                val normalized = normalize(candidate)
                when {
                    normalized == target -> 3
                    normalized.contains(target) || target.contains(normalized) -> 1
                    else -> 0
                }
            } ?: 0
            if (score > selectedScore) {
                selectedId = item.optInt("mal_id").takeIf { it > 0 }
                selectedScore = score
            }
        }

        val malId = selectedId?.takeIf { selectedScore >= 2 } ?: return@withContext emptyList()
        val response = getJson("https://api.jikan.moe/v4/anime/$malId/episodes?page=1")
        val data = response.optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until data.length()) {
                val episode = data.optJSONObject(i) ?: continue
                val number = episode.optInt("mal_id").takeIf { it > 0 } ?: continue
                val title = episode.optString("title").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Episode $number"
                val thumbnail = episode.optJSONObject("images")
                    ?.optJSONObject("jpg")
                    ?.optString("image_url")
                    ?.takeIf { it.startsWith("https://") }
                add(
                    ProviderEpisode(
                        providerId = id,
                        providerName = displayName,
                        animeId = anime.id,
                        title = title,
                        episodeNumber = number,
                        thumbnail = thumbnail,
                        pageUrl = "https://myanimelist.net/anime/$malId",
                    )
                )
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AnimeWave/1.0 (Android)")
            if (connection.responseCode !in 200..299) return JSONObject()
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
