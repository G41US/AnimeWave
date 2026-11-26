package com.animewave.app.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gets a title ready to play while the viewer is still on its page: subtitles, skip times,
 * runtime and the episode list are fetched in the background, so the player has nothing to wait for.
 */
object Prefetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val done = java.util.Collections.synchronizedSet(HashSet<String>())

    fun title(t: Title, season: Int, episode: Int) {
        val key = "${t.key}:$season:$episode"
        if (!done.add(key)) return
        scope.launch { runCatching { Subtitles.find(t.kind, t.id, season, episode) } }
        scope.launch { runCatching { Segments.find(t.kind, t.id, season, episode) } }
        scope.launch {
            runCatching {
                if (t.kind == Kind.TV) { Tmdb.episodes(t.id, season); Tmdb.seasonCount(t.id) } else Tmdb.details(t)
            }
        }
    }
}
