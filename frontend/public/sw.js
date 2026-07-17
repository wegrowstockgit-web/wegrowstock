const CACHE_NAME = 'invsys-shell-v2';
const MEDIA_CACHE = 'invsys-media-v1';
const SHELL_ASSETS = ['/', '/index.html', '/manifest.json', '/favicon.svg'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  const keep = new Set([CACHE_NAME, MEDIA_CACHE]);
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => !keep.has(k)).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

function isMediaAsset(url) {
  return url.pathname.startsWith('/api/v1/media/');
}

/** Cache-first for catalog images / QC attachments (floor Wi-Fi dead zones). */
async function mediaCacheFirst(request) {
  const cache = await caches.open(MEDIA_CACHE);
  const cached = await cache.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (response.ok) {
    await cache.put(request, response.clone());
  }
  return response;
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  // Ignore browser extensions and other non-HTTP(S) schemes.
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return;

  if (isMediaAsset(url)) {
    event.respondWith(mediaCacheFirst(request));
    return;
  }

  // Mutating / dynamic API traffic must never be shell-cached.
  if (url.pathname.startsWith('/api/')) return;

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        if (!response.ok || response.type === 'opaque') return response;
        const clone = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
        return response;
      }).catch(() => caches.match('/index.html'));
    })
  );
});
