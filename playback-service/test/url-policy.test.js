import test from "node:test";
import assert from "node:assert/strict";
import { hostIsAllowed } from "../src/config.js";

test("host allowlist matches exact hosts and subdomains only", () => {
  assert.equal(hostIsAllowed("media.example.com"), false);
  assert.equal(hostIsAllowed("sub.media.example.com"), false);
});
