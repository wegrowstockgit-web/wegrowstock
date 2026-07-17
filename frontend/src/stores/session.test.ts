import { beforeEach, describe, expect, it } from 'vitest';
import {
  freezeUser,
  isExclusiveRole,
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
    });

    const user = useSessionStore.getState().user!;
    expect(Object.isFrozen(user)).toBe(true);
    expect(Object.isFrozen(user.roles)).toBe(true);
    expect(() => {
      (user.roles as string[]).push('OWNER');
    }).toThrow();
    expect(user.roles).toEqual(['PICKER']);
    expect(isExclusiveRole(user.roles, 'PICKER')).toBe(true);
    expect(rolesInclude(user.roles, 'OWNER')).toBe(false);
  });

  it('freezeUser deep-freezes nested arrays', () => {
    const frozen = freezeUser({
      id: 'u2',
      email: 'a@b.c',
      displayName: 'A',
      roles: ['VIEWER'],
      warehouseIds: ['w'],
      avatarUrl: null,
      tenantId: 't',
    });
    expect(Object.isFrozen(frozen.warehouseIds)).toBe(true);
  });
});
