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

export type ToastTone = 'success' | 'danger' | 'info';

export type ToastMessage = {
  id: string;
  tone: ToastTone;
  message: string;
  leaving?: boolean;
};

type ToastApi = {
  push: (tone: ToastTone, message: string) => void;
  success: (message: string) => void;
  danger: (message: string) => void;
  info: (message: string) => void;
};

const ToastContext = createContext<ToastApi | null>(null);

const EXIT_MS = 150;

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastMessage[]>([]);
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
      const wait = prefersReducedMotion() ? 0 : EXIT_MS;
      timers.current.set(id, window.setTimeout(() => remove(id), wait));
    },
    [clearTimer, remove],
  );

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = crypto.randomUUID();
      setItems((prev) => [...prev, { id, tone, message }]);
      timers.current.set(id, window.setTimeout(() => dismiss(id), 4200));
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

  const api = useMemo<ToastApi>(
    () => ({
      push,
      success: (m) => push('success', m),
      danger: (m) => push('danger', m),
      info: (m) => push('info', m),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="pointer-events-none fixed right-6 z-[200] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
        style={{ top: 'calc(var(--header-height, 3.5rem) + 0.75rem)' }}
        aria-live="polite"
        data-testid="toast-region"
      >
        {items.map((t) => (
          <div
            key={t.id}
            role="status"
            data-toast-state={t.leaving ? 'leave' : 'enter'}
            className={
              (t.leaving ? 'toast-slide-out ' : 'toast-slide-in ') +
              (t.tone === 'success'
                ? 'rounded border border-success/30 bg-success/10 px-3 py-2 text-sm text-success shadow'
                : t.tone === 'danger'
                  ? 'rounded border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger shadow'
                  : 'rounded border border-border bg-surface px-3 py-2 text-sm text-text shadow')
            }
          >
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return ctx;
}
