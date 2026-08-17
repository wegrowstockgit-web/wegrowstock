import { beforeEach, describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';
import {
  freezeUser,
  isExclusiveRole,
  permissionsInclude,
  rolesInclude,
  useCanConfigureRetailPos,
  useSessionStore,
} from './session';

describe('session integrity', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: false,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('freezes auth/me profile roles against console mutation', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'picker@demo.test',
      displayName: 'Picker',
      roles: ['PICKER'],
      warehouseIds: ['wh-1'],
      tenantId: 't1',
      grantedPermissions: ['printing:thermal'],
    });

    const user = useSessionStore.getState().user!;
    expect(Object.isFrozen(user)).toBe(true);
    expect(Object.isFrozen(user.roles)).toBe(true);
    expect(Object.isFrozen(user.grantedPermissions)).toBe(true);
    expect(() => {
      (user.roles as string[]).push('OWNER');
    }).toThrow();
    expect(user.roles).toEqual(['PICKER']);
    expect(isExclusiveRole(user.roles, 'PICKER')).toBe(true);
    expect(rolesInclude(user.roles, 'OWNER')).toBe(false);
    expect(useSessionStore.getState().hasPermission('printing:thermal')).toBe(true);
    expect(useSessionStore.getState().hasPermission('inventory:cost:view')).toBe(false);
  });

  it('multi-role union grants permission when any role contributes it', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u2',
      email: 'hybrid@demo.test',
      displayName: 'Hybrid',
      roles: ['PICKER', 'WAREHOUSE_MANAGER'],
      grantedPermissions: ['printing:thermal', 'inventory:cost:view'],
      tenantId: 't1',
    });
    expect(useSessionStore.getState().hasPermission('inventory:cost:view')).toBe(true);
    expect(permissionsInclude(['printing:thermal'], 'inventory:cost:view')).toBe(false);
  });

  it('applies persisted localeLanguage onto i18n and preferences', async () => {
    const { default: i18n } = await import('@/lib/i18n');
    const { usePreferencesStore } = await import('@/stores/preferencesStore');
    try {
      useSessionStore.getState().applyMeProfile({
        userId: 'u-es',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        tenantId: 't1',
        localeLanguage: 'es-MX',
      });
      expect(useSessionStore.getState().user?.localeLanguage).toBe('es-MX');
      expect(usePreferencesStore.getState().language).toBe('es');
      expect(i18n.language).toMatch(/^es/);
    } finally {
      useSessionStore.getState().applyMeProfile({
        userId: 'u-en',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        tenantId: 't1',
        localeLanguage: 'en',
      });
      await i18n.changeLanguage('en');
    }
  });

  it('freezeUser deep-freezes nested arrays', () => {
    const frozen = freezeUser({
      id: 'u2',
      email: 'a@b.c',
      displayName: 'A',
      roles: ['VIEWER'],
      grantedPermissions: [],
      warehouseIds: ['w'],
      avatarUrl: null,
      tenantId: 't',
    });
    expect(Object.isFrozen(frozen.warehouseIds)).toBe(true);
  });

  it('records mfaVerified on login and clears it on logout', () => {
    useSessionStore.getState().setSessionFromLogin(
      {
        tenantId: 't1',
        userId: 'u1',
        roles: ['OWNER'],
        warehouseIds: [],
        grantedPermissions: [],
      },
      'owner@demo.test',
      'Owner',
      true,
    );
    expect(useSessionStore.getState().mfaVerified).toBe(true);
    useSessionStore.getState().clearSession();
    expect(useSessionStore.getState().mfaVerified).toBe(false);
  });

  it('exposes enabledModules and gates Retail POS configuration', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u-pos',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't1',
      enabledModules: ['CORE', 'RETAIL_POS'],
    });
    expect(useSessionStore.getState().hasModule('RETAIL_POS')).toBe(true);
    expect(useSessionStore.getState().user?.enabledModules).toEqual(['CORE', 'RETAIL_POS']);
    const entitled = renderHook(() => useCanConfigureRetailPos());
    expect(entitled.result.current).toBe(true);
    entitled.unmount();

    useSessionStore.getState().applyMeProfile({
      userId: 'u-basic',
      email: 'owner@acme.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't2',
      enabledModules: ['CORE'],
    });
    const locked = renderHook(() => useCanConfigureRetailPos());
    expect(locked.result.current).toBe(false);
    locked.unmount();
  });
});
