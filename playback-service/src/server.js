import express from "express";
import cors from "cors";
import axios from "axios";
import { config } from "./config.js";
import { validateStreamUrl, pinnedLookup } from "./url-policy.js";
import { createSession, getSession, getAsset, publicSession, registerAsset, sessionCount } from "./session-store.js";

const app = express();
app.disable("x-powered-by");
app.use(cors({
  origin(origin, callback) {
    if (!origin || config.corsOrigins.includes("*") || config.corsOrigins.includes(origin.toLowerCase())) return callback(null, true);
    return callback(new Error("CORS_ORIGIN_NOT_ALLOWED"));
  },
  exposedHeaders: ["Content-Length", "Content-Range", "Accept-Ranges"]
}));
app.use(express.json({ limit: config.maxJsonBytes }));

function fail(res, error, status = 400) {
  res.status(status).json({ error: error.message || "REQUEST_FAILED" });
}

function rewriteManifest(text, baseUrl, session) {
  const rewrite = raw => {
    const absolute = new URL(raw, baseUrl).href;
    return registerAsset(session, absolute);
  };
  return text.split(/\r?\n/).map(line => {
    const trimmed = line.trim();
    if (!trimmed) return line;
    if (trimmed.startsWith("#")) {
      return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${rewrite(uri)}"`);
    }
    return rewrite(trimmed);
  }).join("\n");
}

async function upstreamGet(url, rangeHeader) {
  let current = await validateStreamUrl(url);
  for (let redirect = 0; redirect <= config.maxRedirects; redirect += 1) {
    const response = await axios.get(current.href, {
      responseType: "stream",
      timeout: config.upstreamTimeoutMs,
      maxRedirects: 0,
      validateStatus: status => status >= 200 && status < 400,
      headers: {
        "User-Agent": "AnimeWave/1.0 authorized-media-player",
        Accept: "*/*",
        ...(rangeHeader ? { Range: rangeHeader } : {})
      },
      lookup: pinnedLookup(current)
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      response.data.destroy();
      const location = response.headers.location;
      if (!location || redirect === config.maxRedirects) throw new Error("UPSTREAM_REDIRECT_LIMIT");
      current = await validateStreamUrl(new URL(location, current).href);
      continue;
    }
    return { response, finalUrl: current.href };
  }
  throw new Error("UPSTREAM_REDIRECT_LIMIT");
}

app.get("/api/health", (_req, res) => {
  res.json({
    status: "ok",
    streamHostAllowlistConfigured: config.allowedHosts.length > 0,
    activeSessions: sessionCount()
  });
});

// Safe operational detail: hostnames are configuration identifiers, not credentials.
// Never return environment variables, tokens, or upstream URLs from this endpoint.
app.get("/api/health/detail", (_req, res) => {
  const expectedHosts = ["commondatastorage.googleapis.com", "test-streams.mux.dev"];
  res.json({
    status: "ok",
    allowedHosts: [...config.allowedHosts],
    expectedHostChecks: Object.fromEntries(expectedHosts.map(host => [host, config.allowedHosts.includes(host)])),
    activeSessions: sessionCount()
  });
});

app.post("/api/sessions", async (req, res) => {
  try {
    const session = await createSession(req.body);
    res.status(201).json(session);
  } catch (error) {
    fail(res, error, 422);
  }
});

app.get("/api/sessions/:id", (req, res) => {
  const session = getSession(req.params.id);
  if (!session) return fail(res, new Error("SESSION_NOT_FOUND_OR_EXPIRED"), 404);
  res.json(publicSession(session));
});

app.get("/proxy/:sessionId/:assetId", async (req, res) => {
  const session = getSession(req.params.sessionId);
  const url = getAsset(session, req.params.assetId);
  if (!session || !url) return fail(res, new Error("MEDIA_ASSET_NOT_FOUND"), 404);

  try {
    const { response, finalUrl } = await upstreamGet(url, req.headers.range);
    const contentType = String(response.headers["content-type"] || "application/octet-stream");
    const isManifest = /mpegurl|vnd\.apple\.mpegurl/i.test(contentType) || /\.m3u8(?:$|[?#])/i.test(finalUrl);

    res.status(response.status === 206 ? 206 : 200);
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Accept-Ranges", response.headers["accept-ranges"] || "bytes");
    if (response.headers["content-range"]) res.setHeader("Content-Range", response.headers["content-range"]);

    if (isManifest) {
      const chunks = [];
      response.data.on("data", chunk => chunks.push(chunk));
      response.data.on("end", () => {
        try {
          res.type("application/vnd.apple.mpegurl").send(rewriteManifest(Buffer.concat(chunks).toString("utf8"), finalUrl, session));
        } catch (error) {
          fail(res, error, 502);
        }
      });
      response.data.on("error", error => fail(res, error, 502));
      return;
    }

    if (response.headers["content-length"]) res.setHeader("Content-Length", response.headers["content-length"]);
    res.setHeader("Content-Type", contentType);
    response.data.on("error", error => {
      if (!res.headersSent) fail(res, error, 502);
      else res.destroy(error);
    });
    response.data.pipe(res);
  } catch (error) {
    fail(res, error, 502);
  }
});

app.use((error, _req, res, _next) => {
  fail(res, error, error.message === "CORS_ORIGIN_NOT_ALLOWED" ? 403 : 500);
});

app.listen(config.port, () => {
  process.stdout.write(`AnimeWave authorized playback API listening on :${config.port}\n`);
});

export { app };
