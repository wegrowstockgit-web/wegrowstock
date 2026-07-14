import { Package } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface VariantThumbProps {
  url?: string | null;
  alt?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const sizeClass = {
  sm: 'h-8 w-8',
  md: 'h-12 w-12',
  lg: 'h-16 w-16',
} as const;

/** Inline product thumbnail with package icon fallback. */
export function VariantThumb({ url, alt = 'Product', size = 'sm', className }: VariantThumbProps) {
  const hasImage = !!url?.trim();

  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center overflow-hidden rounded-md bg-surface-overlay text-text-muted',
        'ring-1 ring-border/60',
        sizeClass[size],
        className
      )}
      aria-hidden={!hasImage}
    >
      {hasImage ? (
        <img
          src={url!}
          alt={alt}
          className="h-full w-full object-cover"
          loading="lazy"
          onError={(e) => {
            e.currentTarget.style.display = 'none';
          }}
        />
      ) : (
        <Package className={size === 'sm' ? 'h-3.5 w-3.5' : 'h-5 w-5'} />
      )}
    </span>
  );
}
