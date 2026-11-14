import React, { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import Plyr from "plyr";
import "plyr/dist/plyr.css";

const API = (import.meta.env.VITE_PLAYBACK_API || "").replace(/\/$/, "");

export default function Player({ sessionId }) {
  const videoRef = useRef(null);
  const hlsRef = useRef(null);
  const plyrRef = useRef(null);
  const [session, setSession] = useState(null);
  const [sourceIndex, setSourceIndex] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    fetch(`${API}/api/sessions/${encodeURIComponent(sessionId)}`, { signal: controller.signal })
      .then(async response => {
        const body = await response.json();
        if (!response.ok) throw new Error(body.error || "Unable to load playback session");
        return body;
      })
      .then(body => { setSession(body); setSourceIndex(0); setError(""); })
      .catch(reason => { if (reason.name !== "AbortError") setError(reason.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [sessionId]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !session?.sources?.length) return undefined;

    let hls;
    let plyr;
    let disposed = false;
    const source = session.sources[sourceIndex];

    const nextSource = () => {
      if (disposed) return;
      if (sourceIndex + 1 < session.sources.length) setSourceIndex(index => index + 1);
      else setError("Every supplied source failed. Check the source URLs and try again.");
    };

    const fail = () => { if (!disposed) nextSource(); };
    const ready = () => { if (!disposed) setLoading(false); };

    video.addEventListener("error", fail);
    video.addEventListener("loadeddata", ready, { once: true });

    for (const track of session.subtitles || []) {
      const element = document.createElement("track");
      element.kind = "subtitles";
      element.src = track.url;
      element.srclang = track.lang || "und";
      element.label = track.label || "Subtitles";
      video.appendChild(element);
    }

    if (source.isM3U8 && Hls.isSupported()) {
      hls = new Hls({ enableWorker: true, maxBufferLength: 30, backBufferLength: 60 });
      hlsRef.current = hls;
      hls.on(Hls.Events.ERROR, (_event, data) => { if (data.fatal) fail(); });
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (disposed) return;
        plyr = new Plyr(video, {
          controls: ["play-large", "play", "progress", "current-time", "duration", "mute", "volume", "captions", "settings", "fullscreen"],
          settings: ["captions", "quality", "speed"],
          speed: { selected: 1, options: [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2] }
        });
        plyrRef.current = plyr;
        setLoading(false);
      });
      hls.loadSource(source.url);
      hls.attachMedia(video);
    } else {
      video.src = source.url;
      plyr = new Plyr(video, {
        controls: ["play-large", "play", "progress", "current-time", "duration", "mute", "volume", "captions", "settings", "fullscreen"],
        settings: ["captions", "speed"],
        speed: { selected: 1, options: [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2] }
      });
      plyrRef.current = plyr;
      video.load();
    }

    return () => {
      disposed = true;
      video.removeEventListener("error", fail);
      video.removeEventListener("loadeddata", ready);
      if (hls) hls.destroy();
      if (plyr) plyr.destroy();
      if (hlsRef.current === hls) hlsRef.current = null;
      if (plyrRef.current === plyr) plyrRef.current = null;
      video.pause();
      video.removeAttribute("src");
      video.querySelectorAll("track").forEach(track => track.remove());
      video.load();
    };
  }, [session, sourceIndex]);

  return (
    <section className="player-shell">
      <header className="player-heading">
        <div><h1>{session?.title || "AnimeWave Player"}</h1><p>{session?.episodeId || "Playback session"}</p></div>
        <span>{session ? `Source ${sourceIndex + 1}/${session.sources.length}` : " "}</span>
      </header>
      <div className="video-frame">
        <video ref={videoRef} playsInline controls crossOrigin="anonymous" preload="metadata" />
        {loading && <div className="player-status">Preparing stream…</div>}
        {error && <div className="player-error"><p>{error}</p><button onClick={() => { setError(""); setSourceIndex(0); }}>Retry sources</button></div>}
      </div>
    </section>
  );
}
