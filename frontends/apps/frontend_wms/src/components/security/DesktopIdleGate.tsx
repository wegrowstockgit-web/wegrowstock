import { IdleWarningModal } from '@/components/security/IdleWarningModal';
import { DesktopLockOverlay } from '@/components/security/DesktopLockOverlay';
import { useDesktopIdle } from '@/hooks/useDesktopIdle';

/** Office-only idle warning + biometric-first lock. Mounted from AppShell. */
export function DesktopIdleGate() {
  const idle = useDesktopIdle();

  return (
    <>
      <IdleWarningModal
        open={idle.isWarningPhase && !idle.isLocked}
        onStaySignedIn={idle.staySignedIn}
      />
      <DesktopLockOverlay open={idle.isLocked} onUnlocked={idle.unlock} />
    </>
  );
}
