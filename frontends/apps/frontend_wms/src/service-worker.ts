/// <reference lib="webworker" />
import { clientsClaim } from 'workbox-core';
import { registerRoute, NavigationRoute } from 'workbox-routing';
import { StaleWhileRevalidate, CacheFirst } from 'workbox-strategies';

declare const self: ServiceWorkerGlobalScope;

clientsClaim();
void self.skipWaiting();

/** Bump when SW build or shell asset strategy changes so activate purges stale shells. */
const SHELL_CACHE = 'invsys-shell-swr-v2';
const MEDIA_CACHE = 'invsys-media-v1';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) => cache.addAll(['/', '/index.html', '/manifest.json'])),
  );
});

/**
 * UI shell (HTML / CSS / JS): StaleWhileRevalidate so scanners boot instantly
 * from cache even when Wi-Fi is down, then refresh in the background when online.
 */
registerRoute(
  ({ request, url }) => {
    if (url.pathname.startsWith('/api/')) return false;
    if (request.destination === 'script' || request.destination === 'style') return true;
    if (url.pathname.startsWith('/assets/')) return true;
    return false;
  },
  new StaleWhileRevalidate({
    cacheName: SHELL_CACHE,
  }),
);

registerRoute(
  new NavigationRoute(
    new StaleWhileRevalidate({
      cacheName: SHELL_CACHE,
    }),
    {
      denylist: [/^\/api\//],
    },
  ),
);

/** Catalog / QC media: cache-first for floor dead zones. */
registerRoute(
  ({ url }) => url.pathname.startsWith('/api/v1/media/'),
  new CacheFirst({
    cacheName: MEDIA_CACHE,
  }),
);

self.addEventListener('message', (event) => {
  const data = event.data as { type?: string } | undefined;
  if (data?.type === 'SKIP_WAITING') {
    void self.skipWaiting();
  }
  if (data?.type === 'SYNC_QUEUE' || data?.type === 'ONLINE') {
    void self.clients.matchAll({ type: 'window' }).then((clients) => {
      for (const client of clients) {
        client.postMessage({ type: 'SYNC_QUEUE' });
      }
    });
  }
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keep = new Set([SHELL_CACHE, MEDIA_CACHE]);
      const keys = await caches.keys();
      await Promise.all(keys.filter((k) => !keep.has(k)).map((k) => caches.delete(k)));
      await self.clients.claim();
    })(),
  );
});
