import { useCallback, useEffect, useId, useState } from 'react';
import { createPortal } from 'react-dom';
import { Delete, Fingerprint, UserRound } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { useSessionStore } from '@/stores/session';
import { cn } from '@/lib/utils';
import {
  clearTerminalPasskey,
  readTerminalPasskey,
  storeTerminalPasskey as persistTerminalPasskey,
} from '@/lib/terminalPasskey';

interface TerminalSwitchResponse {
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
  expiresInSeconds: number;
  tokenType: string;
  switchedFromUserId?: string | null;
}

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'] as const;

/** SHA-256 hex — mirrors TerminalBiometricService.computeAssertionSignature. */
async function sha256Hex(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

async function computeAssertionSignature(
  challenge: string,
  credentialId: string,
  secret: string
): Promise<string> {
  const secretHash = await sha256Hex(secret);
  return sha256Hex(`${challenge}:${credentialId}:${secretHash}`);
}

/**
 * Shared-terminal PIN / biometric pad — swaps operator JWT without killing the primary refresh session.
 * Modal is portaled to document.body so header backdrop-filter cannot clip `position: fixed`.
 */
export function TerminalPinPad({ warehouseSized = false }: { warehouseSized?: boolean }) {
  const titleId = useId();
  const [open, setOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const applyTerminalSwitch = useSessionStore((s) => s.applyTerminalSwitch);
  const restorePrimarySession = useSessionStore((s) => s.restorePrimarySession);
  const isTerminalSwitchActive = useSessionStore((s) => s.isTerminalSwitchActive);
  const user = useSessionStore((s) => s.user);

  const close = useCallback(() => {
    setOpen(false);
    setPin('');
    setError(null);
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
      }
    };
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = prevOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open, close]);

  const submit = async (value: string) => {
    if (value.length !== 4 || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await apiClient.post<TerminalSwitchResponse>('/api/v1/auth/terminal-switch', {
        pin: value,
      });
      applyTerminalSwitch(res.data, user?.email ?? 'operator');
      setPin('');
      setOpen(false);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      setError(status === 429 ? 'Too many attempts — wait and try again' : 'Invalid PIN');
      setPin('');
    } finally {
      setBusy(false);
    }
  };

  const submitBiometric = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const passkey = readTerminalPasskey();
      if (!passkey) {
        setError('No passkey registered on this terminal');
        return;
      }
      if (!user?.id || !user.tenantId) {
        setError('Station session required for passkey');
        return;
      }
      if (passkey.tenantId !== user.tenantId) {
        clearTerminalPasskey();
        setError('No passkey registered on this terminal');
        return;
      }
      const { credentialId, secret } = passkey;
      const options = await apiClient.post<{ challenge: string }>('/api/v1/auth/terminal-biometric/options');
      const signature = await computeAssertionSignature(options.data.challenge, credentialId, secret);
      const res = await apiClient.post<TerminalSwitchResponse>('/api/v1/auth/terminal-biometric', {
        credentialId,
        challenge: options.data.challenge,
        signature,
      });
      // Server maps credential → owner; reject if response user is outside this tenant.
      if (res.data.tenantId !== user.tenantId) {
        throw new Error('tenant mismatch');
      }
      applyTerminalSwitch(res.data, user?.email ?? 'operator');
      setOpen(false);
      setPin('');
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      setError(status === 429 ? 'Too many attempts — wait and try again' : 'Biometric assertion failed');
    } finally {
      setBusy(false);
    }
  };

  const onKey = (key: (typeof KEYS)[number]) => {
    setError(null);
    if (key === 'C') {
      setPin('');
      return;
    }
    if (key === '⌫') {
      setPin((p) => p.slice(0, -1));
      return;
    }
    setPin((p) => {
      if (p.length >= 4) return p;
      const next = p + key;
      if (next.length === 4) {
        void submit(next);
      }
      return next;
    });
  };

  if (isTerminalSwitchActive()) {
    return (
      <div className="flex items-center gap-2">
        <span
          className={cn('text-sm text-text-muted', warehouseSized && 'text-base')}
          data-terminal-operator="true"
        >
          {user?.displayName ?? user?.email}
        </span>
        <Button
          variant="secondary"
          size={warehouseSized ? 'md' : 'sm'}
          onClick={() => {
            void (async () => {
              try {
                await apiClient.post('/api/v1/auth/refresh', {});
              } catch {
                // cookie may still be valid for primary operator
              }
              restorePrimarySession();
            })();
          }}
          data-testid="terminal-restore-primary"
        >
          Resume station
        </Button>
      </div>
    );
  }

  const keyClass = cn(
    'flex items-center justify-center rounded-md border border-border bg-surface-overlay font-semibold text-text',
    'touch-target active:scale-[0.98] disabled:opacity-50',
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50',
    warehouseSized ? 'min-h-14 text-2xl' : 'min-h-12 text-xl'
  );

  const pad = open
    ? createPortal(
        <div
          className="fixed inset-0 z-[100] overflow-y-auto overscroll-contain bg-black/55"
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          data-testid="terminal-pin-pad"
        >
          <button
            type="button"
            className="fixed inset-0 cursor-default"
            aria-label="Dismiss PIN pad"
            onClick={close}
          />
          {/* min-h-full + items-center keeps the pad in view; outer overflow-y-auto
              prevents flex-centering from clipping the top row (1–3) on short viewports. */}
          <div className="relative flex min-h-full items-center justify-center p-4 sm:p-6">
            <div
              className={cn(
                'relative z-10 w-full max-w-sm rounded-xl border border-border bg-surface-raised p-4 shadow-elevated',
                'max-h-[min(92dvh,40rem)] overflow-y-auto'
              )}
              data-testid="terminal-pin-panel"
            >
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 id={titleId} className="text-lg font-semibold text-text">
                  Operator PIN
                </h2>
                <Button variant="ghost" size="sm" onClick={close} data-testid="terminal-pin-close">
                  Close
                </Button>
              </div>
              <p className="mb-3 text-sm text-text-muted">
                Enter your 4-digit PIN or tap passkey. The station session stays signed in.
              </p>
              <div
                className="mb-3 flex justify-center gap-2"
                aria-live="polite"
                data-testid="terminal-pin-dots"
              >
                {[0, 1, 2, 3].map((i) => (
                  <span
                    key={i}
                    className={cn(
                      'h-3 w-3 rounded-full border border-border',
                      i < pin.length ? 'bg-text' : 'bg-transparent'
                    )}
                  />
                ))}
              </div>
              {error && (
                <p className="mb-2 text-center text-sm text-danger" role="alert">
                  {error}
                </p>
              )}
              <div className="grid grid-cols-3 gap-2" data-testid="terminal-pin-keys">
                {KEYS.map((key) => (
                  <button
                    key={key}
                    type="button"
                    disabled={busy}
                    data-pin-key={key === '⌫' ? 'back' : key}
                    onClick={() => onKey(key)}
                    className={keyClass}
                  >
                    {key === '⌫' ? <Delete className="h-5 w-5" /> : key}
                  </button>
                ))}
              </div>
              <Button
                className="mt-3 min-h-11 w-full"
                variant="secondary"
                disabled={busy}
                onClick={() => void submitBiometric()}
                data-testid="terminal-biometric-assert"
              >
                <Fingerprint className="h-4 w-4" />
                Passkey / biometric
              </Button>
            </div>
          </div>
        </div>,
        document.body
      )
    : null;

  return (
    <>
      <Button
        variant="secondary"
        size={warehouseSized ? 'md' : 'sm'}
        onClick={() => {
          setOpen(true);
          setPin('');
          setError(null);
        }}
        data-testid="terminal-switch-open"
        title="Switch operator with PIN or passkey"
      >
        <UserRound className="h-4 w-4" />
        <span className="hidden sm:inline">Switch operator</span>
      </Button>
      {pad}
    </>
  );
}

/** Persist a one-time registration secret on this shared terminal (demo / e2e). */
export function storeTerminalPasskey(
  credentialId: string,
  secret: string,
  binding: { userId: string; tenantId: string }
) {
  persistTerminalPasskey(credentialId, secret, binding);
}

export { clearTerminalPasskey } from '@/lib/terminalPasskey';
