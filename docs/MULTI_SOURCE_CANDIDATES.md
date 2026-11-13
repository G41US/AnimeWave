# Multi-source playback candidate engine

This milestone adds the engine layer that turns multiple listings for the same logical episode into a ranked set of verified playback candidates.

## What is implemented

- Resolves each provider listing in an episode group concurrently through the existing ProviderManager validation gate.
- Rejects candidates unless the provider marks the stream as authorized and the URL/MIME type passes validation.
- Deduplicates equivalent media URLs.
- Ranks candidates by declared quality, available subtitle tracks, and supported streaming format.
- Exposes deterministic next-untried candidate selection so a player controller can implement bounded failover without retrying the same URL forever.
- Unit tests cover ranking, URL deduplication, and exhausting the candidate queue.

## What is not yet implemented

- There are still no new live streaming-site adapters. AniList remains the only built-in provider.
- The candidate engine is not yet connected to a Compose source picker or automatic player retry UI.
- Subtitle discovery, quality switching, and next-episode autoplay still need UI/player integration.
- A provider must have a documented, permitted interface and explicitly authorized playback rights before it can return a native stream.

AnimePahe and other third-party anime aggregation sites are not treated as authorized stream providers by default. This project will not scrape or extract unauthorized streams, bypass DRM, account restrictions, CAPTCHAs, or regional controls. An external website can remain a user-opened destination, but a page URL is not a native media stream.

## Next integration steps

1. Add providers only after verifying their public API/SDK and terms permit the intended use.
2. Register those adapters during app startup.
3. Feed a ProviderEpisodeGroup into PlaybackCandidateEngine.resolve.
4. Present the ranked candidates in a source picker.
5. Pass the selected candidate's URL, safe headers, and subtitle tracks to Media3.
6. On a playback error, record the attempted URL and select the next untried candidate, with a small retry limit and user-visible status.
7. Validate actual playback on Android phones and Android TV devices in supported regions.