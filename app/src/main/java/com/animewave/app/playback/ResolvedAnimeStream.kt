package com.animewave.app.playback

/**
 * A stream resolved by a provider adapter and ready for the Media3 player.
 *
 * Keep provider-specific extension APIs behind an adapter; do not expose an
 * extension's implementation classes to the UI layer.
 */
data class ResolvedAnimeStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val title: String? = null,
)
