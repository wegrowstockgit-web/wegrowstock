import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';

export interface SlideOutDrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  width?: 'md' | 'lg';
}

/**
 * Right-peek drawer with token-based motion. Respects prefers-reduced-motion.
 * Portaled to document.body so header overflow cannot trap `fixed`.
 */
export function SlideOutDrawer({
  open,
  onClose,
  title,
  description,
  children,
  width = 'md',
}: SlideOutDrawerProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <div className="fixed inset-0 z-[80] pointer-events-auto" data-testid="slide-out-drawer-root">
      <button
        type="button"
        className="absolute inset-0 bg-text/40 transition-opacity duration-200 ease-out motion-reduce:transition-none"
        onClick={onClose}
        aria-label="Close drawer"
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="slide-out-title"
        data-testid="right-peek-drawer"
        className={cn(
          'absolute flex w-full flex-col border-border bg-surface-raised shadow-elevated',
          'transition-transform duration-200 ease-[cubic-bezier(0.16,1,0.3,1)] motion-reduce:transition-none',
          'inset-x-0 bottom-0 top-auto h-[90vh] max-h-[90vh] max-w-none rounded-t-2xl border-t',
          'translate-y-0',
          'md:inset-y-0 md:right-0 md:left-auto md:top-0 md:h-full md:max-h-[100dvh] md:rounded-none md:border-l md:border-t-0',
          'md:translate-x-0 md:translate-y-0',
          width === 'lg' ? 'md:max-w-xl' : 'md:max-w-md',
        )}
      >
        <div className="flex shrink-0 items-start justify-between gap-4 border-b border-border px-5 py-4">
          <div className="min-w-0">
            <h2 id="slide-out-title" className="truncate text-lg font-semibold text-text">
              {title}
            </h2>
            {description && <p className="mt-0.5 text-sm text-text-muted">{description}</p>}
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="Close">
            <X className="h-4 w-4" />
          </Button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
      </aside>
    </div>,
    document.body,
  );
}
