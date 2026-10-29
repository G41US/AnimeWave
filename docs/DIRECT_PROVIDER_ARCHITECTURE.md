# AnimeWave direct-provider architecture

## Decision

AnimeWave will use native provider adapters inside the Android app. A separate AnimeWave backend is not required for this architecture, and the Aniyomi host runtime is optional rather than a prerequisite.

## Existing building blocks

- `AnimeSourceAdapter` and `EpisodeCatalogRepository` define the search and season/episode catalog boundary.
- `AniListListedEpisodeAdapter` provides metadata-linked episode entries; it does not establish video availability.
- `ConsumetClient` is an optional adapter for a user-configured Consumet-compatible API. It is not a direct provider integration and no public instance is assumed.
- `NativeScraper` parses publicly exposed HTML media elements and direct media links. It does not execute JavaScript or bypass access controls.
- `ResolvedAnimeStream`, `Media3StreamBridge`, and the existing player path handle verified direct media playback and request headers.

## Provider adapter contract

Each provider adapter should implement `AnimeSourceAdapter` and must:

1. Use a documented API or public pages the provider permits clients to access.
2. Return only titles and episode links actually observed in provider responses.
3. Keep provider-specific parsing isolated from UI and playback code.
4. Resolve only direct media URLs the provider permits AnimeWave to play; preserve documented request headers.
5. Enforce HTTPS, reject embedded credentials, use bounded timeouts, and handle rate limits and provider errors.
6. Never manufacture episode URLs, evade authentication/paywalls/DRM, or extract hidden stream URLs from protected players.

## Metadata and video availability

Metadata services such as AniList or TMDB can supply titles, artwork, descriptions, and episode metadata subject to their API terms. Metadata is not proof that a video stream exists or that the app is authorized to play it. A source adapter must independently confirm availability.

## Implementation order

1. Keep the current metadata and Media3 boundaries.
2. Add one concrete native adapter for a provider with a documented API or explicit permission for direct client access.
3. Add fixture-based unit tests for search, episode numbering, malformed responses, redirects, and empty results.
4. Connect its verified stream result to the existing Media3 bridge.
5. Add further providers one at a time, only after confirming their API/access terms and testing real responses.
6. Keep arbitrary Aniyomi APK execution disabled until a compatible host runtime has been fully integrated and tested.

## Current limitation

The repository currently has source navigation entries for several sites, but those entries are not working provider adapters. The optional Consumet client requires the user to configure an API base URL. Do not describe AnimeWave as having universal direct-provider playback until at least one concrete provider adapter passes integration tests on real provider responses.
