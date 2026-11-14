import test from "node:test";
import assert from "node:assert/strict";

process.env.STREAM_ALLOWED_HOSTS = "media.example.com,cdn.example.net";
const { hostIsAllowed } = await import("../src/config.js");

test("host allowlist accepts exact hosts and their subdomains", () => {
  assert.equal(hostIsAllowed("media.example.com"), true);
  assert.equal(hostIsAllowed("edge.media.example.com"), true);
  assert.equal(hostIsAllowed("cdn.example.net"), true);
});

test("host allowlist rejects lookalikes and unrelated hosts", () => {
  assert.equal(hostIsAllowed("media.example.com.attacker.test"), false);
  assert.equal(hostIsAllowed("notexample.com"), false);
  assert.equal(hostIsAllowed("example.com"), false);
});
