import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEffect, useState } from 'react';
import type { User, SessionResponse } from '@/api/types';
import { normalizeLanguage } from '@/lib/i18n';
import { usePreferencesStore } from '@/stores/preferencesStore';

const EMPTY_ROLES: readonly string[] = Object.freeze([]);
const EMPTY_WAREHOUSES: readonly string[] = Object.freeze([]);
const EMPTY_PERMISSIONS: readonly string[] = Object.freeze([]);
const EMPTY_MODULES: readonly string[] = Object.freeze([]);

interface PrimarySessionSnapshot {
  user: Readonly<User>;
}

interface TerminalSwitchPayload {
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
  grantedPermissions?: string[];
}

interface SessionState {
  /** Cookie-authenticated session flag — JWTs never live in JS. */
  authenticated: boolean;
  /** True when this session completed WebAuthn for off-network MFA. */
  mfaVerified: boolean;
  user: Readonly<User> | null;
  lastRequestId: string | null;
  /** Profile snapshot while a short-lived terminal PIN cookie is active. */
  primarySession: Readonly<PrimarySessionSnapshot> | null;
  setSessionFromLogin: (session: SessionResponse, email: string, displayName?: string, mfaVerified?: boolean) => void;
  setAvatarUrl: (avatarUrl: string | null) => void;
  applyMeProfile: (profile: {
    userId: string;
    email: string;
    displayName: string;
    roles: string[];
    warehouseIds?: string[];
    avatarUrl?: string | null;
    tenantId?: string;
    grantedPermissions?: string[];
    isSuperAdmin?: boolean;
    enabledModules?: string[];
    localeLanguage?: string | null;
    preferredLanguage?: string | null;
    tier?: string | null;
  }) => void;
  applyTerminalSwitch: (token: TerminalSwitchPayload, emailHint?: string) => void;
  restorePrimarySession: () => void;
  isTerminalSwitchActive: () => boolean;
  setLastRequestId: (requestId: string) => void;
  clearSession: () => void;
  isAuthenticated: () => boolean;
  hasRole: (...roles: string[]) => boolean;
  hasPermission: (key: string) => boolean;
  hasModule: (module: string) => boolean;
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
    grantedPermissions: Object.freeze([...(user.grantedPermissions ?? [])]) as string[],
    warehouseIds: Object.freeze([...(user.warehouseIds ?? [])]) as string[],
    avatarUrl: user.avatarUrl ?? null,
    tenantId: user.tenantId,
    isSuperAdmin: user.isSuperAdmin === true,
    enabledModules: Object.freeze([...(user.enabledModules ?? [])]) as string[],
    localeLanguage: user.localeLanguage ?? null,
    tier: user.tier ?? null,
  });
}

export function rolesInclude(
  roles: readonly string[] | undefined | null,
  ...needed: string[]
): boolean {
  const list = roles ?? EMPTY_ROLES;
  return needed.some((role) => list.includes(role));
}

export function permissionsInclude(
  permissions: readonly string[] | undefined | null,
  key: string,
): boolean {
  if (!key) return false;
  const list = permissions ?? EMPTY_PERMISSIONS;
  return list.includes(key);
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
      mfaVerified: false,
      user: null,
      lastRequestId: null,
      primarySession: null,

      setSessionFromLogin: (session, email, displayName, mfaVerified = false) =>
        set({
          authenticated: true,
          mfaVerified: mfaVerified === true,
          primarySession: null,
          user: freezeUser({
            id: session.userId,
            email,
            displayName: displayName ?? email.split('@')[0],
            roles: session.roles,
            grantedPermissions: session.grantedPermissions ?? [],
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
            grantedPermissions:
              profile.grantedPermissions ?? state.user?.grantedPermissions ?? [],
            warehouseIds: profile.warehouseIds ?? [],
            avatarUrl: profile.avatarUrl ?? null,
            tenantId: profile.tenantId ?? state.user?.tenantId,
            isSuperAdmin: profile.isSuperAdmin ?? state.user?.isSuperAdmin ?? false,
            enabledModules: profile.enabledModules ?? state.user?.enabledModules ?? [],
            localeLanguage:
              profile.localeLanguage ??
              profile.preferredLanguage ??
              state.user?.localeLanguage ??
              null,
            tier: profile.tier ?? state.user?.tier ?? null,
          });
          const language = normalizeLanguage(
            nextUser.localeLanguage ?? profile.preferredLanguage,
          );
          usePreferencesStore.getState().setLanguage(language);
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
            grantedPermissions: token.grantedPermissions ?? [],
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
          mfaVerified: false,
          user: null,
          primarySession: null,
        }),

      isAuthenticated: () => get().authenticated && !!get().user,

      hasRole: (...roles) => rolesInclude(get().user?.roles, ...roles),

      hasPermission: (key) => {
        if (rolesInclude(get().user?.roles, 'OWNER')) return true;
        return permissionsInclude(get().user?.grantedPermissions, key);
      },

      hasModule: (module) => {
        if (!module) return false;
        const modules = get().user?.enabledModules ?? EMPTY_MODULES;
        // Until /me has hydrated entitlements, do not strip UI (build-time flags still apply).
        if (modules.length === 0) return true;
        return modules.includes(module);
      },

      isB2bCustomerOnly: () => isExclusiveRole(get().user?.roles, 'B2B_CUSTOMER'),

      isPickerOnly: () => isExclusiveRole(get().user?.roles, 'PICKER'),

      isViewerOnly: () => isExclusiveRole(get().user?.roles, 'VIEWER'),

      canManageInventory: () => rolesInclude(get().user?.roles, 'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'),
    }),
    {
      name: 'invsys-session',
      partialize: (state) => ({
        authenticated: state.authenticated,
        mfaVerified: state.mfaVerified,
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
      onRehydrateStorage: () => (state) => {
        const locale = state?.user?.localeLanguage;
        if (locale) {
          usePreferencesStore.getState().setLanguage(normalizeLanguage(locale));
        }
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

export function useEnabledModules(): readonly string[] {
  return useSessionStore((s) => s.user?.enabledModules ?? EMPTY_MODULES);
}

/** Retail POS settings tab: OWNER/ADMIN and an explicit RETAIL_POS module. */
export function useCanConfigureRetailPos(): boolean {
  const roles = useSessionStore((s) => s.user?.roles ?? EMPTY_ROLES);
  const modules = useSessionStore((s) => s.user?.enabledModules ?? EMPTY_MODULES);
  return rolesInclude(roles, 'OWNER', 'ADMIN') && modules.includes('RETAIL_POS');
}

export function useIsSuperAdmin(): boolean {
  return useSessionStore((s) => s.user?.isSuperAdmin === true);
}

/** Reactive check against the union of granted permissions across the user's roles. */
export function useHasPermission(key: string): boolean {
  const roles = useSessionStore((s) => s.user?.roles ?? EMPTY_ROLES);
  const permissions = useSessionStore((s) => s.user?.grantedPermissions ?? EMPTY_PERMISSIONS);
  if (rolesInclude(roles, 'OWNER')) return true;
  return permissionsInclude(permissions, key);
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
