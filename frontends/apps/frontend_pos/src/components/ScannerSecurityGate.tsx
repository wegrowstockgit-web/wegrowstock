import { useEffect, useState, type ReactNode } from 'react';
import { ScannerPinKeypad } from '@/components/ScannerPinKeypad';
import { usePosSession } from '@/lib/PosSessionContext';
import { downloadCatalog } from '@/lib/syncWorker';
import { seedDemoManagerPinsIfEmpty, validateManagerPin } from '@/offline/pinVault';

export const POS_SHIFT_UNLOCK_KEY = 'pos.shiftUnlocked';

export function isShiftUnlocked(): boolean {
  if (typeof sessionStorage === 'undefined') return false;
  return sessionStorage.getItem(POS_SHIFT_UNLOCK_KEY) === '1';
}

export function lockShift(): void {
  if (typeof sessionStorage === 'undefined') return;
  sessionStorage.removeItem(POS_SHIFT_UNLOCK_KEY);
}

export function unlockShift(): void {
  if (typeof sessionStorage === 'undefined') return;
  sessionStorage.setItem(POS_SHIFT_UNLOCK_KEY, '1');
}

export function ScannerSecurityGate({ children }: { children: ReactNode }) {
  const { t } = usePosSession();
  const [unlocked, setUnlocked] = useState(isShiftUnlocked);
  const [pin, setPin] = useState('');
  const [error, setError] = useState(false);

  useEffect(() => {
    seedDemoManagerPinsIfEmpty();
  }, []);

  useEffect(() => {
    if (!unlocked) return;
    void downloadCatalog().catch(() => undefined);
  }, [unlocked]);

  useEffect(() => {
    if (pin.length !== 4) return;
    const managerId = validateManagerPin(pin);
    if (managerId) {
      unlockShift();
      setUnlocked(true);
      setError(false);
      setPin('');
      return;
    }
    setError(true);
    setPin('');
  }, [pin]);

  if (unlocked) return children;

  return (
    <div className="pos-login-shell" data-testid="pos-pin-gate">
      <div className="pos-login-card">
        <p className="pos-kicker">weGrowStock</p>
        <ScannerPinKeypad
          value={pin}
          error={error}
          onChange={(next) => {
            setError(false);
            setPin(next);
          }}
          title={t('pin.title')}
          subtitle={t('pin.subtitle')}
        />
        {error ? (
          <p className="pos-login-error" data-testid="pos-pin-error">
            {t('pin.error')}
          </p>
        ) : null}
      </div>
    </div>
  );
}
