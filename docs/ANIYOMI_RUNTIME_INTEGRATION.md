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

## First runtime code milestone: package preflight

Added `com.animewave.app.aniyomi.AniyomiExtensionInspector` to inspect installed packages that declare the official anime-extension feature. It reports extension name, package/version, extension-lib version, declared source entry points, and SHA-256 signer fingerprints. It rejects candidates with missing version metadata, missing signatures, unsupported library versions, or missing entry points before any class loading is attempted. The Android manifest declares package visibility for this sideloaded Android TV use case.

**This is discovery/preflight, not extension execution.** A candidate marked `REQUIRES_TRUST_AND_HOST` is not loaded. The trust store and exact runtime ABI must be present before loading. In particular, the pinned Aniyomi v0.18.2.1 host supports extension-lib v17, but a metadata inspector alone cannot implement that ABI.

## Upstream loader audit (v0.18.2.1)

The pinned upstream host's real loading path has now been traced to these files:

- `app/src/main/java/eu/kanade/tachiyomi/extension/anime/AnimeExtensionManager.kt`: initializes installed extensions through `AnimeExtensionLoader.loadExtensions(context)`, separates successful and untrusted results, and only registers an untrusted extension after an explicit trust action.
- `app/src/main/java/eu/kanade/tachiyomi/extension/anime/util/AnimeExtensionLoader.kt`: responsible for package inspection, class loading, and constructing extension instances.
- `app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt`: observes installed extensions and registers their sources with the source repository.
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeSourceFactory.kt`: extension entry point; factories create the anime source instances.

These are implementation entry points, not standalone drop-in classes: the manager depends on Injekt, preferences, extension trust persistence, package install receivers, source repositories, and other host services. The correct next milestone is to port the loader plus the minimum required host dependency graph and source registration lifecycle together, not to write a speculative class loader around the API stubs.

## Runtime integration spike (in progress)

The repository currently does not contain the upstream host modules or a compatible extension ABI. Do not add guessed Maven coordinates or point the build at a moving upstream branch. The first implementation milestone is to pin and vendor the exact upstream host sources and build configuration, then validate the extension ABI in both debug and minified release builds.

Before enabling extension installation, add a compatibility gate that checks package identity, extension API version, signatures/trust policy, and required host classes. Only user-selected, explicitly trusted extension packages should be loaded. Extension execution is third-party code and must be treated accordingly.

A useful independent reference implementation is [Watchbox-Android](https://github.com/Nicartjay/Watchbox-Android), which documents ABI compatibility, shrinker keep rules, and version constraints. It is not an upstream Aniyomi dependency and must not be copied blindly; use it as a checklist for risks to verify against the pinned v0.18.2.1 source.

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
