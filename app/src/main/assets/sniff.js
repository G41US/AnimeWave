// Runs in every frame of a source's page: reports video playlists and files the player loads,
// whatever their address looks like, so Movio can play them in its own player.
(function () {
  if (window.__ms) return; window.__ms = 1;
  var B = window.MovioPlayer;
  if (!B) return;
  function abs(u) { try { return new URL(u, location.href).href; } catch (e) { return null; } }
  function report(u, ct, text) {
    u = abs(u); if (!u || u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return;
    try {
      if ((ct && /mpegurl/i.test(ct)) || (text && text.slice(0, 7) === '#EXTM3U') || /\.m3u8(\?|$|#)/i.test(u)) B.stream(u, location.href);
      else if (ct && /^video\/(mp4|webm)/i.test(ct)) B.video(u, location.href);
    } catch (e) {}
  }
  var of = window.fetch;
  if (of) window.fetch = function (input, init) {
    var u = typeof input === 'string' ? input : (input && input.url);
    return of.apply(this, arguments).then(function (r) {
      try {
        var ct = r.headers.get('content-type') || '';
        if (/mpegurl|^video\//i.test(ct) || /\.m3u8/i.test(u)) report(r.url || u, ct);
        else if (/text\/plain|^$/i.test(ct)) r.clone().text().then(function (t) { if (t.slice(0, 7) === '#EXTM3U') report(r.url || u, ct, t); }).catch(function () {});
      } catch (e) {}
      return r;
    });
  };
  var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) { this.__mu = u; return open.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function () {
    var x = this;
    x.addEventListener('load', function () {
      try {
        var ct = x.getResponseHeader('content-type') || '';
        var t = (x.responseType === '' || x.responseType === 'text') ? (x.responseText || '').slice(0, 16) : '';
        report(x.responseURL || x.__mu, ct, t);
      } catch (e) {}
    });
    return send.apply(this, arguments);
  };
  // Tell Movio when the page's own video starts, so it can show the page if the stream wasn't caught.
  document.addEventListener('playing', function (e) {
    try { if (e.target && e.target.tagName === 'VIDEO' && e.target.duration > 150) B.playing(); } catch (x) {}
  }, true);
  var d = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
  if (d && d.set) Object.defineProperty(HTMLMediaElement.prototype, 'src', {
    get: d.get, configurable: true,
    set: function (v) {
      if (v && !/^(blob|data):/.test(v)) { if (/\.m3u8/i.test(v)) report(v, 'application/x-mpegurl'); else try { B.video(abs(v), location.href); } catch (e) {} }
      return d.set.call(this, v);
    },
  });
})();
