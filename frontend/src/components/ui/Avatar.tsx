import { useState } from 'react';
import { User } from 'lucide-react';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import { cn } from '@/lib/utils';

export interface AvatarProps {
  src?: string | null;
  alt?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const sizeClass = {
  sm: 'h-8 w-8',
  md: 'h-9 w-9',
  lg: 'h-11 w-11',
} as const;

const iconClass = {
  sm: 'h-4 w-4',
  md: 'h-4 w-4',
  lg: 'h-5 w-5',
} as const;

/** Compact avatar with Lucide user fallback (shadcn-style primitive). */
export function Avatar({ src, alt = 'User', size = 'md', className }: AvatarProps) {
  const [failed, setFailed] = useState(false);
  const hasImage = !!src?.trim() && !failed;

  return (
    <span
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full',
        'bg-surface-overlay text-text-muted ring-1 ring-border/70',
        sizeClass[size],
        className
      )}
      aria-label={alt}
      role="img"
    >
      {hasImage ? (
        <AuthenticatedImage
          src={src}
          alt=""
          className="h-full w-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <User className={iconClass[size]} aria-hidden />
      )}
    </span>
  );
}
