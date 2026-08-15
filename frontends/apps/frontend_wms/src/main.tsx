import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { QueryProvider } from './offline/queryPersistence';
import { SessionHydrationGate } from './components/layout/SessionHydrationGate';
import { ToastProvider } from './components/ui/Toast';
import { startMutationQueueReplay } from './offline/mutationQueue';
import { installGlobalErrorTelemetry } from './lib/errorTelemetry';
import { registerServiceWorker } from './lib/registerServiceWorker';
import { installUiActionTracker } from './stores/uiActionTrackerStore';
import './styles/index.css';

installGlobalErrorTelemetry();
installUiActionTracker();
startMutationQueueReplay();
registerServiceWorker();

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
