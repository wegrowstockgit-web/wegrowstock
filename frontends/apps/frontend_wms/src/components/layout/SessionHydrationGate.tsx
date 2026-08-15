import { useEffect, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useSessionHydrated, useIsAuthenticated, useSessionStore } from '@/stores/session';
import { apiClient } from '@/api/client';

type MeEntitlements = {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  grantedPermissions?: string[];
  isSuperAdmin?: boolean;
  enabledModules?: string[];
};

/**
 * Waits for Zustand persist hydration, then keeps commercial entitlements in sync.
 * Listens for TENANT_SUBSCRIPTION_UPDATED on the dashboard SSE stream (and polls /me
 * as a fallback) so disabled modules disappear from the active DOM promptly.
 */
export function SessionHydrationGate({ children }: { children: ReactNode }) {
  const hydrated = useSessionHydrated();
  const authenticated = useIsAuthenticated();
  const applyMeProfile = useSessionStore((s) => s.applyMeProfile);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!hydrated || !authenticated) return;

    let closed = false;
    let source: EventSource | null = null;
    let pollTimer: ReturnType<typeof setInterval> | undefined;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;

    const refreshMe = async () => {
      try {
        const { data } = await apiClient.get<MeEntitlements>('/api/v1/auth/me');
        if (closed) return;
        applyMeProfile({
          userId: data.userId,
          email: data.email,
          displayName: data.displayName,
          roles: data.roles,
          warehouseIds: data.warehouseIds,
          avatarUrl: data.avatarUrl,
          tenantId: data.tenantId,
          grantedPermissions: data.grantedPermissions,
          isSuperAdmin: data.isSuperAdmin,
          enabledModules: data.enabledModules?.map(String),
        });
        void queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
      } catch {
        // keep prior session; next poll / SSE reconnect will retry
      }
    };

    const connect = () => {
      if (closed || typeof EventSource === 'undefined') return;
      source = new EventSource('/api/v1/dashboard/stream', { withCredentials: true });
      source.addEventListener('dashboard', (evt) => {
        attempt = 0;
        try {
          const data = JSON.parse((evt as MessageEvent).data) as { eventType?: string };
          if (data.eventType === 'TENANT_SUBSCRIPTION_UPDATED') {
            void refreshMe();
          }
        } catch {
          // ignore malformed frames
        }
      });
      source.onerror = () => {
        source?.close();
        source = null;
        if (closed) return;
        const delay = Math.min(30_000, 1_000 * 2 ** Math.min(attempt, 5));
        attempt += 1;
        retryTimer = setTimeout(connect, delay);
      };
    };

    void refreshMe();
    connect();
    pollTimer = setInterval(() => void refreshMe(), 60_000);

    return () => {
      closed = true;
      if (pollTimer) clearInterval(pollTimer);
      if (retryTimer) clearTimeout(retryTimer);
      source?.close();
    };
  }, [hydrated, authenticated, applyMeProfile, queryClient]);

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface text-text-muted">
        Loading session...
      </div>
    );
  }

  return <>{children}</>;
}
