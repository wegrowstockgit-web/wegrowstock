import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEffect, useState } from 'react';
import type { User, TokenResponse } from '@/api/types';
interface SessionState {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  lastRequestId: string | null;
  setSessionFromToken: (token: TokenResponse, email: string, displayName?: string) => void;
  updateTokens: (accessToken: string, refreshToken?: string) => void;
  setLastRequestId: (requestId: string) => void;
  clearSession: () => void;
  isAuthenticated: () => boolean;
  hasRole: (...roles: string[]) => boolean;
  isB2bCustomerOnly: () => boolean;
  isPickerOnly: () => boolean;
  isViewerOnly: () => boolean;
  canManageInventory: () => boolean;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      lastRequestId: null,

      setSessionFromToken: (token, email, displayName) =>
        set({
          accessToken: token.accessToken,
          refreshToken: token.refreshToken,
          user: {
            id: token.userId,
            email,
            displayName: displayName ?? email.split('@')[0],
            roles: token.roles,
          },
        }),

      updateTokens: (accessToken, refreshToken) =>
        set((state) => ({
          accessToken,
          refreshToken: refreshToken ?? state.refreshToken,
        })),

      setLastRequestId: (requestId) => set({ lastRequestId: requestId }),

      clearSession: () =>
        set({ accessToken: null, refreshToken: null, user: null }),

      isAuthenticated: () => !!get().accessToken && !!get().user,

      hasRole: (...roles) => {
        const userRoles = get().user?.roles ?? [];
        return roles.some((role) => userRoles.includes(role));
      },

      isB2bCustomerOnly: () => {
        const userRoles = get().user?.roles ?? [];
        return userRoles.length > 0 && userRoles.every((role) => role === 'B2B_CUSTOMER');
      },

      isPickerOnly: () => {
        const userRoles = get().user?.roles ?? [];
        return userRoles.length > 0 && userRoles.every((role) => role === 'PICKER');
      },

      isViewerOnly: () => {
        const userRoles = get().user?.roles ?? [];
        return userRoles.length > 0 && userRoles.every((role) => role === 'VIEWER');
      },

      canManageInventory: () =>
        get().hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'),
    }),
    {
      name: 'invsys-session',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
      }),
    }
  )
);

export function useIsAuthenticated(): boolean {
  const accessToken = useSessionStore((s) => s.accessToken);
  const user = useSessionStore((s) => s.user);
  return !!accessToken && !!user;
}

export function useSessionHydrated(): boolean {
  const [hydrated, setHydrated] = useState(() => useSessionStore.persist.hasHydrated());

  useEffect(() => {
    if (useSessionStore.persist.hasHydrated()) {
      setHydrated(true);
      return;
    }
    return useSessionStore.persist.onFinishHydration(() => setHydrated(true));
  }, []);

  return hydrated;
}
