import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  DEMO_MANAGER_ID,
  DEMO_MANAGER_PIN,
  DEMO_TENANT_ID,
  cacheManagerPins,
  clearPinVault,
  hashManagerPin,
  seedDemoManagerPinsIfEmpty,
  syncManagerPinVault,
  validateManagerPin,
} from './pinVault';

describe('pinVault', () => {
  afterEach(() => {
    clearPinVault();
  });

  it('validates a cached manager PIN offline and rejects misses', () => {
    cacheManagerPins(DEMO_TENANT_ID, [
      { managerId: 'mgr-a', pinHash: hashManagerPin(DEMO_TENANT_ID, '4821') },
    ]);
    expect(validateManagerPin('4821')).toBe('mgr-a');
    expect(validateManagerPin('0000')).toBeNull();
    expect(validateManagerPin('12')).toBeNull();
  });

  it('seeds the demo vault once and keeps it across empty writes', () => {
    const first = seedDemoManagerPinsIfEmpty();
    expect(validateManagerPin(DEMO_MANAGER_PIN)).toBe(DEMO_MANAGER_ID);
    expect(seedDemoManagerPinsIfEmpty().managers).toEqual(first.managers);
  });

  it('replaces the vault from the morning sync payload', async () => {
    seedDemoManagerPinsIfEmpty();
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        tenantId: 'tenant-live',
        managers: [{ managerId: 'live-mgr', pinHash: hashManagerPin('tenant-live', '9999') }],
      }),
    });
    await syncManagerPinVault(fetchImpl);
    expect(fetchImpl).toHaveBeenCalledWith(
      '/api/v1/pos/managers/sync-pins',
      expect.objectContaining({ credentials: 'include' }),
    );
    expect(validateManagerPin(DEMO_MANAGER_PIN)).toBeNull();
    expect(validateManagerPin('9999')).toBe('live-mgr');
  });

  it('ignores a corrupt vault and session-shaped morning sync payloads', async () => {
    localStorage.setItem('pos.managerPins.v1', '{not-json');
    expect(validateManagerPin('1234')).toBeNull();
    await syncManagerPinVault(vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ posEnabled: true, language: 'en' }),
    }));
    expect(validateManagerPin('1234')).toBeNull();
  });

  it('keeps the cached vault when morning sync is offline', async () => {
    cacheManagerPins(DEMO_TENANT_ID, [
      { managerId: 'kept', pinHash: hashManagerPin(DEMO_TENANT_ID, '2468') },
    ]);
    await syncManagerPinVault(vi.fn().mockRejectedValue(new Error('offline')));
    expect(validateManagerPin('2468')).toBe('kept');
    await syncManagerPinVault(vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    expect(validateManagerPin('2468')).toBe('kept');
  });
});
