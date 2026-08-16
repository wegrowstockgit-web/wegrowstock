import { beforeEach, describe, expect, it } from 'vitest';
import {
  freezeUser,
  isExclusiveRole,
  permissionsInclude,
  rolesInclude,
  useSessionStore,
} from './session';

describe('session integrity', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: false,
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
});
