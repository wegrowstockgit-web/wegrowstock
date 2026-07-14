import { useEffect, type ReactNode } from 'react';
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
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  return (
    <div
      className={cn(
        'fixed inset-0 z-50',
        open ? 'pointer-events-auto' : 'pointer-events-none'
      )}
      aria-hidden={!open}
    >
      <button
        type="button"
        className={cn(
          'absolute inset-0 bg-text/40 transition-opacity duration-200 ease-out',
          'motion-reduce:transition-none',
          open ? 'opacity-100' : 'opacity-0'
        )}
        onClick={onClose}
        aria-label="Close drawer"
        tabIndex={open ? 0 : -1}
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="slide-out-title"
        data-testid="right-peek-drawer"
        className={cn(
          'absolute right-0 top-0 flex h-full flex-col border-l border-border bg-surface-raised shadow-elevated',
          'transition-transform duration-200 ease-[cubic-bezier(0.16,1,0.3,1)]',
          'motion-reduce:transition-none',
          width === 'lg' ? 'w-full max-w-xl' : 'w-full max-w-md',
          open ? 'translate-x-0' : 'translate-x-full'
        )}
      >
        {open && (
          <>
            <div className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
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
            <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
          </>
        )}
      </aside>
    </div>
  );
}
