import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEffect, useState } from 'react';
import type { User, SessionResponse } from '@/api/types';

interface PrimarySessionSnapshot {
  user: User;
}

interface TerminalSwitchPayload {
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
}

interface SessionState {
  /** Cookie-authenticated session flag — JWTs never live in JS. */
  authenticated: boolean;
  user: User | null;
  lastRequestId: string | null;
  /** Profile snapshot while a short-lived terminal PIN cookie is active. */
  primarySession: PrimarySessionSnapshot | null;
  setSessionFromLogin: (session: SessionResponse, email: string, displayName?: string) => void;
  setAvatarUrl: (avatarUrl: string | null) => void;
  applyMeProfile: (profile: {
    userId: string;
    email: string;
    displayName: string;
    roles: string[];
    warehouseIds?: string[];
    avatarUrl?: string | null;
    tenantId?: string;
  }) => void;
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
      authenticated: false,
      user: null,
      lastRequestId: null,
      primarySession: null,

      setSessionFromLogin: (session, email, displayName) =>
        set({
          authenticated: true,
          primarySession: null,
          user: {
            id: session.userId,
            email,
            displayName: displayName ?? email.split('@')[0],
            roles: session.roles,
            warehouseIds: session.warehouseIds ?? [],
            avatarUrl: session.avatarUrl ?? null,
            tenantId: session.tenantId,
          },
        }),

      setAvatarUrl: (avatarUrl) =>
        set((state) =>
          state.user
            ? {
                user: { ...state.user, avatarUrl },
                primarySession: state.primarySession
                  ? {
                      ...state.primarySession,
                      user: { ...state.primarySession.user, avatarUrl },
                    }
                  : state.primarySession,
              }
            : state,
        ),

      applyMeProfile: (profile) =>
        set((state) => {
          const nextUser: User = {
            id: profile.userId,
            email: profile.email,
            displayName: profile.displayName,
            roles: profile.roles,
            warehouseIds: profile.warehouseIds ?? [],
            avatarUrl: profile.avatarUrl ?? null,
            tenantId: profile.tenantId ?? state.user?.tenantId,
          };
          return {
            authenticated: true,
            user: nextUser,
            primarySession: state.primarySession
              ? { ...state.primarySession, user: nextUser }
              : state.primarySession,
          };
        }),

      applyTerminalSwitch: (token, emailHint) => {
        const state = get();
        if (!state.authenticated || !state.user) return;
        const primary =
          state.primarySession ??
          ({
            user: state.user,
          } satisfies PrimarySessionSnapshot);
        set({
          primarySession: primary,
          authenticated: true,
          user: {
            id: token.userId,
            email: emailHint ?? state.user.email,
            displayName: emailHint?.split('@')[0] ?? 'Operator',
            roles: token.roles,
            warehouseIds: token.warehouseIds ?? [],
            avatarUrl: state.primarySession?.user.avatarUrl ?? state.user.avatarUrl ?? null,
            tenantId: token.tenantId ?? state.user.tenantId,
          },
        });
      },

      restorePrimarySession: () => {
        const primary = get().primarySession;
        if (!primary) return;
        set({
          user: primary.user,
          primarySession: null,
          authenticated: true,
        });
      },

      isTerminalSwitchActive: () => !!get().primarySession,

      setLastRequestId: (requestId) => set({ lastRequestId: requestId }),

      clearSession: () =>
        set({
          authenticated: false,
          user: null,
          primarySession: null,
        }),

      isAuthenticated: () => get().authenticated && !!get().user,

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

      canManageInventory: () => get().hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'),
    }),
    {
      name: 'invsys-session',
      partialize: (state) => ({
        authenticated: state.authenticated,
        user: state.user,
        primarySession: state.primarySession,
      }),
    },
  ),
);

export function useIsAuthenticated(): boolean {
  const authenticated = useSessionStore((s) => s.authenticated);
  const user = useSessionStore((s) => s.user);
  return authenticated && !!user;
}

export function useSessionHydrated(): boolean {
  const [hydrated, setHydrated] = useState(() => useSessionStore.persist.hasHydrated());

  useEffect(() => {
    if (useSessionStore.persist.hasHydrated()) {
      setHydrated(true);
      return;
    }
    const unsub = useSessionStore.persist.onFinishHydration(() => setHydrated(true));
    void useSessionStore.persist.rehydrate();
    return unsub;
  }, []);

  return hydrated;
}

/** @deprecated use setSessionFromLogin */
export type TokenResponse = SessionResponse;
