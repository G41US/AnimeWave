/**
 * Explicit provider registry for authorized playback integrations.
 * No third-party providers are registered by default.
 */
export class PlaybackResolverProvider {
  constructor(id, displayName) {
    if (new.target === PlaybackResolverProvider) throw new TypeError("PlaybackResolverProvider is abstract");
    this.id = id;
    this.displayName = displayName;
  }

  async resolve(_input) {
    throw new Error("resolve() must be implemented by a provider");
  }
}

const providers = new Map();

export function registerResolverProvider(provider) {
  if (!provider?.id || typeof provider.resolve !== "function") {
    throw new TypeError("INVALID_PLAYBACK_PROVIDER");
  }
  providers.set(provider.id, provider);
}

export function unregisterResolverProvider(providerId) {
  providers.delete(providerId);
}

export function listResolverProviders() {
  return [...providers.values()].map(({ id, displayName }) => ({ id, displayName }));
}

export async function resolveWithProvider(providerId, input) {
  const provider = providers.get(providerId);
  if (!provider) throw new Error("PLAYBACK_PROVIDER_NOT_FOUND");
  return provider.resolve(input);
}

/** Public Mux demo asset used only for gateway/player compatibility tests. */
export class MuxHlsTestProvider extends PlaybackResolverProvider {
  constructor() {
    super("mux-hls-test", "Mux public HLS test asset");
  }

  async resolve(input = {}) {
    if (input.testMode !== true) throw new Error("EXPLICIT_TEST_MODE_REQUIRED");
    return {
      rightsConfirmed: true,
      mimeType: "application/vnd.apple.mpegurl",
      quality: "Auto",
      isM3U8: true,
      url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
      providerName: this.displayName,
    };
  }
}

/** Opt-in only; the default registry remains empty. */
export function registerMuxTestProvider() {
  registerResolverProvider(new MuxHlsTestProvider());
}
