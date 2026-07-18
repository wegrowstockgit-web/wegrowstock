import { useEffect, useRef } from 'react';
import { X, Package } from 'lucide-react';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

export interface ImagePreviewToastProps {
  open: boolean;
  onClose: () => void;
  url?: string | null;
  alt?: string;
  caption?: string;
}

/**
 * Responsive image lightbox presented as a floating toast-style overlay.
 * Uses native &lt;dialog&gt; for focus trap + Esc; full-bleed on phones, capped on desktop.
 */
export function ImagePreviewToast({
  open,
  onClose,
  url,
  alt = 'Product image',
  caption,
}: ImagePreviewToastProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const hasImage = !!url?.trim();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || !open) return;
    if (!dialog.open) {
      dialog.showModal();
    }
  }, [open]);

  // Mount only while open so virtualized grids do not leave N closed <dialog> nodes in the DOM.
  if (!open) return null;

  return (
    <dialog
      ref={ref}
      data-testid="image-preview-toast"
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      className={cn(
        'm-auto max-h-[min(92dvh,56rem)] w-[min(100vw-1.5rem,40rem)] border-0 bg-transparent p-0 text-text',
        'backdrop:bg-black/55 backdrop:backdrop-blur-[3px]',
        'open:flex open:flex-col open:items-stretch',
      )}
      aria-label={caption ? `Preview: ${caption}` : 'Image preview'}
    >
      <div
        className={cn(
          'relative flex max-h-[min(92dvh,56rem)] flex-col overflow-hidden rounded-xl',
          'border border-border/80 bg-surface-raised shadow-elevated',
        )}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-border px-3 py-2.5 sm:px-4">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-text">
              {caption ?? 'Product image'}
            </p>
            <p className="text-xs text-text-muted">Tap outside or press Esc to close</p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onClose}
            aria-label="Close image preview"
            data-testid="image-preview-close"
            className="min-h-11 min-w-11 shrink-0 touch-target"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>

        <div
          className={cn(
            'flex min-h-[12rem] flex-1 items-center justify-center bg-surface-overlay/40 p-3 sm:p-6',
            'max-h-[calc(min(92dvh,56rem)-4.5rem)] overflow-auto',
          )}
        >
          {hasImage ? (
            <AuthenticatedImage
              src={url}
              alt={alt}
              className="max-h-[min(70dvh,40rem)] w-auto max-w-full rounded-md object-contain"
            />
          ) : (
            <div className="flex flex-col items-center gap-2 text-text-muted">
              <Package className="h-12 w-12" aria-hidden />
              <p className="text-sm">No image available</p>
            </div>
          )}
        </div>
      </div>
    </dialog>
  );
}
