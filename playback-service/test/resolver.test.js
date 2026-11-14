import test from "node:test";
import assert from "node:assert/strict";
import {
  listResolverProviders,
  registerMuxTestProvider,
  resolveWithProvider,
  unregisterResolverProvider
} from "../src/resolver.js";

test("resolver registry starts empty", () => {
  assert.deepEqual(listResolverProviders(), []);
});

test("Mux HLS test provider requires explicit test mode", async () => {
  registerMuxTestProvider();
  try {
    await assert.rejects(
      resolveWithProvider("mux-hls-test", {}),
      /EXPLICIT_TEST_MODE_REQUIRED/
    );
    const result = await resolveWithProvider("mux-hls-test", { testMode: true });
    assert.equal(result.rightsConfirmed, true);
    assert.equal(result.isM3U8, true);
    assert.equal(result.url, "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
  } finally {
    unregisterResolverProvider("mux-hls-test");
  }
});
