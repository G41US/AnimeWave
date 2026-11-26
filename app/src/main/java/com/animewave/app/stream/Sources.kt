package com.animewave.app.stream

/**
 * A player site that streams any title by TMDB id. [web] sources only play in their own page
 * (shown straight away); the rest hand their stream to Movio's player. An empty [base] is
 * Movio's direct source.
 */
data class StreamSource(val name: String, val base: String, val web: Boolean = false, val anime: String? = null) {
    val direct get() = base.isEmpty()

    /**
     * Page for AniList title [id], episode [e]. Anime sources take the AniList id and episode
     * ([anime] is "sub" or "dub"); the others play by TMDB id, so the episode is first mapped to
     * TMDB's season/episode. Empty when this source can't have the title.
     */
    fun url(kind: Kind, id: Int, s: Int, e: Int): String {
        if (anime != null) return "$base/anime/$id/${if (kind == Kind.MOVIE) 1 else e}/$anime"
        val info = AnimeMap.cached(id) ?: return ""
        val tmdb = info.tmdb ?: return ""
        if (info.movie || kind == Kind.MOVIE) return "$base/movie/$tmdb" + query(movie = true)
        val (_, ts, te) = AnimeMap.tmdb(id, e) ?: return ""
        return when (name) {
            "Club" -> "$base/tv/$tmdb-$ts-$te"
            else -> "$base/tv/$tmdb/$ts/$te" + query(movie = false)
        }
    }

    private fun query(movie: Boolean) = when (name) {
        "Vix" -> "?autoplay=true"
        "Braflix" -> if (movie) "" else "?autonext=1&ds_lang=en"
        else -> ""
    }
}

/** Sources that tested working, clearest first (Movio player), then page-only ones (web player). */
object Sources {
    val all = listOf(
        // Anime sources: subtitled and English-dubbed versions, by AniList id.
        StreamSource("Wave", "https://vidnest.fun", anime = "sub"),
        StreamSource("Wave Dub", "https://vidnest.fun", anime = "dub"),
        StreamSource("Nero", "https://vidfast.pro"),
        StreamSource("Flixify", "https://vidflix.club"),
        StreamSource("Club", "https://moviesapi.to"),
        StreamSource("Cine", "https://cinesrc.st/embed"),
        StreamSource("Main", "https://player.vidzee.wtf/embed"),
        StreamSource("Vid", "https://embed.wplay.me/embed"),
        StreamSource("Peach", "https://peachify.net/embed"),
        StreamSource("Mist", "https://play.xpass.top/e"),
        StreamSource("Nest", "https://vidnest.fun"),
        StreamSource("Pass", "https://vidcore.net"),
        StreamSource("Azute", "https://vidrock.to"),
        StreamSource("Vidmux", "https://vidlux.site/embed"),
        StreamSource("Vix", "https://vixsrc.to"),
        StreamSource("Direct", ""),
        StreamSource("Vidsrc HD", "https://braflix.dev/embed", web = true),
        StreamSource("Vidads", "https://vidstuck.cc/embed", web = true),
        StreamSource("Braflix", "https://api.cineby.homes/embed", web = true),
        StreamSource("Vidind", "https://player.vidify.top/embed", web = true),
        StreamSource("Mistify", "https://vaplayer.ru/embed", web = true),
        StreamSource("Diablo", "https://vsembed.ru/embed", web = true),
        StreamSource("Vidsrc", "https://vidsrc2.ru/embed", web = true),
    )

    /** Tried in order when a source can't play a title (or plays the wrong one). */
    val fallbackOrder = all.indices.filter { !all[it].web && !all[it].direct && all[it].anime != "dub" }.take(9) + all.indices.filter { all[it].direct }

    fun index(name: String?) = all.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: 0
}
