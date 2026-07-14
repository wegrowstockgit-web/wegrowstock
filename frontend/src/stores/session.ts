import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEffect, useState } from 'react';
import type { User, TokenResponse } from '@/api/types';

interface PrimarySessionSnapshot {
  accessToken: string;
  refreshToken: string | null;
  user: User;
}

interface TerminalSwitchPayload {
  accessToken: string;
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
}

interface SessionState {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  lastRequestId: string | null;
  /** Device login preserved while a short-lived terminal PIN JWT is active. */
  primarySession: PrimarySessionSnapshot | null;
  setSessionFromToken: (token: TokenResponse, email: string, displayName?: string) => void;
  updateTokens: (accessToken: string, refreshToken?: string) => void;
  applyTerminalSwitch: (token: TerminalSwitchPayload, emailHint?: string) => void;
  restorePrimarySession: () => void;
  isTerminalSwitchActive: () => boolean;
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
      primarySession: null,

      setSessionFromToken: (token, email, displayName) =>
        set({
          accessToken: token.accessToken,
          refreshToken: token.refreshToken,
          primarySession: null,
          user: {
            id: token.userId,
            email,
            displayName: displayName ?? email.split('@')[0],
            roles: token.roles,
            warehouseIds: token.warehouseIds ?? [],
          },
        }),

      updateTokens: (accessToken, refreshToken) =>
        set((state) => ({
          accessToken,
          refreshToken: refreshToken ?? state.refreshToken,
        })),

      applyTerminalSwitch: (token, emailHint) => {
        const state = get();
        if (!state.accessToken || !state.user) return;
        const primary =
          state.primarySession ??
          ({
            accessToken: state.accessToken,
            refreshToken: state.refreshToken,
            user: state.user,
          } satisfies PrimarySessionSnapshot);
        set({
          primarySession: primary,
          accessToken: token.accessToken,
          // Keep primary refresh token — do not kill the station session.
          refreshToken: primary.refreshToken,
          user: {
            id: token.userId,
            email: emailHint ?? state.user.email,
            displayName: emailHint?.split('@')[0] ?? 'Operator',
            roles: token.roles,
            warehouseIds: token.warehouseIds ?? [],
          },
        });
      },

      restorePrimarySession: () => {
        const primary = get().primarySession;
        if (!primary) return;
        set({
          accessToken: primary.accessToken,
          refreshToken: primary.refreshToken,
          user: primary.user,
          primarySession: null,
        });
      },

      isTerminalSwitchActive: () => !!get().primarySession,

      setLastRequestId: (requestId) => set({ lastRequestId: requestId }),

      clearSession: () =>
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
          primarySession: null,
        }),

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
        primarySession: state.primarySession,
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
    const unsub = useSessionStore.persist.onFinishHydration(() => setHydrated(true));
    // Playwright storageState can race hydration — force a rehydrate pass.
    void useSessionStore.persist.rehydrate();
    return unsub;
  }, []);

  return hydrated;
}
