import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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

import * as idb from 'idb-keyval';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { useScannerLockStore } from '@/stores/scannerLockStore';
import { useSessionStore } from '@/stores/session';
import { useScannerIdle } from './useScannerIdle';

describe('useScannerIdle', () => {
  beforeEach(async () => {
    vi.useFakeTimers();
    localStorage.clear();
    // @ts-expect-error test helper
    idb.__reset?.();
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
    await useScannerLockStore.getState().setupPin('1234');
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('locks and wipes the AES key after the idle timeout', async () => {
    renderHook(() => useScannerIdle(5_000));
    expect(useScannerLockStore.getState().isLocked).toBe(false);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeTruthy();

    await act(async () => {
      vi.advanceTimersByTime(5_000);
    });

    expect(useScannerLockStore.getState().isLocked).toBe(true);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeNull();
  });

  it('resets the idle timer on activity events', async () => {
    renderHook(() => useScannerIdle(5_000));

    await act(async () => {
      vi.advanceTimersByTime(4_000);
      window.dispatchEvent(new Event('touchstart'));
      vi.advanceTimersByTime(4_000);
    });

    expect(useScannerLockStore.getState().isLocked).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(2_000);
    });

    expect(useScannerLockStore.getState().isLocked).toBe(true);
  });

  it('does not arm when disabled (office routes)', async () => {
    renderHook(() => useScannerIdle({ idleMs: 5_000, enabled: false }));

    await act(async () => {
      vi.advanceTimersByTime(5_000);
    });

    expect(useScannerLockStore.getState().isLocked).toBe(false);
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeTruthy();
  });
});
