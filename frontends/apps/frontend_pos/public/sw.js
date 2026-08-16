self.addEventListener('install', (event) => {
  event.waitUntil(caches.open('invsys-pos-v1').then((cache) => cache.addAll(['/', '/index.html', '/manifest.json'])));
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || event.request.url.includes('/api/')) {
    return;
  }
  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request)),
  );
});
