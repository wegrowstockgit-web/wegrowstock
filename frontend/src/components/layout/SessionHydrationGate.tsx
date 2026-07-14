import type { ReactNode } from 'react';
import { useSessionHydrated } from '@/stores/session';

export function SessionHydrationGate({ children }: { children: ReactNode }) {
  const hydrated = useSessionHydrated();

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface text-text-muted">
        Loading session...
      </div>
    );
  }

  return <>{children}</>;
}
