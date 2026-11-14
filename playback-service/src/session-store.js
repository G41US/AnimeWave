import { randomUUID } from "node:crypto";
import { config } from "./config.js";
import { validateStreamUrl } from "./url-policy.js";

const sessions = new Map();

function cleanExpired() {
  const now = Date.now();
  for (const [id, session] of sessions) {
    if (session.expiresAt <= now) sessions.delete(id);
  }
}

function assetIdFor(session, url) {
  const existing = session.assetIds.get(url);
  if (existing) return existing;
  const id = randomUUID().replaceAll("-", "");
  session.assetIds.set(url, id);
  session.assets.set(id, url);
  return id;
}

export function proxiedAssetUrl(sessionId, assetId) {
  return `/proxy/${encodeURIComponent(sessionId)}/${encodeURIComponent(assetId)}`;
}

export async function createSession(input) {
  cleanExpired();
  if (!input || !Array.isArray(input.sources) || input.sources.length === 0) {
    throw new Error("AT_LEAST_ONE_SOURCE_REQUIRED");
  }
  if (input.sources.length > config.maxSessionSources) {
    throw new Error("TOO_MANY_SOURCES");
  }

  const sources = [];
  for (const item of input.sources) {
    const url = await validateStreamUrl(item.url);
    const isM3U8 = item.isM3U8 === true || /\.m3u8(?:$|[?#])/i.test(url.href) || item.type === "hls";
    sources.push({
      url: url.href,
      quality: String(item.quality || "Auto").slice(0, 40),
      isM3U8
    });
  }

  const subtitles = [];
  for (const item of (Array.isArray(input.subtitles) ? input.subtitles : [])) {
    const url = await validateStreamUrl(item.url);
    subtitles.push({
      url: url.href,
      lang: String(item.lang || "und").slice(0, 12),
      label: String(item.label || "Subtitles").slice(0, 60)
    });
  }

  const id = randomUUID();
  const session = {
    id,
    title: String(input.title || "AnimeWave").slice(0, 160),
    episodeId: String(input.episodeId || "").slice(0, 160),
    expiresAt: Date.now() + config.sessionTtlMs,
    sources,
    subtitles,
    assets: new Map(),
    assetIds: new Map()
  };

  session.sources = session.sources.map(source => ({
    ...source,
    url: proxiedAssetUrl(id, assetIdFor(session, source.url))
  }));
  session.subtitles = session.subtitles.map(track => ({
    ...track,
    url: proxiedAssetUrl(id, assetIdFor(session, track.url))
  }));

  sessions.set(id, session);
  return publicSession(session);
}

export function publicSession(session) {
  return {
    id: session.id,
    title: session.title,
    episodeId: session.episodeId,
    expiresAt: new Date(session.expiresAt).toISOString(),
    sources: session.sources,
    subtitles: session.subtitles
  };
}

export function getSession(id) {
  cleanExpired();
  const session = sessions.get(id);
  if (!session) return null;
  return session;
}

export function getAsset(session, assetId) {
  return session?.assets.get(assetId) || null;
}

export function registerAsset(session, url) {
  const id = assetIdFor(session, url);
  return proxiedAssetUrl(session.id, id);
}

export function sessionCount() {
  cleanExpired();
  return sessions.size;
}
