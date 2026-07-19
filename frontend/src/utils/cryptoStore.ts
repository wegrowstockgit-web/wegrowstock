/**
 * AES-GCM + PBKDF2 helpers for offline IndexedDB (Web Crypto API).
 * The derived CryptoKey must live only in volatile memory — never on disk.
 */

const ALGO = 'AES-GCM' as const;
/** AES-GCM IV length — 96 bits (12 bytes) per NIST / Web Crypto guidance. */
export const AES_GCM_IV_BYTES = 12;
const VERSION_PREFIX = 'enc:v1:';
export const PBKDF2_ITERATIONS = 120_000;
const DEVICE_SALT_STORAGE = 'invsys-device-salt-v1';
const DEVICE_SALT_BYTES = 16;

export function isEncryptedPayload(value: unknown): value is string {
  return typeof value === 'string' && value.startsWith(VERSION_PREFIX);
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]!);
  }
  return btoa(binary);
}

function base64ToBytes(b64: string): Uint8Array {
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    out[i] = binary.charCodeAt(i);
  }
  return out;
}

/** Persistent per-device salt (not secret). Combined with the shift PIN in PBKDF2. */
export function getOrCreateDeviceSalt(): Uint8Array {
  if (typeof localStorage === 'undefined') {
    return crypto.getRandomValues(new Uint8Array(DEVICE_SALT_BYTES));
  }
  const existing = localStorage.getItem(DEVICE_SALT_STORAGE);
  if (existing) {
    try {
      return base64ToBytes(existing);
    } catch {
      localStorage.removeItem(DEVICE_SALT_STORAGE);
    }
  }
  const salt = crypto.getRandomValues(new Uint8Array(DEVICE_SALT_BYTES));
  localStorage.setItem(DEVICE_SALT_STORAGE, bytesToBase64(salt));
  return salt;
}

/**
 * Derive a non-extractable AES-256-GCM key from a 4-digit PIN + device salt (PBKDF2-SHA-256).
 */
export async function deriveAesKeyFromPin(
  pin: string,
  salt: Uint8Array = getOrCreateDeviceSalt(),
): Promise<CryptoKey> {
  if (!/^\d{4}$/.test(pin)) {
    throw new Error('PIN_INVALID_FORMAT');
  }
  const material = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(pin),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt as BufferSource,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256',
    },
    material,
    { name: ALGO, length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

/** @deprecated Prefer {@link deriveAesKeyFromPin} on the floor. Kept for unit-test bootstraps. */
export async function generateMemoryKey(): Promise<CryptoKey> {
  return crypto.subtle.generateKey({ name: ALGO, length: 256 }, false, ['encrypt', 'decrypt']);
}

/**
 * Encrypt UTF-8 plaintext → versioned base64 blob (`enc:v1:` + iv||ciphertext).
 * IV is always {@link AES_GCM_IV_BYTES} (12) random bytes.
 */
export async function encrypt(plainText: string, memoryKey: CryptoKey): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(AES_GCM_IV_BYTES));
  const encoded = new TextEncoder().encode(plainText);
  const cipherBuf = await crypto.subtle.encrypt({ name: ALGO, iv }, memoryKey, encoded);
  const cipher = new Uint8Array(cipherBuf);
  const packed = new Uint8Array(iv.length + cipher.length);
  packed.set(iv, 0);
  packed.set(cipher, iv.length);
  return VERSION_PREFIX + bytesToBase64(packed);
}

/**
 * Decrypt a versioned AES-GCM blob produced by {@link encrypt}.
 */
export async function decrypt(cipherText: string, memoryKey: CryptoKey): Promise<string> {
  if (!isEncryptedPayload(cipherText)) {
    throw new Error('CRYPTO_STORE_NOT_ENCRYPTED');
  }
  const packed = base64ToBytes(cipherText.slice(VERSION_PREFIX.length));
  if (packed.length <= AES_GCM_IV_BYTES) {
    throw new Error('CRYPTO_STORE_TRUNCATED');
  }
  const iv = packed.subarray(0, AES_GCM_IV_BYTES);
  const data = packed.subarray(AES_GCM_IV_BYTES);
  const plainBuf = await crypto.subtle.decrypt({ name: ALGO, iv }, memoryKey, data);
  return new TextDecoder().decode(plainBuf);
}

export { AES_GCM_IV_BYTES as IV_BYTES };
