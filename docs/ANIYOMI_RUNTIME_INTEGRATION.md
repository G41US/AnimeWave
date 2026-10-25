# Aniyomi source runtime integration

## Pinned upstream target

The integration target is **Aniyomi v0.18.2.1**, with extension API major version 17. The version pin is recorded in `gradle/aniyomi-runtime.properties`. Upstream release notes state that v0.18.2.1 added extensions-lib v17 support. See [upstream release notes](https://aniyomi.org/changelogs/) and [source repository](https://github.com/aniyomiorg/aniyomi/tree/v0.18.2.1).

## Current implementation status

AnimeWave has a Media3 playback boundary in `com.animewave.app.playback`:

- `ResolvedAnimeStream` carries a resolved URL, request headers, MIME type, and title.
- `Media3StreamBridge` validates network URLs, forwards required HTTP headers, and creates Media3 media sources.
- `AnimeStreamResolver` defines the source-resolution interface.
- `ConsumetStreamResolver` adapts AnimeWave's existing user-configured Consumet-compatible API to the resolver interface.
- `Media3StreamBridgeTest` covers MIME type mapping and URL validation.

**The Aniyomi host runtime is not yet integrated.** The Consumet adapter is not an Aniyomi extension loader and does not make extension APKs installable or executable. Do not advertise arbitrary Aniyomi APK support until the host runtime and extension ABI are integrated and tested.

## Why the host modules cannot be added as ordinary Maven dependencies

Aniyomi's app wires internal Gradle modules such as `source-api`, `domain`, `data`, and `core/common` from its own multi-module build. The extension API library primarily provides compile-time stubs for extension authors; it is not a drop-in implementation of the host runtime. Copying only the extension stubs or using `DexClassLoader` does not implement the host services expected by extension instances.

Upstream references:
- https://github.com/aniyomiorg/aniyomi
- https://github.com/aniyomiorg/aniyomi-extensions
- https://extension-docs.aniyomi.org/

## Next steps for Aniyomi compatibility

1. Vendor or include the required host modules from the pinned revision as Gradle subprojects. Resolve their transitive dependencies and Android namespace/build plugin requirements instead of inventing Maven coordinates.
2. Implement the host-side extension manager: installed extension discovery, package/signature/version validation, class loading, source registration, extension context/services, and lifecycle/error handling. Require user consent before installing or loading an extension.
3. Implement an adapter that invokes the real source API's anime search, episode listing, and video-link resolution methods. Map the upstream video model's URL, request headers, and format into `ResolvedAnimeStream`.
4. Feed the resolved stream to `Media3StreamBridge.createMediaSource(stream)`, preserving required Referer/User-Agent headers.
5. Add a small sample extension built against the pinned API and test installation, loading, search, seasons, episodes, stream resolution, redirects, HLS/DASH, cancellation, and TV remote controls.
6. Run the repository's CI tests and Android build on the branch before marking this complete.

## Safety and compatibility notes

- Only load extensions from a repository the user explicitly trusts; validate package metadata and declared extension API compatibility.
- Treat extension code as third-party executable code, not as data. Do not silently download and execute arbitrary APKs.
- Aniyomi's upstream player uses a different playback stack; AnimeWave should reuse source resolution only and keep Media3 as its player.
- The app currently targets Android API 35 and minSdk 23. Any vendored modules must be checked for compatible SDK and Kotlin/Gradle requirements before integration.

## Resolver contract

Implement `AnimeStreamResolver.resolve(episodeId)` with the pinned host runtime. Keep upstream API types inside the adapter; the UI/player should depend only on `ResolvedAnimeStream`.
