// Keeps the app and its voice engine on the phone so it opens and speaks Uzbek without internet.
const CACHE = "tarjimon-v4";
const FILES = ["./", "index.html", "manifest.json", "icon.svg", "icon-192.png", "icon-512.png", "uz-tts-worker.js",
  "vendor/transformers.min.js", "vendor/ort-wasm-simd-threaded.jsep.mjs", "vendor/ort-wasm-simd-threaded.jsep.wasm"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)));
  self.skipWaiting();
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith("tarjimon-") && k !== CACHE).map(k => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener("fetch", e => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return; // translation requests always go to the network
  if (url.pathname.includes("/models/")) return; // the voice model is stored by the voice engine itself
  e.respondWith(
    fetch(e.request).then(r => {
      if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
      return r;
    }).catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r || caches.match("index.html")))
  );
});
