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
    __reset: () => store.clear(),
  };
});

import * as idb from 'idb-keyval';
import {
  clearPinVerifier,
  hasPinVerifier,
  verifyPinAndDerive,
  writePinVerifier,
} from './pinVault';
import { deriveAesKeyFromPin, getOrCreateDeviceSalt } from '@/utils/cryptoStore';

describe('pinVault', () => {
  beforeEach(() => {
    // @ts-expect-error test helper
    idb.__reset?.();
    localStorage.clear();
  });

  it('reports verifier absence then presence after write', async () => {
    expect(await hasPinVerifier()).toBe(false);
    const key = await deriveAesKeyFromPin('1234', getOrCreateDeviceSalt());
    await writePinVerifier(key);
    expect(await hasPinVerifier()).toBe(true);
  });

  it('verifyPinAndDerive returns key only for the correct PIN', async () => {
    const key = await deriveAesKeyFromPin('1234', getOrCreateDeviceSalt());
    await writePinVerifier(key);
    expect(await verifyPinAndDerive('9999')).toBeNull();
    const unlocked = await verifyPinAndDerive('1234');
    expect(unlocked).toBeTruthy();
  });

  it('clearPinVerifier removes the validation ciphertext', async () => {
    const key = await deriveAesKeyFromPin('1234', getOrCreateDeviceSalt());
    await writePinVerifier(key);
    await clearPinVerifier();
    expect(await hasPinVerifier()).toBe(false);
    expect(await verifyPinAndDerive('1234')).toBeNull();
  });
});
