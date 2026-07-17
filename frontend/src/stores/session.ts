import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEffect, useState } from 'react';
import type { User, SessionResponse } from '@/api/types';

const EMPTY_ROLES: readonly string[] = Object.freeze([]);
const EMPTY_WAREHOUSES: readonly string[] = Object.freeze([]);

interface PrimarySessionSnapshot {
  user: Readonly<User>;
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
  user: Readonly<User> | null;
  lastRequestId: string | null;
  /** Profile snapshot while a short-lived terminal PIN cookie is active. */
  primarySession: Readonly<PrimarySessionSnapshot> | null;
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

/** Deep-freeze auth/me profile fields so console mutation cannot escalate roles. */
export function freezeUser(user: User): Readonly<User> {
  return Object.freeze({
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    roles: Object.freeze([...(user.roles ?? [])]) as string[],
    warehouseIds: Object.freeze([...(user.warehouseIds ?? [])]) as string[],
    avatarUrl: user.avatarUrl ?? null,
    tenantId: user.tenantId,
  });
}

export function rolesInclude(
  roles: readonly string[] | undefined | null,
  ...needed: string[]
): boolean {
  const list = roles ?? EMPTY_ROLES;
  return needed.some((role) => list.includes(role));
}

export function isExclusiveRole(
  roles: readonly string[] | undefined | null,
  role: string,
): boolean {
  const list = roles ?? EMPTY_ROLES;
  return list.length > 0 && list.every((r) => r === role);
}

function freezePrimary(snapshot: PrimarySessionSnapshot): Readonly<PrimarySessionSnapshot> {
  return Object.freeze({ user: freezeUser(snapshot.user as User) });
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
          user: freezeUser({
            id: session.userId,
            email,
            displayName: displayName ?? email.split('@')[0],
            roles: session.roles,
            warehouseIds: session.warehouseIds ?? [],
            avatarUrl: session.avatarUrl ?? null,
            tenantId: session.tenantId,
          }),
        }),

      setAvatarUrl: (avatarUrl) =>
        set((state) =>
          state.user
            ? {
                user: freezeUser({ ...state.user, avatarUrl }),
                primarySession: state.primarySession
                  ? freezePrimary({
                      user: { ...state.primarySession.user, avatarUrl },
                    })
                  : state.primarySession,
              }
            : state,
        ),

      applyMeProfile: (profile) =>
        set((state) => {
          const nextUser = freezeUser({
            id: profile.userId,
            email: profile.email,
            displayName: profile.displayName,
            roles: profile.roles,
            warehouseIds: profile.warehouseIds ?? [],
            avatarUrl: profile.avatarUrl ?? null,
            tenantId: profile.tenantId ?? state.user?.tenantId,
          });
          return {
            authenticated: true,
            user: nextUser,
            primarySession: state.primarySession
              ? freezePrimary({ user: nextUser })
              : state.primarySession,
          };
        }),

      applyTerminalSwitch: (token, emailHint) => {
        const state = get();
        if (!state.authenticated || !state.user) return;
        const primary =
          state.primarySession ??
          freezePrimary({
            user: state.user,
          });
        set({
          primarySession: primary,
          authenticated: true,
          user: freezeUser({
            id: token.userId,
            email: emailHint ?? state.user.email,
            displayName: emailHint?.split('@')[0] ?? 'Operator',
            roles: token.roles,
            warehouseIds: token.warehouseIds ?? [],
            avatarUrl: state.primarySession?.user.avatarUrl ?? state.user.avatarUrl ?? null,
            tenantId: token.tenantId ?? state.user.tenantId,
          }),
        });
      },

      restorePrimarySession: () => {
        const primary = get().primarySession;
        if (!primary) return;
        set({
          user: freezeUser(primary.user as User),
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

      hasRole: (...roles) => rolesInclude(get().user?.roles, ...roles),

      isB2bCustomerOnly: () => isExclusiveRole(get().user?.roles, 'B2B_CUSTOMER'),

      isPickerOnly: () => isExclusiveRole(get().user?.roles, 'PICKER'),

      isViewerOnly: () => isExclusiveRole(get().user?.roles, 'VIEWER'),

      canManageInventory: () => rolesInclude(get().user?.roles, 'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'),
    }),
    {
      name: 'invsys-session',
      partialize: (state) => ({
        authenticated: state.authenticated,
        user: state.user,
        primarySession: state.primarySession,
      }),
      merge: (persisted, current) => {
        const p = (persisted ?? {}) as Partial<SessionState>;
        return {
          ...current,
          ...p,
          user: p.user ? freezeUser(p.user as User) : null,
          primarySession: p.primarySession
            ? freezePrimary(p.primarySession as PrimarySessionSnapshot)
            : null,
        };
      },
    },
  ),
);

/** Reactive role list for layout gates — never copy into a mutable local that consoles can edit. */
export function useSessionRoles(): readonly string[] {
  return useSessionStore((s) => s.user?.roles ?? EMPTY_ROLES);
}

export function useSessionWarehouseIds(): readonly string[] {
  return useSessionStore((s) => s.user?.warehouseIds ?? EMPTY_WAREHOUSES);
}

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
