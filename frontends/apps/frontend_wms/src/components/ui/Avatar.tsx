import { useState, type ReactNode } from 'react';
import { User } from 'lucide-react';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import { cn } from '@/lib/utils';

const sizeClass = {
  sm: 'h-8 w-8 text-xs',
  md: 'h-9 w-9 text-sm',
  lg: 'h-11 w-11 text-base',
} as const;

const iconClass = {
  sm: 'h-4 w-4',
  md: 'h-4 w-4',
  lg: 'h-5 w-5',
} as const;

export interface AvatarProps {
  src?: string | null;
  alt?: string;
  fallback?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  children?: ReactNode;
}

/** Root avatar shell (shadcn-style composition). */
export function Avatar({
  src,
  alt = 'User',
  fallback,
  size = 'md',
  className,
  children,
}: AvatarProps) {
  const [failed, setFailed] = useState(false);
  const hasImage = !!src?.trim() && !failed;

  if (children) {
    return (
      <span
        className={cn(
          'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full',
          'bg-surface-overlay text-text-muted ring-1 ring-border/70',
          sizeClass[size],
          className,
        )}
        aria-label={alt}
        role="img"
      >
        {children}
      </span>
    );
  }

  return (
    <span
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full',
        'bg-surface-overlay text-text-muted ring-1 ring-border/70',
        sizeClass[size],
        className,
      )}
      aria-label={alt}
      role="img"
    >
      {hasImage ? (
        <AvatarImage src={src!} alt="" onError={() => setFailed(true)} />
      ) : (
        <AvatarFallback>{fallback ?? <User className={iconClass[size]} aria-hidden />}</AvatarFallback>
      )}
    </span>
  );
}

export function AvatarImage({
  src,
  alt = '',
  className,
  onError,
}: {
  src: string;
  alt?: string;
  className?: string;
  onError?: () => void;
}) {
  return (
    <AuthenticatedImage
      src={src}
      alt={alt}
      className={cn('h-full w-full object-cover', className)}
      onError={onError}
    />
  );
}

export function AvatarFallback({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <span className={cn('flex h-full w-full items-center justify-center font-medium uppercase', className)}>
      {children}
    </span>
  );
}

export function initialsFromName(name?: string | null, email?: string | null): string {
  const source = (name ?? email ?? '').trim();
  if (!source) return '';
  const parts = source.split(/[\s@._-]+/).filter(Boolean);
  if (parts.length === 0) return source.slice(0, 2).toUpperCase();
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return `${parts[0]![0] ?? ''}${parts[1]![0] ?? ''}`.toUpperCase();
}
