import dns from "node:dns/promises";
import net from "node:net";
import { hostIsAllowed } from "./config.js";

function isPublicAddress(address) {
  if (net.isIPv4(address)) {
    const [a, b] = address.split(".").map(Number);
    return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 198 && (b === 18 || b === 19)));
  }
  if (net.isIPv6(address)) {
    const value = address.toLowerCase();
    if (value.startsWith("::ffff:")) return isPublicAddress(value.slice(7));
    return !(value === "::" || value === "::1" || value.startsWith("fc") ||
      value.startsWith("fd") || value.startsWith("fe80:") ||
      value.startsWith("2001:db8:"));
  }
  return false;
}

export async function validateStreamUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error("INVALID_STREAM_URL");
  }

  if (url.protocol !== "https:" || url.username || url.password || (url.port && url.port !== "443")) {
    throw new Error("STREAM_URL_MUST_BE_PUBLIC_HTTPS");
  }
  if (!hostIsAllowed(url.hostname) || net.isIP(url.hostname)) {
    throw new Error("STREAM_HOST_NOT_ALLOWLISTED");
  }

  const addresses = await dns.lookup(url.hostname, { all: true, verbatim: true });
  if (!addresses.length || addresses.some(record => !isPublicAddress(record.address))) {
    throw new Error("STREAM_HOST_RESOLVES_TO_NON_PUBLIC_ADDRESS");
  }
  return url;
}

export function pinnedLookup(_url) {
  return (hostname, options, callback) => {
    dns.lookup(hostname, { all: true, verbatim: true }).then(records => {
      const record = records.find(item => isPublicAddress(item.address));
      if (!record) return callback(new Error("NO_PUBLIC_UPSTREAM_ADDRESS"));
      if (options?.all) return callback(null, [{ address: record.address, family: record.family }]);
      return callback(null, record.address, record.family);
    }).catch(callback);
  };
}
