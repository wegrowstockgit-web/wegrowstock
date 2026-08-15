import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('idb-keyval', () => {
  const store = new Map<string, unknown>();
  return {
    get: vi.fn(async (key: string) => store.get(key)),
    set: vi.fn(async (key: string, value: unknown) => {
      store.set(key, value);
    }),
    del: vi.fn(async (key: string) => {
      store.delete(key);
    }),
    keys: vi.fn(async () => [...store.keys()]),
    __reset: () => store.clear(),
  };
});

vi.mock('@/offline/queryPersistence', async () => {
  const actual = await vi.importActual<typeof import('@/offline/queryPersistence')>(
    '@/offline/queryPersistence',
  );
  return {
    ...actual,
    purgeEncryptedOfflineData: vi.fn(async () => {
      const { clearPinVerifier } = await import('@/offline/pinVault');
      await clearPinVerifier();
      const { useCryptoMemoryKeyStore } = await import('@/stores/cryptoMemoryKeyStore');
      useCryptoMemoryKeyStore.getState().clearKey();
    }),
  };
});

import * as idb from 'idb-keyval';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { useSessionStore } from '@/stores/session';
import {
  installScannerLockTestHook,
  MAX_PIN_FAILURES,
  useScannerLockStore,
} from './scannerLockStore';

describe('scannerLockStore', () => {
  beforeEach(() => {
    // @ts-expect-error test helper
    idb.__reset?.();
    localStorage.clear();
    useCryptoMemoryKeyStore.getState().clearKey();
    useScannerLockStore.getState().resetLockState();
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'picker@demo.test',
        displayName: 'Picker',
        roles: ['PICKER'],
        warehouseIds: [],
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    } as never);
    // Prevent hard navigation during wipe tests.
    vi.stubGlobal('location', { ...window.location, replace: vi.fn() });
  });

  it('setupPin derives key, writes verifier, and clears setup flag', async () => {
    await useScannerLockStore.getState().setupPin('1234');
    expect(useScannerLockStore.getState().pinConfigured).toBe(true);
    expect(useScannerLockStore.getState().needsPinSetup).toBe(false);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeTruthy();
  });

  it('lockDevice wipes volatile AES key and sets isLocked', async () => {
    await useScannerLockStore.getState().setupPin('1234');
    useScannerLockStore.getState().lockDevice();
    expect(useScannerLockStore.getState().isLocked).toBe(true);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeNull();
  });

  it('tryUnlock restores key with correct PIN', async () => {
    await useScannerLockStore.getState().setupPin('1234');
    useScannerLockStore.getState().lockDevice();
    const result = await useScannerLockStore.getState().tryUnlock('1234');
    expect(result).toBe('ok');
    expect(useScannerLockStore.getState().isLocked).toBe(false);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeTruthy();
  });

  it('wipes offline data after MAX_PIN_FAILURES bad attempts', async () => {
    await useScannerLockStore.getState().setupPin('1234');
    useScannerLockStore.getState().lockDevice();

    let last: 'ok' | 'bad' | 'wiped' = 'bad';
    for (let i = 0; i < MAX_PIN_FAILURES; i += 1) {
      last = await useScannerLockStore.getState().tryUnlock('0000');
    }
    expect(last).toBe('wiped');
    expect(useSessionStore.getState().authenticated).toBe(false);
    expect(window.location.replace).toHaveBeenCalledWith('/login');
  });

  it('hydrate marks setup needed when authenticated without verifier', async () => {
    await useScannerLockStore.getState().hydrate();
    expect(useScannerLockStore.getState().hydrated).toBe(true);
    expect(useScannerLockStore.getState().needsPinSetup).toBe(true);
    expect(useScannerLockStore.getState().pinConfigured).toBe(false);
  });

  it('hydrate locks when verifier exists but AES key was wiped', async () => {
    await useScannerLockStore.getState().setupPin('1234');
    useCryptoMemoryKeyStore.getState().clearKey();
    await useScannerLockStore.getState().hydrate();
    expect(useScannerLockStore.getState().pinConfigured).toBe(true);
    expect(useScannerLockStore.getState().isLocked).toBe(true);
    expect(useScannerLockStore.getState().needsPinSetup).toBe(false);
  });

  it('installScannerLockTestHook exposes lockDevice and getState on window', async () => {
    await useScannerLockStore.getState().setupPin('1234');
    installScannerLockTestHook();
    const hook = (
      window as Window & {
        __INVSYS_SCANNER_LOCK__?: {
          lockDevice: () => void;
          getState: () => { hydrated: boolean; isLocked: boolean };
        };
      }
    ).__INVSYS_SCANNER_LOCK__;
    expect(hook?.getState?.().isLocked).toBe(false);
    hook?.lockDevice();
    expect(useScannerLockStore.getState().isLocked).toBe(true);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeNull();
  });
});


