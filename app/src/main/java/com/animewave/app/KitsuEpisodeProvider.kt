package com.animewave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Kitsu episode catalogue adapter. It supplies metadata, never playable media URLs. */
class KitsuEpisodeProvider : AnimeProvider {
    override val id = "kitsu"
    override val displayName = "Kitsu episode catalogue"

    override suspend fun searchEpisodes(anime: Anime): List<ProviderEpisode> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(anime.title, Charsets.UTF_8.name())
        val search = getJson("https://kitsu.io/api/edge/anime?filter%5Btext%5D=$query&page%5Blimit%5D=5")
        val matches = search.optJSONArray("data") ?: return@withContext emptyList()
        val target = anime.title.trim().lowercase()
        var selected: JSONObject? = null
        var selectedScore = 0
        for (i in 0 until matches.length()) {
            val item = matches.optJSONObject(i) ?: continue
            val attrs = item.optJSONObject("attributes") ?: continue
            val titles = listOf(attrs.optString("canonicalTitle"), attrs.optJSONObject("titles")?.optString("en"),
                attrs.optJSONObject("titles")?.optString("en_jp"), attrs.optJSONObject("titles")?.optString("ja_jp"))
                .filter { it.isNotBlank() && it != "null" }
            val score = titles.maxOfOrNull { title ->
                when {
                    title.trim().equals(target, true) -> 3
                    title.contains(target, true) || target.contains(title, true) -> 1
                    else -> 0
                }
            } ?: 0
            if (score > selectedScore) { selected = item; selectedScore = score }
        }
        val record = selected?.takeIf { selectedScore >= 2 } ?: return@withContext emptyList()
        val recordId = record.optString("id")
        val slug = record.optJSONObject("attributes")?.optString("slug").orEmpty()
        if (recordId.isBlank()) return@withContext emptyList()
        val response = getJson("https://kitsu.io/api/edge/anime/$recordId/episodes?page%5Blimit%5D=20&sort=number")
        val data = response.optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until data.length()) {
                val episode = data.optJSONObject(i) ?: continue
                val attrs = episode.optJSONObject("attributes") ?: continue
                val number = attrs.optInt("number").takeIf { it > 0 }
                val title = attrs.optString("canonicalTitle").takeIf { it.isNotBlank() && it != "null" }
                    ?: number?.let { "Episode $it" } ?: continue
                val page = if (slug.isNotBlank() && number != null) "https://kitsu.io/anime/$slug/episodes/$number"
                    else "https://kitsu.io/anime/$slug"
                add(ProviderEpisode(id, displayName, anime.id, title, number,
                    attrs.optJSONObject("thumbnail")?.optString("original")?.takeIf { it.startsWith("https://") }, page))
            }
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.api+json")
            connection.setRequestProperty("User-Agent", "AnimeWave/1.0 (Android)")
            if (connection.responseCode !in 200..299) return JSONObject()
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}
