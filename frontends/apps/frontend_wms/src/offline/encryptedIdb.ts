import { del, get, set } from 'idb-keyval';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { decrypt, encrypt, isEncryptedPayload } from '@/utils/cryptoStore';

function activeMemoryKey(): CryptoKey | null {
  return useCryptoMemoryKeyStore.getState().memoryKey;
}

/**
 * AES-GCM wrapped idb-keyval accessors. Ciphertext only is written to IndexedDB;
 * the CryptoKey stays in the in-memory Zustand store (PIN-derived).
 */
export async function encryptedGetJson<T>(key: string): Promise<T | undefined> {
  const raw = await get<unknown>(key);
  if (raw == null) return undefined;

  if (isEncryptedPayload(raw)) {
    const memoryKey = activeMemoryKey();
    if (!memoryKey) return undefined;
    try {
      const plain = await decrypt(raw, memoryKey);
      return JSON.parse(plain) as T;
    } catch {
      return undefined;
    }
  }

  // Legacy plaintext (pre-encryption) — return as-is; next write re-encrypts.
  return raw as T;
}

export async function encryptedSetJson(key: string, value: unknown): Promise<void> {
  const memoryKey = activeMemoryKey();
  if (!memoryKey) {
    throw new Error('CRYPTO_KEY_LOCKED');
  }
  const cipherText = await encrypt(JSON.stringify(value), memoryKey);
  await set(key, cipherText);
}

export async function encryptedDel(key: string): Promise<void> {
  await del(key);
}

/** Async storage adapter shape expected by TanStack Query persist client. */
export function createEncryptedIdbStorage() {
  return {
    getItem: async (key: string): Promise<string | null> => {
      const raw = await get<unknown>(key);
      if (raw == null) return null;

      if (isEncryptedPayload(raw)) {
        const memoryKey = activeMemoryKey();
        if (!memoryKey) return null;
        try {
          return await decrypt(raw, memoryKey);
        } catch {
          return null;
        }
      }

      if (typeof raw === 'string') return raw;
      return JSON.stringify(raw);
    },
    setItem: async (key: string, value: string): Promise<void> => {
      const memoryKey = activeMemoryKey();
      if (!memoryKey) return;
      const cipherText = await encrypt(value, memoryKey);
      await set(key, cipherText);
    },
    removeItem: async (key: string): Promise<void> => {
      await del(key);
    },
  };
}
