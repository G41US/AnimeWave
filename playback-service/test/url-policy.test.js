import test from "node:test";
import assert from "node:assert/strict";

process.env.STREAM_ALLOWED_HOSTS = "media.example.com,cdn.example.net";
const { hostIsAllowed } = await import("../src/config.js");
const { validateStreamUrl } = await import("../src/url-policy.js");

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

test("stream URL validation rejects malformed URLs", async () => {
  await assert.rejects(validateStreamUrl("not a URL"), { message: "INVALID_STREAM_URL" });
});

test("stream URL validation requires HTTPS", async () => {
  await assert.rejects(validateStreamUrl("http://media.example.com/video.mp4"), {
    message: "STREAM_URL_MUST_BE_PUBLIC_HTTPS"
  });
});

test("stream URL validation rejects unapproved hosts before DNS lookup", async () => {
  await assert.rejects(validateStreamUrl("https://unapproved.example.org/video.mp4"), {
    message: "STREAM_HOST_NOT_ALLOWLISTED"
  });
});

test("stream URL validation rejects embedded credentials", async () => {
  await assert.rejects(validateStreamUrl("https://user:pass@media.example.com/video.mp4"), {
    message: "STREAM_URL_MUST_BE_PUBLIC_HTTPS"
  });
});
