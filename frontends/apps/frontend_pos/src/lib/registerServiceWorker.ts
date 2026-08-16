export function registerServiceWorker(
  sw: Pick<ServiceWorkerContainer, 'register'> | undefined = navigator.serviceWorker,
): void {
  if (!sw || import.meta.env.MODE === 'test') return;
  void sw.register('/sw.js').catch(() => undefined);
}
