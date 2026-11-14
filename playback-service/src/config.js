const split = value => (value || "").split(",").map(item => item.trim().toLowerCase()).filter(Boolean);

export const config = Object.freeze({
  port: Number(process.env.PORT || 3000),
  allowedHosts: split(process.env.STREAM_ALLOWED_HOSTS),
  corsOrigins: split(process.env.CORS_ORIGINS || "http://localhost:5173"),
  sessionTtlMs: Math.max(60_000, Number(process.env.SESSION_TTL_MS || 3_600_000)),
  maxSessionSources: Math.min(12, Math.max(1, Number(process.env.MAX_SESSION_SOURCES || 12))),
  maxJsonBytes: "256kb",
  maxRedirects: 5,
  upstreamTimeoutMs: 15_000
});

export function hostIsAllowed(hostname) {
  const host = hostname.toLowerCase();
  return config.allowedHosts.some(domain => host === domain || host.endsWith(`.${domain}`));
}
