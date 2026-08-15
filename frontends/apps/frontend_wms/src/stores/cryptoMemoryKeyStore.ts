import { create } from 'zustand';
import { generateMemoryKey } from '@/utils/cryptoStore';

/**
 * Session-only AES-GCM key holder. Intentionally NOT persisted — wiped on idle lock
 * and page reload so the encryption key never touches IndexedDB / localStorage.
 */
interface CryptoMemoryKeyState {
  memoryKey: CryptoKey | null;
  /** Install a PIN-derived (or test) key into volatile memory. */
  setMemoryKey: (key: CryptoKey) => void;
  /**
   * Returns the in-memory key. In Vitest only, mints an ephemeral key when missing
   * so unit tests do not require the full PIN gate.
   */
  ensureKey: () => Promise<CryptoKey>;
  clearKey: () => void;
}

function isVitestRuntime(): boolean {
  return (
    import.meta.env.MODE === 'test' ||
    import.meta.env.VITEST === true ||
    import.meta.env.VITEST === 'true'
  );
}

export const useCryptoMemoryKeyStore = create<CryptoMemoryKeyState>((set, get) => ({
  memoryKey: null,

  setMemoryKey: (memoryKey) => {
    set({ memoryKey });
  },

  ensureKey: async () => {
    const existing = get().memoryKey;
    if (existing) return existing;
    if (isVitestRuntime()) {
      const memoryKey = await generateMemoryKey();
      set({ memoryKey });
      return memoryKey;
    }
    throw new Error('CRYPTO_KEY_LOCKED');
  },

  clearKey: () => {
    set({ memoryKey: null });
  },
}));
