package com.animewave.app.playback

/**
 * Boundary between source integrations and Media3.
 *
 * A verified Aniyomi adapter can implement this interface. Aniyomi extension
 * APKs require a compatible host runtime and exact source-api ABI; an arbitrary
 * extension APK is not safely usable through DexClassLoader alone.
 */
interface AnimeStreamResolver {
    suspend fun resolve(episodeUrl: String): ResolvedAnimeStream
}
