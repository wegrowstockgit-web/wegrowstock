import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';

export type ToastTone = 'default' | 'success' | 'danger';

export interface ToastItem {
  id: string;
  message: string;
  tone?: ToastTone;
  durationMs?: number;
}

interface ToastContextValue {
  toast: (message: string, opts?: { tone?: ToastTone; durationMs?: number }) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: string) => {
    setItems((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, opts?: { tone?: ToastTone; durationMs?: number }) => {
      const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      const durationMs = opts?.durationMs ?? 3500;
      setItems((prev) => [...prev, { id, message, tone: opts?.tone ?? 'default', durationMs }]);
      window.setTimeout(() => dismiss(id), durationMs);
    },
    [dismiss]
  );

  const value = useMemo(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed bottom-6 left-1/2 z-[60] flex w-[min(24rem,calc(100vw-2rem))] -translate-x-1/2 flex-col gap-2"
        aria-live="polite"
      >
        {items.map((item) => (
          <div
            key={item.id}
            role="status"
            className={cn(
              'pointer-events-auto flex items-center justify-between gap-3 rounded-lg border px-4 py-3 shadow-elevated',
              item.tone === 'success' && 'border-success/30 bg-surface-raised text-text',
              item.tone === 'danger' && 'border-danger/40 bg-surface-raised text-text',
              item.tone === 'default' && 'border-border bg-surface-raised text-text'
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
