// Applies the saved colour theme BEFORE React renders, so there's no flash of the wrong
// theme on load. This lives in an external file (rather than an inline <script> in
// index.html) so our Content-Security-Policy can use a strict `script-src 'self'` with no
// 'unsafe-inline'. It's loaded synchronously in <head>, so it runs before the first paint.
// Defaults to dark if the user hasn't chosen a theme yet.
(function () {
  var theme = localStorage.getItem('theme') || 'dark';
  if (theme === 'dark') document.documentElement.classList.add('dark');
})();
