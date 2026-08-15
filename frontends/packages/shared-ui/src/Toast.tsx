import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

export type ToastTone = 'success' | 'danger' | 'info';

export type ToastMessage = {
  id: string;
  tone: ToastTone;
  message: string;
};

type ToastApi = {
  push: (tone: ToastTone, message: string) => void;
  success: (message: string) => void;
  danger: (message: string) => void;
  info: (message: string) => void;
};

const ToastContext = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastMessage[]>([]);

  const push = useCallback((tone: ToastTone, message: string) => {
    const id = crypto.randomUUID();
    setItems((prev) => [...prev, { id, tone, message }]);
    window.setTimeout(() => {
      setItems((prev) => prev.filter((t) => t.id !== id));
    }, 4200);
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
        className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
        aria-live="polite"
        data-testid="toast-region"
      >
        {items.map((t) => (
          <div
            key={t.id}
            role="status"
            className={
              t.tone === 'success'
                ? 'rounded border border-success/30 bg-success/10 px-3 py-2 text-sm text-success shadow'
                : t.tone === 'danger'
                  ? 'rounded border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger shadow'
                  : 'rounded border border-border bg-surface px-3 py-2 text-sm text-text shadow'
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
