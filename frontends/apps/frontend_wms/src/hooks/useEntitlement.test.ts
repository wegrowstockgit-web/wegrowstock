import { beforeEach, describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useSessionStore } from '@/stores/session';
import { useEntitlement } from './useEntitlement';

describe('useEntitlement', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: false,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('exposes activeModules from session enabledModules and hasModule()', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't1',
      enabledModules: ['CORE', 'MANUFACTURING'],
    });

    const { result } = renderHook(() => useEntitlement());
    expect(result.current.activeModules).toEqual(['CORE', 'MANUFACTURING']);
    expect(result.current.hasModule('MANUFACTURING')).toBe(true);
    expect(result.current.hasModule('RETAIL_POS')).toBe(false);
    expect(result.current.hasModule('B2B_SHOWROOM')).toBe(false);
  });
});
