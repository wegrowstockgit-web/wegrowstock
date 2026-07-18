import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { QueryProvider } from './offline/queryPersistence';
import { SessionHydrationGate } from './components/layout/SessionHydrationGate';
import { ToastProvider } from './components/ui/Toast';
import { startMutationQueueReplay } from './offline/mutationQueue';
import { installGlobalErrorTelemetry } from './lib/errorTelemetry';
import './styles/index.css';

installGlobalErrorTelemetry();
startMutationQueueReplay();

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((err) => {
      // Service worker registration is best-effort
      console.warn('[invsys:telemetry] service worker registration failed', err);
    });
  });
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryProvider>
      <SessionHydrationGate>
        <ToastProvider>
          <App />
        </ToastProvider>
      </SessionHydrationGate>
    </QueryProvider>
  </StrictMode>
);
