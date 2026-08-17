import { StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { ErrorBoundary, PageSkeleton, ToastProvider } from '@invsys/shared-ui';
import { App } from './App';
import { ensureCsrfCookie } from './lib/apiClient';
import { queryClient } from './lib/queryClient';
import './index.css';

void ensureCsrfCookie().catch(() => {
  /* login remains CSRF-exempt; cookie warm is best-effort */
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ErrorBoundary boundaryName="admin-root">
          <Suspense fallback={<PageSkeleton label="Loading control plane…" />}>
            <App />
          </Suspense>
        </ErrorBoundary>
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
);
