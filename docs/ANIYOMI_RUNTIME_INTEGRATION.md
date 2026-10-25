# Aniyomi source runtime integration

## Current status

AnimeWave now has a Media3 playback boundary in `com.animewave.app.playback`.
`AnimeStreamResolver` is the seam between source resolution and playback, and
`ResolvedAnimeStream` carries the final URL plus request headers and MIME type.

This is **not yet an Aniyomi extension runtime**. Do not report arbitrary
Aniyomi APK support until the host runtime and extension ABI are integrated and
tested.

## Why the host modules cannot be added as ordinary Maven dependencies

Aniyomi's app wires internal Gradle modules such as `source-api`, `domain`,
`data`, and `core/common` from its own multi-module build. The extension API
library primarily provides compile-time stubs for extension authors; it is not
a drop-in implementation of the host's runtime. Copying only the extension
stubs or using `DexClassLoader` does not implement the host services expected
by extension instances.

Upstream references:
- https://github.com/aniyomiorg/aniyomi
- https://github.com/aniyomiorg/aniyomi-extensions
- https://unstable-extension-docs.aniyomi.org/

## Integration steps

1. Choose and pin one exact Aniyomi commit/tag. Record its extension-lib API
   version and license notices in the repository.
2. Vendor or include the required host modules from that exact revision as
   Gradle subprojects. Resolve their transitive dependencies and Android
   namespace/build plugin requirements rather than inventing Maven coordinates.
3. Implement the host-side extension manager: installed extension discovery,
   package/signature/version validation, class loading, source registration,
   extension context/services, and lifecycle/error handling. Never load an
   arbitrary APK from an untrusted URL without user consent and validation.
4. Implement an adapter that invokes the actual source API's anime search,
   episode listing, and video-link resolution methods. Map the upstream video
   model's URL and request headers into `ResolvedAnimeStream`.
5. Use `Media3StreamBridge.createMediaSource(stream)` so Referer/User-Agent
   and other required request headers reach Media3's HTTP data source.
6. Test against a known, legally accessible source and a sample extension built
   against the pinned API. Verify playback, redirects, HLS/DASH, headers,
   cancellation, and TV remote controls before advertising compatibility.

## Current adapter contract

Implement `AnimeStreamResolver.resolve(episodeUrl)` with the pinned host
runtime. Keep upstream API types inside that adapter; the UI/player should
continue to depend only on `ResolvedAnimeStream`.
