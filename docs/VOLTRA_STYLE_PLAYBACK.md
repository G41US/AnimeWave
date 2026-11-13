# Voltra-style playback architecture for AnimeWave

## What the supplied recording establishes

The recording is a walkthrough of a static reverse-engineering report for Voltra TV APK 13.0, not a live recording of playback. It provides useful architectural evidence, but the report itself says that remote endpoints and provider availability were not runtime-tested.

The report describes these design patterns:

- Anime has a dedicated catalogue and playback-source subsystem rather than reusing the movie resolver.
- Playback resolution fans out to several configured providers concurrently, gathers candidate sources, normalizes them and removes duplicates.
- A playable source is a structured object, not just a URL: it can include request context, quality/type metadata and subtitle tracks.
- There are two distinct source paths: a provider may return a direct media stream, or it may return an embed page that the app's embedded player can handle.
- The native player uses Media3 and a custom networking layer, and can recover from failures by trying another known playable source.
- Playback history records media identity, episode/season, position and duration.
- Provider configuration is remote-configurable, allowing providers to be enabled, disabled and ordered without hardcoding every switch in the UI.
- The report also describes viewing-entitlement and device-registration systems. Those are separate product/backend features, not prerequisites for a basic lawful streaming player.

These are architectural observations from static APK analysis. They do not prove that every provider currently works, that every anime has a stream, or that the remote services are reachable today.

## AnimeWave implementation direction

### 1. Provider contract

Each provider adapter should have a stable ID, a display name, a documented or otherwise permitted discovery interface, episode matching, and an optional resolver. Provider discovery must be concurrent, bounded by timeouts, cancellable, and isolated so one failed provider does not cancel healthy providers.

### 2. Candidate model

A native candidate should carry:

- HTTPS media URI
- Provider and quality labels
- Supported media type (HLS, DASH or MP4)
- A narrowly allowlisted set of non-secret request headers where the source's documented interface requires them
- Subtitle tracks with language, label and MIME type
- Provider authorization/rights confirmation

Never store or log cookies, bearer tokens, passwords, private keys or other account credentials in the APK or playback history. Do not bypass DRM, paywalls, authentication, CAPTCHAs or publisher access controls.

### 3. Two playback modes

- **Native stream:** Use Media3/ExoPlayer for authorized direct HLS, DASH or MP4 streams.
- **Provider embed:** Open a provider's permitted HTTPS embed page in a restricted in-app WebView when that is the provider's supported playback method. Keep TLS validation enabled, restrict navigation and bridge exposure, and do not treat arbitrary intercepted network traffic as permission to play it natively.

### 4. Source selection and recovery

For an episode, collect all valid candidates, deduplicate by normalized media identity, rank by availability/quality/latency metadata when supplied, and present a source selector. If a native candidate fails, offer the next authorized candidate while preserving playback position where possible. Retry loops must be bounded and must not repeatedly hammer a provider.

### 5. Player experience

The target experience includes subtitle selection, quality selection when the stream exposes variants, next/previous episode, source switching, playback progress, Continue Watching, and Android TV D-pad navigation. The app should display a clear unavailable-source state instead of implying every title is guaranteed to stream.

## Work started in this branch

- Added request-header metadata to the provider stream model.
- Added strict filtering for native playback headers: only User-Agent, Referer, Origin, Accept and Accept-Language are forwarded; cookies, authorization and malformed values are dropped.
- Wired the filtered request context into Media3's HTTP data source.
- Kept HTTPS-only transport and disabled cross-protocol redirects.
- Added a unit test for the header allowlist.

This is playback infrastructure, not a completed provider integration. AniList remains the only built-in episode-listing adapter until additional provider adapters are implemented and verified.

## Completion criteria for the full streaming milestone

- At least two permitted provider adapters are integrated and independently tested.
- A single anime search can discover and group matching episodes from all enabled adapters.
- An episode can expose multiple playable source candidates.
- Native HLS/DASH/MP4 playback works with provider-approved request context.
- Embed playback stays inside AnimeWave where supported by the source.
- Source switching and bounded failover work without losing the episode selection.
- Subtitle tracks are discovered and selectable.
- Playback history and resume work for native streams; embedded-page progress is shown only when the source exposes a supported progress interface.
- CI tests pass and the app is exercised on both an Android phone and Android TV hardware.

No integration should be labelled working until it passes these checks against a real, permitted source.
