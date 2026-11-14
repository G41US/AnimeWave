import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import Player from "./Player.jsx";
import "./styles.css";

function App() {
  const [title, setTitle] = useState("");
  const [episodeId, setEpisodeId] = useState("");
  const [url, setUrl] = useState("");
  const [quality, setQuality] = useState("Auto");
  const [sessionId, setSessionId] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function createPlayback(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`${(import.meta.env.VITE_PLAYBACK_API || "").replace(/\/$/, "")}/api/sessions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title,
          episodeId,
          sources: [{ url, quality, isM3U8: /\.m3u8(?:$|[?#])/i.test(url) }]
        })
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.error || "Could not create playback session");
      setSessionId(body.id);
    } catch (reason) {
      setError(reason.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="app">
      {!sessionId ? (
        <form className="source-form" onSubmit={createPlayback}>
          <h1>AnimeWave Playback</h1>
          <p>Play a stream URL from a provider you are authorized to use.</p>
          <label>Anime title<input required value={title} onChange={event => setTitle(event.target.value)} /></label>
          <label>Episode identifier<input value={episodeId} onChange={event => setEpisodeId(event.target.value)} /></label>
          <label>HTTPS stream URL<input required type="url" placeholder="https://media.example.com/video.m3u8" value={url} onChange={event => setUrl(event.target.value)} /></label>
          <label>Quality<input value={quality} onChange={event => setQuality(event.target.value)} /></label>
          {error && <p className="error-text">{error}</p>}
          <button disabled={busy}>{busy ? "Preparing…" : "Start playback"}</button>
        </form>
      ) : (
        <>
          <Player sessionId={sessionId} />
          <button className="new-session" onClick={() => setSessionId("")}>New playback session</button>
        </>
      )}
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
