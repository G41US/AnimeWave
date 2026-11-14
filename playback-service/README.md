# AnimeWave Playback Service

This service is a standalone Node.js/Express playback gateway and React player. It does not scrape streaming sites, discover copyrighted streams, or impersonate provider browsers. It accepts HTTPS stream and subtitle URLs from sources you are authorized to use, validates them against an explicit hostname allowlist, and proxies HLS manifests, nested playlists, segments, and subtitles through the service.

## Requirements

- Node.js 20 or newer
- An HTTPS media origin you are authorized to access
- A public deployment for the Express service if clients are outside your local network

## Run locally

```sh
cd playback-service
cp .env.example .env
```

Set `STREAM_ALLOWED_HOSTS` in `.env` to the exact media hostnames you control or are authorized to proxy. For example:

```env
PORT=3000
STREAM_ALLOWED_HOSTS=media.example.com,cdn.example.net
CORS_ORIGINS=http://localhost:5173
```

Then:

```sh
npm install
npm run check
npm test
npm run dev
```

In a second terminal:

```sh
npm run web:dev
```

Open http://localhost:5173. Enter a title and an authorized HTTPS MP4 or HLS URL. The server creates a short-lived playback session and returns only proxy URLs to the browser.

## API

- `GET /api/health` — service status and allowlist configuration state.
- `POST /api/sessions` — create a short-lived playback session.
- `GET /api/sessions/:id` — retrieve the session's proxy URLs.
- `GET /proxy/:sessionId/:assetId` — stream media through the allowlisted proxy.

Example request:

```json
{
  "title": "Example series",
  "episodeId": "episode-1",
  "sources": [
    {
      "url": "https://media.example.com/episode.m3u8",
      "quality": "1080p",
      "isM3U8": true
    }
  ],
  "subtitles": [
    {
      "url": "https://cdn.example.net/subtitles/en.vtt",
      "lang": "en",
      "label": "English"
    }
  ]
}
```

## Operational notes

- Only HTTPS URLs on `STREAM_ALLOWED_HOSTS` are accepted.
- DNS results are checked to prevent private-network and loopback access.
- Redirect targets are revalidated and redirects are capped.
- Browser requests never contact media origins directly; media URLs are rewritten to the proxy, including nested HLS playlist references.
- HTTP Range requests are forwarded for seeking.
- Sessions are held in process memory and expire automatically. Use a single service instance unless you add a shared session store.
- The allowlist must contain the hostnames used by every HLS segment and subtitle. A CDN using unrelated hostnames needs those hostnames added too.
- The React player is a web player. AnimeWave's existing Android app remains native Kotlin/Media3 and is not replaced by this React UI.

## Deployment (Render)

The gateway deploys from `render.yaml`; `STREAM_ALLOWED_HOSTS` and `CORS_ORIGINS` are set in the Render dashboard, not in the repo.

To enable the legal embed-test provider on Render, set `EMBED_TEST_ENABLE=true`.

> **Warning:** Do not set `ANIMEPAHE_ENABLE=true` on Render. Cloudflare blocks Render's IPs; once the AnimePahe provider is registered, Cloudflare will cause it to return `SERIES_NOT_FOUND` for every request on Render; until then the flag has no effect.
