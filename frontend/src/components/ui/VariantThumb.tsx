import { useState, type MouseEvent, type KeyboardEvent } from 'react';
import { Package } from 'lucide-react';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import { ImagePreviewToast } from '@/components/ui/ImagePreviewToast';
import { cn } from '@/lib/utils';

export interface VariantThumbProps {
  url?: string | null;
  alt?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  /** When true (default), clicking an image opens a responsive lightbox toast. */
  previewable?: boolean;
  /** Optional title shown in the preview chrome (e.g. SKU). */
  previewCaption?: string;
}

const sizeClass = {
  sm: 'h-8 w-8',
  md: 'h-12 w-12',
  lg: 'h-16 w-16',
} as const;

/** Inline product thumbnail with package icon fallback; optional click-to-enlarge. */
export function VariantThumb({
  url,
  alt = 'Product',
  size = 'sm',
  className,
  previewable = true,
  previewCaption,
}: VariantThumbProps) {
  const [failed, setFailed] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const hasImage = !!url?.trim() && !failed;
  const canPreview = previewable;

  const openPreview = (e: MouseEvent | KeyboardEvent) => {
    e.stopPropagation();
    e.preventDefault();
    if (canPreview) setPreviewOpen(true);
  };

  const thumb = (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center overflow-hidden rounded-md bg-surface-overlay text-text-muted',
        'ring-1 ring-border/60',
        sizeClass[size],
        canPreview && 'transition-opacity hover:opacity-90',
        className,
      )}
      aria-hidden={canPreview || !hasImage ? true : undefined}
    >
      {hasImage ? (
        <AuthenticatedImage
          src={url}
          alt=""
          className="h-full w-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <Package className={size === 'sm' ? 'h-3.5 w-3.5' : 'h-5 w-5'} aria-hidden />
      )}
    </span>
  );

  return (
    <>
      {canPreview ? (
        <button
          type="button"
          data-testid="variant-thumb-preview"
          className={cn(
            'inline-flex items-center justify-center rounded-md p-1.5',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50',
          )}
          aria-label={`View larger image: ${previewCaption ?? alt}`}
          onClick={openPreview}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') openPreview(e);
          }}
        >
          {thumb}
        </button>
      ) : (
        thumb
      )}

      <ImagePreviewToast
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        url={url}
        alt={alt}
        caption={previewCaption ?? alt}
      />
    </>
  );
}
