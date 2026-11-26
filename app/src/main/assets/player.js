(function () {
  if (window.__mp || window.top !== window) return; window.__mp = 1;
  var bridge = window.MovioPlayer;
  function report(s) { try { bridge && bridge.state(s); } catch (e) {} }
  function css(id, text) {
    if (document.getElementById(id)) return;
    var s = document.createElement('style'); s.id = id; s.textContent = text;
    (document.head || document.documentElement).appendChild(s);
  }
  function isChallenge() {
    return !!window._cf_chl_opt || /just a moment/i.test(document.title) ||
      !!document.querySelector('#challenge-form, .cf-turnstile, [name=cf-turnstile-response]');
  }

  // Cloudflare's human check, restyled to look like part of the app.
  function challenge() {
    document.title = 'Movio';
    css('__mp_cf',
      "html,body{background:#07080D!important;font-family:system-ui,sans-serif!important}" +
      "h1,h2,p,img,footer,.footer,[role=contentinfo],.heading-favicon,.zone-name-title{display:none!important}" +
      ".main-wrapper,.main-content{margin:0!important;padding:0 24px!important;max-width:none!important}" +
      "body{display:flex!important;flex-direction:column!important;justify-content:center!important;align-items:center!important;min-height:100vh!important}" +
      "body::before{content:'Quick check';display:block;text-align:center;color:#fff;font:800 22px system-ui,sans-serif;margin-bottom:8px}" +
      "body::after{content:'Tap the box to start watching';display:block;text-align:center;color:#9EA1B3;font:500 14px system-ui,sans-serif;order:-1;margin-bottom:18px}" +
      ".main-content{display:flex!important;flex-direction:column!important;align-items:center!important}");
    report('challenge');
  }

  // Player-only view: the video fills the screen and nothing else on the page shows.
  css('__mp_focus',
    "html.mp-on,html.mp-on body{background:#000!important;overflow:hidden!important}" +
    "html.mp-on body *{visibility:hidden!important}" +
    "html.mp-on .mp-el,html.mp-on .mp-el *{visibility:visible!important}" +
    "html.mp-on .mp-el{position:fixed!important;inset:0!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;" +
    "max-width:none!important;max-height:none!important;margin:0!important;border:0!important;border-radius:0!important;" +
    "transform:none!important;z-index:2147483647!important;background:#000!important}" +
    "html.mp-on .mp-anc{transform:none!important;filter:none!important;contain:none!important;overflow:visible!important;perspective:none!important}");

  function area(el) { var r = el.getBoundingClientRect(); return r.width * r.height; }
  function findPlayer() {
    var best = null, bestArea = 0;
    document.querySelectorAll('iframe, video').forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (r.width < innerWidth * .3 || r.height < 120) return;
      var a = area(el);
      if (a > bestArea) { best = el; bestArea = a; }
    });
    return best;
  }

  var current = null;
  function focus() {
    if (window.__mpOff) return;
    var el = (current && document.contains(current)) ? current : findPlayer();
    if (!el) return;
    if (el !== current) {
      document.querySelectorAll('.mp-el').forEach(function (x) { x.classList.remove('mp-el'); });
      document.querySelectorAll('.mp-anc').forEach(function (x) { x.classList.remove('mp-anc'); });
      el.classList.add('mp-el');
      for (var p = el.parentElement; p && p !== document.body; p = p.parentElement) p.classList.add('mp-anc');
      current = el;
    }
    document.documentElement.classList.add('mp-on');
    report('player');
  }

  window.__mpToggle = function (on) {
    window.__mpOff = !on;
    if (on) focus(); else document.documentElement.classList.remove('mp-on');
  };

  // Ads that slip past the network blocklist: off-site banners, banner-sized frames, click-jacking overlays.
  function hide(el) { if (el) el.style.setProperty('display', 'none', 'important'); }
  function cleanAds() {
    var own = location.hostname.split('.').slice(-2).join('.');
    document.querySelectorAll('a[href]').forEach(function (a) {
      try {
        var h = new URL(a.href, location.href).hostname;
        if (h && h.slice(-own.length) !== own && a.querySelector('img')) hide(a);
      } catch (e) {}
    });
    document.querySelectorAll('iframe').forEach(function (f) {
      if (f === current) return;
      var r = f.getBoundingClientRect();
      if (r.height > 0 && r.height <= 130 && r.width > 150) hide(f);
    });
    document.querySelectorAll('body > div, body > a').forEach(function (el) {
      if (el.contains(current)) return;
      var cs = getComputedStyle(el);
      if (cs.position === 'fixed' && +cs.zIndex > 1000 && (parseFloat(cs.opacity) < .1 || el.children.length === 0)) hide(el);
    });
  }

  var pending = false;
  function run() {
    pending = false;
    if (!document.body) return;
    if (isChallenge()) { challenge(); return; }
    cleanAds();
    focus();
  }
  function schedule() { if (!pending) { pending = true; setTimeout(run, 150); } }
  document.addEventListener('DOMContentLoaded', function () {
    run();
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  });
  if (document.readyState !== 'loading') schedule();
  setInterval(schedule, 1500);
})();
