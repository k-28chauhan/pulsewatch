// Applies the saved theme before first paint so there is no light flash in dark mode.
try {
  var t = localStorage.getItem('pulsewatch.theme');
  if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
} catch (e) {}
