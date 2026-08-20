import { useEffect, useRef, useState, type FormEvent } from 'react';
import { apiClient } from '@/api/client';
import { completeMfaAssertion, type MfaChallengeBody } from '@/features/settings/networkAccess';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

type UnlockOptions = MfaChallengeBody & {
  hasPasskey?: boolean;
};

type DesktopLockOverlayProps = {
  open: boolean;
  onUnlocked: () => void;
};

export function DesktopLockOverlay({ open, onUnlocked }: DesktopLockOverlayProps) {
  const [options, setOptions] = useState<UnlockOptions | null>(null);
  const [usePassword, setUsePassword] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const autoStarted = useRef(false);

  useEffect(() => {
    if (!open) {
      setOptions(null);
      setUsePassword(false);
      setPassword('');
      setError('');
      setBusy(false);
      autoStarted.current = false;
      return;
    }
    let cancelled = false;
    void apiClient
      .get<UnlockOptions>('/api/v1/auth/desktop-unlock/options')
      .then((res) => {
        if (cancelled) return;
        setOptions(res.data);
        setUsePassword(!res.data.hasPasskey);
      })
      .catch(() => {
        if (!cancelled) setUsePassword(true);
      });
    return () => {
      cancelled = true;
    };
  }, [open]);

  useEffect(() => {
    if (!open || !options?.hasPasskey || usePassword || autoStarted.current) return;
    autoStarted.current = true;
    void (async () => {
      setBusy(true);
      setError('');
      try {
        const assertion = await completeMfaAssertion(options);
        await apiClient.post('/api/v1/auth/desktop-unlock', {
          mfaCredentialId: assertion.mfaCredentialId,
          mfaChallenge: assertion.mfaChallenge,
          mfaSignature: assertion.mfaSignature,
        });
        onUnlocked();
      } catch {
        setUsePassword(true);
        setError('Biometric unlock failed. Enter your password.');
      } finally {
        setBusy(false);
      }
    })();
  }, [open, options, usePassword, onUnlocked]);

  const submitPassword = async (e: FormEvent) => {
    e.preventDefault();
    if (!password.trim()) return;
    setBusy(true);
    setError('');
    try {
      await apiClient.post('/api/v1/auth/desktop-unlock', { password });
      onUnlocked();
    } catch {
      setError('Password is incorrect.');
    } finally {
      setBusy(false);
    }
  };

  if (!open) return null;

  const showPasskey = Boolean(options?.hasPasskey) && !usePassword;

  return (
    <div
      className="fixed inset-0 z-[100] flex h-screen w-screen items-center justify-center bg-surface/70 p-4 backdrop-blur-md"
      data-testid="desktop-lock-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="desktop-lock-title"
    >
      <div className="w-full max-w-sm rounded-xl border border-border bg-surface-raised p-6 shadow-elevated">
        <h2 id="desktop-lock-title" className="text-lg font-semibold text-text">
          Session locked
        </h2>
        <p className="mt-1 text-sm text-text-muted">
          Confirm it is you to restore this workspace exactly where you left it.
        </p>

        {showPasskey ? (
          <div className="mt-5 space-y-3">
            <p className="text-sm text-text">Touch your passkey or use device biometrics.</p>
            <Button
              type="button"
              variant="secondary"
              className="w-full"
              onClick={() => {
                setBusy(false);
                setUsePassword(true);
              }}
              data-testid="desktop-unlock-use-password"
            >
              Use password instead
            </Button>
          </div>
        ) : (
          <form className="mt-5 space-y-4" onSubmit={(e) => void submitPassword(e)}>
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(ev) => setPassword(ev.target.value)}
              autoFocus
              data-testid="desktop-unlock-password"
            />
            {error ? (
              <p className="text-sm text-danger" data-testid="desktop-unlock-error">
                {error}
              </p>
            ) : null}
            <Button type="submit" className="w-full" loading={busy} data-testid="desktop-unlock-submit">
              Unlock
            </Button>
            {options?.hasPasskey ? (
              <Button
                type="button"
                variant="ghost"
                className="w-full"
                onClick={() => {
                  setUsePassword(false);
                  setError('');
                  autoStarted.current = false;
                }}
              >
                Use passkey
              </Button>
            ) : null}
          </form>
        )}
      </div>
    </div>
  );
}
