import { create } from 'zustand';
import { del, keys } from 'idb-keyval';
import { clearTerminalPasskey } from '@/lib/terminalPasskey';
import { queryClient } from '@/lib/queryClient';
import { hasPinVerifier, verifyPinAndDerive, writePinVerifier } from '@/offline/pinVault';
import { purgeEncryptedOfflineData } from '@/offline/queryPersistence';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { useSessionStore } from '@/stores/session';
import { deriveAesKeyFromPin, getOrCreateDeviceSalt } from '@/utils/cryptoStore';

export const MAX_PIN_FAILURES = 5;
/** Default 10 minutes; override with VITE_SCANNER_IDLE_MS for E2E. */
export const SCANNER_IDLE_MS = Number(import.meta.env.VITE_SCANNER_IDLE_MS ?? 600_000);

interface ScannerLockState {
  isLocked: boolean;
  /** True once a shift PIN verifier exists in IndexedDB. */
  pinConfigured: boolean;
  /** True while first-shift PIN setup overlay should show. */
  needsPinSetup: boolean;
  failedAttempts: number;
  hydrated: boolean;
  hydrate: () => Promise<void>;
  setupPin: (pin: string) => Promise<void>;
  lockDevice: () => void;
  tryUnlock: (pin: string) => Promise<'ok' | 'bad' | 'wiped'>;
  wipeAndHardLogout: () => Promise<void>;
  resetLockState: () => void;
}

async function purgeAllIdb(): Promise<void> {
  try {
    const allKeys = await keys();
    await Promise.all(allKeys.map((k) => del(k)));
  } catch {
    // best-effort
  }
  await purgeEncryptedOfflineData();
}

export const useScannerLockStore = create<ScannerLockState>((set, get) => ({
  isLocked: false,
  pinConfigured: false,
  needsPinSetup: false,
  failedAttempts: 0,
  hydrated: false,

  hydrate: async () => {
    const configured = await hasPinVerifier();
    const authenticated = useSessionStore.getState().authenticated;
    const hasKey = !!useCryptoMemoryKeyStore.getState().memoryKey;
    set({
      pinConfigured: configured,
      // Authenticated + verifier but no volatile key → treat as locked (reload / idle).
      isLocked: authenticated && configured && !hasKey,
      needsPinSetup: authenticated && !configured,
      hydrated: true,
      failedAttempts: 0,
    });
  },

  setupPin: async (pin: string) => {
    const key = await deriveAesKeyFromPin(pin, getOrCreateDeviceSalt());
    await writePinVerifier(key);
    useCryptoMemoryKeyStore.getState().setMemoryKey(key);
    set({
      pinConfigured: true,
      needsPinSetup: false,
      isLocked: false,
      failedAttempts: 0,
    });
  },

  lockDevice: () => {
    useCryptoMemoryKeyStore.getState().clearKey();
    set({ isLocked: true, failedAttempts: 0 });
  },

  tryUnlock: async (pin: string) => {
    const key = await verifyPinAndDerive(pin);
    if (key) {
      useCryptoMemoryKeyStore.getState().setMemoryKey(key);
      set({ isLocked: false, failedAttempts: 0 });
      if (navigator.onLine) {
        void queryClient.resumePausedMutations();
      }
      return 'ok';
    }

    const next = get().failedAttempts + 1;
    set({ failedAttempts: next });
    if (next >= MAX_PIN_FAILURES) {
      await get().wipeAndHardLogout();
      return 'wiped';
    }
    return 'bad';
  },

  wipeAndHardLogout: async () => {
    useCryptoMemoryKeyStore.getState().clearKey();
    clearTerminalPasskey();
    useSessionStore.getState().clearSession();
    useActiveWarehouseStore.getState().clearWarehouse();
    queryClient.clear();
    await purgeAllIdb();
    set({
      isLocked: false,
      pinConfigured: false,
      needsPinSetup: false,
      failedAttempts: 0,
    });
    if (typeof window !== 'undefined') {
      window.location.replace('/login');
    }
  },

  resetLockState: () => {
    set({
      isLocked: false,
      pinConfigured: false,
      needsPinSetup: false,
      failedAttempts: 0,
      hydrated: false,
    });
  },
}));

/** Test / E2E seam — never relied on by production UI. */
export function installScannerLockTestHook(): void {
  if (typeof window === 'undefined') return;
  (window as Window & { __INVSYS_SCANNER_LOCK__?: { lockDevice: () => void } }).__INVSYS_SCANNER_LOCK__ =
    {
      lockDevice: () => useScannerLockStore.getState().lockDevice(),
    };
}
