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
        AnimeSource("listed", "AniList metadata", "https://anilist.co", AnimeSource.Kind.LISTED_EPISODE),
        AnimeSource("copon", "Copon", "https://copon.site/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anivault", "AniVault", "https://anivault.lol/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anineko", "AniNeko", "https://anineko.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animeheaven", "AnimeHeaven", "https://animeheaven.me/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("reanime", "ReAnime", "https://reanime.app/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anidb", "AniDB", "https://anidb.net/", AnimeSource.Kind.EXTERNAL_SITE),
        // The previous .ru URL redirects to a parked domain in current checks.
        AnimeSource("animepahe", "AnimePahe", "https://animepahe.ng/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anikoto", "AniKoto", "https://anikoto.to/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("animenexus", "AnimeNexus", "https://animenexus.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("miruro", "Miruro", "https://miruro.tv/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("mkissa", "MKissa", "https://mkissa.com/", AnimeSource.Kind.EXTERNAL_SITE),
        AnimeSource("anichi", "Anichi", "https://anichi.to/", AnimeSource.Kind.EXTERNAL_SITE),
    )

    fun byId(id: String): AnimeSource = sources.firstOrNull { it.id == id } ?: sources.first()
}
