import { useEffect, useRef, useState } from 'react';
import { ScannerPinKeypad } from '@/components/security/ScannerPinKeypad';
import { useScannerLockStore } from '@/stores/scannerLockStore';

/**
 * First-shift PIN enrollment — PIN salts PBKDF2 → AES-GCM key for offline IDB.
 * Two steps: choose PIN, then re-enter to confirm (not a failed first attempt).
 */
export function ScannerPinSetupOverlay() {
  const needsPinSetup = useScannerLockStore((s) => s.needsPinSetup);
  const setupPin = useScannerLockStore((s) => s.setupPin);
  const [pin, setPin] = useState('');
  const [confirm, setConfirm] = useState('');
  const [phase, setPhase] = useState<'create' | 'confirm'>('create');
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const committingRef = useRef(false);

  useEffect(() => {
    if (!needsPinSetup) {
      setPin('');
      setConfirm('');
      setPhase('create');
      setError(false);
      committingRef.current = false;
    }
  }, [needsPinSetup]);

  // Advance to confirm after 4 digits — brief beat so the filled dots are noticeable.
  useEffect(() => {
    if (phase !== 'create' || pin.length !== 4) return;
    const id = window.setTimeout(() => {
      setConfirm('');
      setError(false);
      setPhase('confirm');
    }, 180);
    return () => window.clearTimeout(id);
  }, [phase, pin]);

  useEffect(() => {
    if (phase !== 'confirm' || confirm.length !== 4 || committingRef.current) return;
    if (confirm !== pin) {
      setError(true);
      window.setTimeout(() => {
        setConfirm('');
        setPin('');
        setPhase('create');
        setError(false);
      }, 450);
      return;
    }
    committingRef.current = true;
    setBusy(true);
    void setupPin(pin).finally(() => {
      setBusy(false);
      committingRef.current = false;
    });
  }, [phase, confirm, pin, setupPin]);

  if (!needsPinSetup) return null;

  const isConfirm = phase === 'confirm';

  return (
    <div
      className="fixed inset-0 z-50 flex h-screen w-screen items-center justify-center bg-surface"
      data-testid="scanner-pin-setup-overlay"
      data-phase={phase}
      data-theme="warehouse"
      role="dialog"
      aria-modal="true"
      aria-label={isConfirm ? 'Confirm scanner PIN' : 'Set scanner PIN'}
    >
      <ScannerPinKeypad
        value={isConfirm ? confirm : pin}
        error={error}
        disabled={busy || (phase === 'create' && pin.length === 4)}
        onChange={(next) => {
          setError(false);
          if (phase === 'create') setPin(next);
          else setConfirm(next);
        }}
        title={isConfirm ? 'Confirm PIN' : 'Set shift PIN'}
        subtitle={
          isConfirm
            ? 'Enter the same 4 digits again to save'
            : 'Choose a 4-digit PIN, then you will confirm it'
        }
        stepLabel={isConfirm ? 'Step 2 of 2' : 'Step 1 of 2'}
        testIdPrefix="scanner-setup"
      />
    </div>
  );
}
