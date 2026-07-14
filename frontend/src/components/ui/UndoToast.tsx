import { useEffect, useState } from 'react';
import { RotateCcw, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';

export interface UndoToastProps {
  message: string;
  visible: boolean;
  durationMs?: number;
  onUndo: () => void;
  onDismiss: () => void;
}

export function UndoToast({
  message,
  visible,
  durationMs = 5000,
  onUndo,
  onDismiss,
}: UndoToastProps) {
  const [progress, setProgress] = useState(100);

  useEffect(() => {
    if (!visible) {
      setProgress(100);
      return;
    }
    const started = Date.now();
    const tick = window.setInterval(() => {
      const elapsed = Date.now() - started;
      const remaining = Math.max(0, 100 - (elapsed / durationMs) * 100);
      setProgress(remaining);
      if (remaining <= 0) {
        window.clearInterval(tick);
        onDismiss();
      }
    }, 50);
    return () => window.clearInterval(tick);
  }, [visible, durationMs, onDismiss]);

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'fixed bottom-6 left-1/2 z-50 w-[min(24rem,calc(100vw-2rem))] -translate-x-1/2 overflow-hidden rounded-lg border border-border bg-surface-raised shadow-elevated transition-all duration-300',
        visible ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-4 opacity-0'
      )}
    >
      <div className="flex items-center justify-between gap-3 px-4 py-3">
        <p className="text-sm text-text">{message}</p>
        <div className="flex shrink-0 gap-1">
          <Button variant="secondary" size="sm" onClick={onUndo}>
            <RotateCcw className="h-3.5 w-3.5" />
            Undo
          </Button>
          <Button variant="ghost" size="sm" onClick={onDismiss} aria-label="Dismiss">
            <X className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
      <div className="h-1 bg-surface-overlay">
        <div
          className="h-full bg-accent transition-all duration-75 ease-linear"
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  );
}
