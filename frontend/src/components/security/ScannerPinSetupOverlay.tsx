import { useEffect, useRef, useState } from 'react';
import { ScannerPinKeypad } from '@/components/security/ScannerPinKeypad';
import { useScannerLockStore } from '@/stores/scannerLockStore';

/**
 * First-shift PIN enrollment — PIN salts PBKDF2 → AES-GCM key for offline IDB.
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

  useEffect(() => {
    if (phase === 'create' && pin.length === 4) {
      setPhase('confirm');
    }
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
      }, 400);
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

  return (
    <div
      className="fixed inset-0 z-50 flex h-screen w-screen items-center justify-center bg-surface"
      data-testid="scanner-pin-setup-overlay"
      data-theme="warehouse"
      role="dialog"
      aria-modal="true"
      aria-label="Set scanner PIN"
    >
      <ScannerPinKeypad
        value={phase === 'create' ? pin : confirm}
        error={error}
        disabled={busy}
        onChange={(next) => {
          setError(false);
          if (phase === 'create') setPin(next);
          else setConfirm(next);
        }}
        title={phase === 'create' ? 'Set shift PIN' : 'Confirm PIN'}
        subtitle="This PIN unlocks offline scans after idle lock"
        testIdPrefix="scanner-setup"
      />
    </div>
  );
}
