import { useState } from 'react';
import { Delete, UserRound } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { useSessionStore } from '@/stores/session';
import { cn } from '@/lib/utils';

interface TerminalSwitchResponse {
  accessToken: string;
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
  expiresInSeconds: number;
  tokenType: string;
  switchedFromUserId?: string | null;
}

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'] as const;

/**
 * Shared-terminal PIN pad — swaps operator JWT without killing the primary refresh session.
 */
export function TerminalPinPad({ warehouseSized = false }: { warehouseSized?: boolean }) {
  const [open, setOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const applyTerminalSwitch = useSessionStore((s) => s.applyTerminalSwitch);
  const restorePrimarySession = useSessionStore((s) => s.restorePrimarySession);
  const isTerminalSwitchActive = useSessionStore((s) => s.isTerminalSwitchActive);
  const user = useSessionStore((s) => s.user);

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
    } catch {
      setError('Invalid PIN');
      setPin('');
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
          onClick={() => restorePrimarySession()}
          data-testid="terminal-restore-primary"
        >
          Resume station
        </Button>
      </div>
    );
  }

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
        title="Switch operator with PIN"
      >
        <UserRound className="h-4 w-4" />
        <span className="hidden sm:inline">Switch operator</span>
      </Button>

      {open && (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-4 sm:items-center"
          role="dialog"
          aria-modal="true"
          aria-label="Terminal PIN pad"
          data-testid="terminal-pin-pad"
        >
          <div className="w-full max-w-sm rounded-lg border border-border bg-surface-raised p-4 shadow-lg">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-text">Operator PIN</h2>
              <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
                Close
              </Button>
            </div>
            <p className="mb-3 text-sm text-text-muted">
              Enter your 4-digit PIN. The station session stays signed in.
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
            <div className="grid grid-cols-3 gap-2">
              {KEYS.map((key) => (
                <button
                  key={key}
                  type="button"
                  disabled={busy}
                  onClick={() => onKey(key)}
                  className={cn(
                    'flex h-14 items-center justify-center rounded-md border border-border bg-surface-overlay text-xl font-semibold text-text',
                    'active:scale-[0.98] disabled:opacity-50'
                  )}
                >
                  {key === '⌫' ? <Delete className="h-5 w-5" /> : key}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
