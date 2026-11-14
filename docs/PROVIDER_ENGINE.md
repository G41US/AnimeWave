# AnimeWave provider and playback architecture

## Current implementation

- `AnimeProvider` defines the provider contract.
- `ProviderManager` runs registered adapters with a maximum of three concurrent lookups and a 12-second per-provider timeout.
- `AniListStreamingProvider` adapts AniList's public `streamingEpisodes` metadata into the common episode model.
- `KitsuEpisodeProvider` and `JikanEpisodeProvider` add catalogue metadata (episode titles, numbers and artwork); neither resolves media streams.
- `InternetArchiveProvider` searches public archive metadata and only offers native playback candidates when an item declares an allow-listed open license.
- `EpisodeMatcher` extracts season and episode identities from S02E03, Season 2 Episode 3, 2x03, Episode 2 and similar names.
- `ProviderManager.discover` groups equivalent listings by season and episode, preserves provider variants, and deduplicates repeated records without collapsing different episodes that share one series page URL.
- Subtitle configuration accepts only SRT, WebVTT and ASS/SSA MIME types; playback history accepts only valid HTTPS or content URIs.
- `PlaybackResolver` is the single gate for native playback. A provider must explicitly mark a stream as authorized, return HTTPS, and use a supported media type.
- `SubtitleManager` maps SRT, WebVTT and ASS/SSA tracks into Media3 subtitle configurations.
- `PlaybackHistoryStore` persists the last position for supported native streams and user-selected content URIs. Completed items are removed from Continue Watching.

## Adding a provider

Implement `AnimeProvider` in a new adapter file:

1. Give it a stable, unique `id` and a human-readable `displayName`.
2. Implement `searchEpisodes(anime)` using a documented API or a source whose terms and permissions allow this use.
3. Return `ProviderEpisode` values with the matching AniList anime ID, a stable episode title/number, and an HTTPS episode page URL.
4. Implement `resolve(episode)` only when the publisher explicitly permits native playback. Return a `ProviderStream` with `rightsConfirmed = true`, a direct HTTPS media URL, an allowed MIME type, and any permitted subtitle tracks.
5. Register the adapter with `ProviderManager.register(MyProvider())` during application startup.

External source search is only a convenience for locating a title on the selected site. It does not scrape pages, resolve streams, or guarantee that a search result exists. Do not treat an episode webpage as a direct media URL. Do not add private keys, cookies, account tokens, or embedded decryption secrets to the APK. Do not bypass access controls or publisher restrictions. A provider that only supplies a webpage should return no native stream; AnimeWave can still open the HTTPS page in its embedded browser.

## Current provider coverage

AniList, Kitsu and Jikan are active metadata/catalogue adapters. Internet Archive is an active, license-filtered catalogue adapter and may resolve direct media only for items with an allow-listed open license. These integrations do not provide AnimePahe, GogoAnime, HiAnime or other unofficial-site scraping. The named entries in `AnimeSourceRegistry` remain external website shortcuts, not working API integrations. They must not be presented as automatically searchable streaming providers.

## Testing

Run:

```sh
gradle testDebugUnitTest --tests "com.animewave.app.ParsingTest" --tests "com.animewave.app.ProviderEngineTest"
gradle assembleDebug
```

The GitHub Actions workflow also builds the release APK on pull requests to `main`.
