import { useEffect, useRef, useState } from 'react';
import { ScannerPinKeypad } from '@/components/security/ScannerPinKeypad';
import { MAX_PIN_FAILURES, useScannerLockStore } from '@/stores/scannerLockStore';

function vibrateError(): void {
  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    navigator.vibrate([80, 40, 80]);
  }
}

/**
 * Full-viewport idle lock — blocks all floor interaction until the shift PIN unlocks
 * and reconstructs the AES-GCM key via PBKDF2.
 */
export function ScannerLockOverlay() {
  const isLocked = useScannerLockStore((s) => s.isLocked);
  const tryUnlock = useScannerLockStore((s) => s.tryUnlock);
  const failedAttempts = useScannerLockStore((s) => s.failedAttempts);
  const [pin, setPin] = useState('');
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const submittingRef = useRef(false);

  useEffect(() => {
    if (!isLocked) {
      setPin('');
      setError(false);
      submittingRef.current = false;
    }
  }, [isLocked]);

  useEffect(() => {
    if (pin.length !== 4 || submittingRef.current) return;
    submittingRef.current = true;
    setBusy(true);
    void tryUnlock(pin).then((result) => {
      if (result === 'ok') {
        setPin('');
        setError(false);
      } else if (result === 'bad') {
        vibrateError();
        setError(true);
        window.setTimeout(() => {
          setPin('');
          setError(false);
          submittingRef.current = false;
        }, 400);
      }
      setBusy(false);
      if (result === 'ok' || result === 'wiped') {
        submittingRef.current = false;
      }
    });
  }, [pin, tryUnlock]);

  if (!isLocked) return null;

  const remaining = Math.max(0, MAX_PIN_FAILURES - failedAttempts);

  return (
    <div
      className="fixed inset-0 z-50 flex h-screen w-screen items-center justify-center bg-surface"
      data-testid="scanner-lock-overlay"
      data-theme="warehouse"
      role="dialog"
      aria-modal="true"
      aria-label="Scanner locked"
    >
      <ScannerPinKeypad
        value={pin}
        error={error}
        disabled={busy}
        onChange={(next) => {
          setError(false);
          setPin(next);
        }}
        title="Scanner locked"
        subtitle={
          remaining < MAX_PIN_FAILURES
            ? `Wrong PIN — ${remaining} attempt${remaining === 1 ? '' : 's'} left`
            : 'Enter your 4-digit shift PIN'
        }
        testIdPrefix="scanner-unlock"
      />
    </div>
  );
}
