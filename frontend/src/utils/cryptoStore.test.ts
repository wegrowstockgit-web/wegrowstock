import { beforeEach, describe, expect, it } from 'vitest';
import {
  AES_GCM_IV_BYTES,
  decrypt,
  deriveAesKeyFromPin,
  encrypt,
  generateMemoryKey,
  getOrCreateDeviceSalt,
  isEncryptedPayload,
} from './cryptoStore';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';

describe('cryptoStore AES-GCM', () => {
  beforeEach(() => {
    useCryptoMemoryKeyStore.getState().clearKey();
    localStorage.removeItem('invsys-device-salt-v1');
  });

  it('uses a 12-byte (96-bit) IV in the packed ciphertext', async () => {
    expect(AES_GCM_IV_BYTES).toBe(12);
    const key = await generateMemoryKey();
    const cipher = await encrypt('probe', key);
    const packed = atob(cipher.slice('enc:v1:'.length));
    // iv (12) + tag (16) + at least 1 byte ciphertext
    expect(packed.length).toBeGreaterThan(12 + 16);
  });

  it('round-trips plaintext with a memory key', async () => {
    const key = await generateMemoryKey();
    const cipher = await encrypt(JSON.stringify({ scans: [1, 2] }), key);
    expect(isEncryptedPayload(cipher)).toBe(true);
    expect(cipher.startsWith('enc:v1:')).toBe(true);
    const plain = await decrypt(cipher, key);
    expect(JSON.parse(plain)).toEqual({ scans: [1, 2] });
  });

  it('derives a stable AES key from PIN + device salt via PBKDF2', async () => {
    const salt = getOrCreateDeviceSalt();
    const a = await deriveAesKeyFromPin('1234', salt);
    const b = await deriveAesKeyFromPin('1234', salt);
    const payload = 'invsys-pin-ok-v1';
    const cipher = await encrypt(payload, a);
    expect(await decrypt(cipher, b)).toBe(payload);
  });

  it('rejects wrong PIN for the same salt', async () => {
    const salt = getOrCreateDeviceSalt();
    const good = await deriveAesKeyFromPin('1234', salt);
    const bad = await deriveAesKeyFromPin('9999', salt);
    const cipher = await encrypt('secret', good);
    await expect(decrypt(cipher, bad)).rejects.toThrow();
  });

  it('memory key store resets on clearKey (never persisted)', async () => {
    const first = await useCryptoMemoryKeyStore.getState().ensureKey();
    const again = await useCryptoMemoryKeyStore.getState().ensureKey();
    expect(again).toBe(first);
    useCryptoMemoryKeyStore.getState().clearKey();
    expect(useCryptoMemoryKeyStore.getState().memoryKey).toBeNull();
    const next = await useCryptoMemoryKeyStore.getState().ensureKey();
    expect(next).not.toBe(first);
  });
});
