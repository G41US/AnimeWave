package com.animewave.app

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
        AnimeSource("listed", "Wave Source 1", "https://anilist.co", AnimeSource.Kind.LISTED_EPISODE),
        AnimeSource("copon", "Wave Source 2", "https://copon.site/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anivault", "Wave Source 3", "https://anivault.lol/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anineko", "Wave Source 4", "https://anineko.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animeheaven", "Wave Source 5", "https://animeheaven.me/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("reanime", "Wave Source 6", "https://reanime.app/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anidb", "Wave Source 7", "https://anidb.net/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animepahe", "Wave Source 8", "https://animepahe.ru/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anikoto", "Wave Source 9", "https://anikoto.to/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animenexus", "Wave Source 10", "https://animenexus.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("miruro", "Wave Source 11", "https://miruro.tv/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("mkissa", "Wave Source 12", "https://mkissa.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anichi", "Wave Source 13", "https://anichi.to/", AnimeSource.Kind.EXTERNAL_SITE),
    )

    fun byId(id: String): AnimeSource = sources.firstOrNull { it.id == id } ?: sources.first()
}
