import { del, get, set } from 'idb-keyval';
import { decrypt, deriveAesKeyFromPin, encrypt, getOrCreateDeviceSalt } from '@/utils/cryptoStore';

/** Tiny ciphertext used to validate a reconstructed PIN-derived key. */
export const PIN_VERIFY_IDB_KEY = 'invsys-pin-verify';
export const PIN_VERIFY_PLAINTEXT = 'invsys-pin-ok-v1';

export async function hasPinVerifier(): Promise<boolean> {
  const raw = await get<unknown>(PIN_VERIFY_IDB_KEY);
  return typeof raw === 'string' && raw.length > 0;
}

export async function writePinVerifier(memoryKey: CryptoKey): Promise<void> {
  const cipher = await encrypt(PIN_VERIFY_PLAINTEXT, memoryKey);
  await set(PIN_VERIFY_IDB_KEY, cipher);
}

export async function verifyPinAndDerive(pin: string): Promise<CryptoKey | null> {
  const raw = await get<unknown>(PIN_VERIFY_IDB_KEY);
  if (typeof raw !== 'string') return null;
  try {
    const key = await deriveAesKeyFromPin(pin, getOrCreateDeviceSalt());
    const plain = await decrypt(raw, key);
    if (plain !== PIN_VERIFY_PLAINTEXT) return null;
    return key;
  } catch {
    return null;
  }
}

export async function clearPinVerifier(): Promise<void> {
  await del(PIN_VERIFY_IDB_KEY);
}
