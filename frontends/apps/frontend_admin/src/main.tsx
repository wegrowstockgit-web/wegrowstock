import { StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ErrorBoundary, PageSkeleton, ToastProvider } from '@invsys/shared-ui';
import { App } from './App';
import { ensureCsrfCookie } from './lib/apiClient';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

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
