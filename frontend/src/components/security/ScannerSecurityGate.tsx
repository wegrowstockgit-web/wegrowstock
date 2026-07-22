import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { ScannerLockOverlay } from '@/components/security/ScannerLockOverlay';
import { ScannerPinSetupOverlay } from '@/components/security/ScannerPinSetupOverlay';
import { isFloorRoute } from '@/lib/floorRoutes';
import { useScannerIdle } from '@/hooks/useScannerIdle';
import { installMutationQueueTestHook } from '@/offline/mutationQueue';
import {
  installScannerLockTestHook,
  useScannerLockStore,
} from '@/stores/scannerLockStore';
import { useIsAuthenticated } from '@/stores/session';

/**
 * Orchestrates shift PIN setup, idle cryptographic wipe, and unlock overlays
 * for Surface B / handheld scanner routes only — never on office dashboard.
 */
export function ScannerSecurityGate({ children }: { children: ReactNode }) {
  const location = useLocation();
  const onFloor = isFloorRoute(location.pathname);
  const authenticated = useIsAuthenticated();
  const hydrate = useScannerLockStore((s) => s.hydrate);
  const hydrated = useScannerLockStore((s) => s.hydrated);
  const resetLockState = useScannerLockStore((s) => s.resetLockState);

  useScannerIdle({ enabled: onFloor });

  useEffect(() => {
    installScannerLockTestHook();
    installMutationQueueTestHook();
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
      {authenticated && hydrated && onFloor ? (
        <>
          <ScannerPinSetupOverlay />
          <ScannerLockOverlay />
        </>
      ) : null}
    </>
  );
}
