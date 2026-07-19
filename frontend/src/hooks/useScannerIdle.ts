import { useEffect, useRef } from 'react';
import { SCANNER_IDLE_MS, useScannerLockStore } from '@/stores/scannerLockStore';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';
import { useIsAuthenticated } from '@/stores/session';

const ACTIVITY_EVENTS = ['touchstart', 'keydown', 'click', 'scroll'] as const;

/**
 * Floor scanner idle lock — after {@link SCANNER_IDLE_MS} without interaction,
 * wipes the AES-GCM key from memory and sets `isLocked`.
 */
export function useScannerIdle(idleMs: number = SCANNER_IDLE_MS): void {
  const authenticated = useIsAuthenticated();
  const isLocked = useScannerLockStore((s) => s.isLocked);
  const needsPinSetup = useScannerLockStore((s) => s.needsPinSetup);
  const pinConfigured = useScannerLockStore((s) => s.pinConfigured);
  const lockDevice = useScannerLockStore((s) => s.lockDevice);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    const armed =
      authenticated && pinConfigured && !needsPinSetup && !isLocked && !!useCryptoMemoryKeyStore.getState().memoryKey;
    if (!armed) {
      if (timerRef.current) clearTimeout(timerRef.current);
      return;
    }

    const arm = () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        lockDevice();
      }, idleMs);
    };

    arm();
    for (const event of ACTIVITY_EVENTS) {
      window.addEventListener(event, arm, { passive: true, capture: true });
    }

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      for (const event of ACTIVITY_EVENTS) {
        window.removeEventListener(event, arm, { capture: true });
      }
    };
  }, [authenticated, pinConfigured, needsPinSetup, isLocked, idleMs, lockDevice]);
}
