(function () {
  if (window.__appFeel) return; window.__appFeel = 1;
  var css = "*{-webkit-tap-highlight-color:transparent!important;-webkit-touch-callout:none!important}" +
    "html,body{-webkit-user-select:none;user-select:none;overscroll-behavior:none}" +
    "input,textarea,[contenteditable=true]{-webkit-user-select:text;user-select:text}" +
    "::-webkit-scrollbar{display:none!important;width:0!important;height:0!important}" +
    "html{scrollbar-width:none}";
  function addCss() {
    var host = document.head || document.documentElement;
    if (!host) return setTimeout(addCss, 10);
    if (document.getElementById('__app_css')) return;
    var s = document.createElement('style'); s.id = '__app_css'; s.textContent = css; host.appendChild(s);
  }
  function lockViewport() {
    var m = document.querySelector('meta[name=viewport]');
    if (!m) { m = document.createElement('meta'); m.name = 'viewport'; (document.head || document.documentElement).appendChild(m); }
    m.content = 'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';
  }
  addCss();
  document.addEventListener('DOMContentLoaded', function () { addCss(); lockViewport(); });
  document.addEventListener('contextmenu', function (e) {
    if (!(e.target.closest && e.target.closest('input,textarea'))) e.preventDefault();
  }, true);
  window.open = function () { return null; };
  // Links that try to open a new tab or window are ad redirects here.
  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('a[target=_blank], a[target=_new]');
    if (a) { e.preventDefault(); e.stopPropagation(); }
  }, true);
})();
