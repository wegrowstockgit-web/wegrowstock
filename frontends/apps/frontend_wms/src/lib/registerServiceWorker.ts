import { Workbox } from 'workbox-window';

/**
 * Registers the Workbox service worker in production and forwards sync signals
 * to the IndexedDB mutation queue when the SW reports connectivity.
 */
export function registerServiceWorker(): void {
  if (!('serviceWorker' in navigator) || !import.meta.env.PROD) {
    return;
  }

  const wb = new Workbox('/sw.js');

  wb.addEventListener('waiting', () => {
    void wb.messageSW({ type: 'SKIP_WAITING' });
  });

  wb.addEventListener('controlling', () => {
    // New SW took control — keep the floor shell warm.
  });

  void wb.register().catch((err: unknown) => {
    console.warn('[invsys] service worker registration failed', err);
  });
}
