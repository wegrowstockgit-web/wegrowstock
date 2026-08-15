import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('idb-keyval', () => {
  let store = new Map<string, unknown>();
  return {
    get: vi.fn(async (key: string) => store.get(key)),
    set: vi.fn(async (key: string, value: unknown) => {
      store.set(key, value);
    }),
    del: vi.fn(async (key: string) => {
      store.delete(key);
    }),
    __reset: () => {
      store = new Map();
    },
  };
});

import * as idb from 'idb-keyval';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { isEncryptedPayload } from '@/utils/cryptoStore';
import { createEncryptedIdbStorage, encryptedGetJson, encryptedSetJson } from './encryptedIdb';

describe('encryptedIdb', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // @ts-expect-error test helper
    idb.__reset?.();
    useCryptoMemoryKeyStore.getState().clearKey();
    await useCryptoMemoryKeyStore.getState().ensureKey();
  });

  it('writes ciphertext to IndexedDB and reads plaintext back', async () => {
    await encryptedSetJson('invsys-mutation-queue', [{ id: '1', idempotencyKey: 'k' }]);
    const stored = await idb.get('invsys-mutation-queue');
    expect(isEncryptedPayload(stored)).toBe(true);

    const loaded = await encryptedGetJson<Array<{ id: string }>>('invsys-mutation-queue');
    expect(loaded).toEqual([{ id: '1', idempotencyKey: 'k' }]);
  });

  it('TanStack storage adapter encrypts setItem and decrypts getItem', async () => {
    const storage = createEncryptedIdbStorage();
    const queryData = JSON.stringify({ clientState: { queries: [] } });
    await storage.setItem('invsys-query-cache', queryData);

    const onDisk = await idb.get('invsys-query-cache');
    expect(isEncryptedPayload(onDisk)).toBe(true);
    expect(onDisk).not.toContain('clientState');

    const restored = await storage.getItem('invsys-query-cache');
    expect(restored).toBe(queryData);
  });

  it('migrates legacy plaintext JSON values', async () => {
    await idb.set('legacy', [{ plain: true }]);
    const loaded = await encryptedGetJson<{ plain: boolean }[]>('legacy');
    expect(loaded).toEqual([{ plain: true }]);
  });
});
