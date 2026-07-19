import { useEffect, type ReactNode } from 'react';
import { ScannerLockOverlay } from '@/components/security/ScannerLockOverlay';
import { ScannerPinSetupOverlay } from '@/components/security/ScannerPinSetupOverlay';
import { useScannerIdle } from '@/hooks/useScannerIdle';
import {
  installScannerLockTestHook,
  useScannerLockStore,
} from '@/stores/scannerLockStore';
import { useIsAuthenticated } from '@/stores/session';

/**
 * Orchestrates shift PIN setup, idle cryptographic wipe, and unlock overlays.
 */
export function ScannerSecurityGate({ children }: { children: ReactNode }) {
  const authenticated = useIsAuthenticated();
  const hydrate = useScannerLockStore((s) => s.hydrate);
  const hydrated = useScannerLockStore((s) => s.hydrated);
  const resetLockState = useScannerLockStore((s) => s.resetLockState);

  useScannerIdle();

  useEffect(() => {
    installScannerLockTestHook();
  }, []);

  useEffect(() => {
    if (!authenticated) {
      resetLockState();
      return;
    }
    void hydrate();
  }, [authenticated, hydrate, resetLockState]);

  return (
    <>
      {children}
      {authenticated && hydrated ? (
        <>
          <ScannerPinSetupOverlay />
          <ScannerLockOverlay />
        </>
      ) : null}
    </>
  );
}
