package com.animewave.app

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Display names and home pages for source choices. These are navigation entries, not
 * claims that a provider exposes an API or a direct stream. Native playback is enabled
 * only by OfficialPlaybackRegistry entries.
 */
data class AnimeSource(
    val id: String,
    val displayName: String,
    val homeUrl: String,
    val kind: Kind,
) {
    enum class Kind { LISTED_EPISODE, EXTERNAL_SITE }
}

object AnimeSourceRegistry {
    val sources = listOf(
        AnimeSource("listed", "AniList episodes", "https://anilist.co", AnimeSource.Kind.LISTED_EPISODE),
        AnimeSource("copon", "Copon", "https://copon.site/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anivault", "AniVault", "https://anivault.lol/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anineko", "AniNeko", "https://anineko.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animeheaven", "AnimeHeaven", "https://animeheaven.me/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("reanime", "ReAnime", "https://reanime.app/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anidb", "AniDB", "https://anidb.net/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animepahe", "AnimePahe", "https://animepahe.ru/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anikoto", "AniKoto", "https://anikoto.to/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animenexus", "AnimeNexus", "https://animenexus.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("miruro", "Miruro", "https://miruro.tv/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("mkissa", "Mkissa", "https://mkissa.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anichi", "AniChi", "https://anichi.to/", AnimeSource.Kind.EXTERNAL_SITE),
    )

    fun byId(id: String): AnimeSource = sources.firstOrNull { it.id == id } ?: sources.first()

    /**
     * Search-engine fallback scoped to the selected source host.
     * This is navigation only, not a provider integration or searchable API.
     */
    fun searchUrl(id: String, animeTitle: String, episodeTitle: String): String {
        val source = byId(id)
        if (source.kind == AnimeSource.Kind.LISTED_EPISODE) return source.homeUrl
        val host = runCatching { java.net.URI(source.homeUrl).host }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return source.homeUrl
        val query = "site:" + host + " " + animeTitle + " " + episodeTitle
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return "https://www.google.com/search?q=" + encoded
    }
}
