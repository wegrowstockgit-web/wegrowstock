import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { useUiActionTrackerStore } from '@/stores/uiActionTrackerStore';

export type ToastTone = 'default' | 'success' | 'danger';

export interface ToastItem {
  id: string;
  message: string;
  tone?: ToastTone;
  durationMs?: number;
  leaving?: boolean;
}

type ToastShow = (message: string, opts?: { tone?: ToastTone; durationMs?: number }) => void;

interface ToastContextValue {
  toast: ToastShow;
}

interface GlobalToast extends ToastShow {
  error: (message: string) => void;
}

let boundToast: ToastShow | null = null;

export function bindToastImpl(impl: ToastShow | null): void {
  boundToast = impl;
}

/** Imperative toast for Axios interceptors and non-React callers. */
export const toast: GlobalToast = Object.assign(
  ((message: string, opts?: { tone?: ToastTone; durationMs?: number }) => {
    boundToast?.(message, opts);
  }) as ToastShow,
  {
    error: (message: string) => {
      boundToast?.(message, { tone: 'danger' });
    },
  },
);

const ToastContext = createContext<ToastContextValue | null>(null);

/** Exit is faster than enter so dismissal feels like acknowledgment, not latency. */
export const TOAST_EXIT_MS = 150;

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const timers = useRef<Map<string, number>>(new Map());

  const clearTimer = useCallback((id: string) => {
    const handle = timers.current.get(id);
    if (handle != null) {
      window.clearTimeout(handle);
      timers.current.delete(id);
    }
  }, []);

  const remove = useCallback(
    (id: string) => {
      clearTimer(id);
      setItems((prev) => prev.filter((t) => t.id !== id));
    },
    [clearTimer],
  );

  const dismiss = useCallback(
    (id: string) => {
      setItems((prev) => {
        const current = prev.find((t) => t.id === id);
        if (!current || current.leaving) return prev;
        return prev.map((t) => (t.id === id ? { ...t, leaving: true } : t));
      });
      clearTimer(id);
      const wait = prefersReducedMotion() ? 0 : TOAST_EXIT_MS;
      timers.current.set(id, window.setTimeout(() => remove(id), wait));
    },
    [clearTimer, remove],
  );

  const toast = useCallback(
    (message: string, opts?: { tone?: ToastTone; durationMs?: number }) => {
      const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      const durationMs = opts?.durationMs ?? 3500;
      const tone = opts?.tone ?? 'default';
      let added = false;
      setItems((prev) => {
        // Interceptor + local onError can fire the same RFC 7807 detail together.
        if (prev.some((t) => t.message === message && !t.leaving)) return prev;
        added = true;
        return [...prev, { id, message, tone, durationMs }];
      });
      if (!added) return;
      if (tone === 'danger') {
        useUiActionTrackerStore.getState().trackAction({
          actionType: 'TOAST_ERROR',
          elementLabel: 'Error toast',
          errorMessage: message,
        });
      }
      timers.current.set(id, window.setTimeout(() => dismiss(id), durationMs));
    },
    [dismiss],
  );

  useEffect(() => {
    const stored = timers.current;
    return () => {
      stored.forEach((handle) => window.clearTimeout(handle));
      stored.clear();
    };
  }, []);

  const value = useMemo(() => ({ toast }), [toast]);

  useEffect(() => {
    bindToastImpl(toast);
    return () => bindToastImpl(null);
  }, [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed right-6 z-[200] flex w-[min(24rem,calc(100vw-2rem))] flex-col items-stretch gap-2"
        style={{ top: 'calc(var(--header-height, 3.5rem) + 0.75rem)' }}
        aria-live="polite"
        data-testid="toast-region"
      >
        {items.map((item) => (
          <div
            key={item.id}
            role="status"
            data-testid="app-toast"
            data-toast-state={item.leaving ? 'leave' : 'enter'}
            className={cn(
              'pointer-events-auto flex items-center justify-between gap-3 rounded-lg border px-4 py-3 shadow-elevated',
              item.leaving ? 'toast-slide-out' : 'toast-slide-in',
              item.tone === 'success' && 'border-success/30 bg-surface-raised text-text',
              item.tone === 'danger' && 'border-danger/40 bg-surface-raised text-text',
              item.tone === 'default' && 'border-border bg-surface-raised text-text',
            )}
          >
            <p className="text-sm">{item.message}</p>
            <Button variant="ghost" size="sm" onClick={() => dismiss(item.id)} aria-label="Dismiss">
              <X className="h-3.5 w-3.5" />
            </Button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
