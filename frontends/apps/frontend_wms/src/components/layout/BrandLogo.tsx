import { useId } from 'react';
import { useSessionStore } from '@/stores/session';
import { cn } from '@/lib/utils';

interface BrandLogoProps {
  compact?: boolean;
  inverted?: boolean;
  /** Smaller mark + wordmark for the office rail. */
  size?: 'sm' | 'md';
  className?: string;
}

export function BrandLogo({
  compact = false,
  inverted = false,
  size = 'md',
  className,
}: BrandLogoProps) {
  const tier = useSessionStore((s) => s.user?.tier);
  const titleClass = inverted ? 'text-white' : 'text-text';
  const mutedClass = inverted ? 'text-white/70' : 'text-text-muted';
  const dense = size === 'sm';

  return (
    <div
      className={cn('flex items-center', dense ? 'gap-2' : 'gap-2.5', className)}
      data-testid="brand-logo"
    >
      <BrandMark inverted={inverted} size={size} />
      {!compact && (
        <div className="shrink-0 leading-tight">
          <p
            className={cn(
              'whitespace-nowrap font-bold tracking-tight',
              dense ? 'text-[0.9375rem]' : 'text-base',
              titleClass,
            )}
          >
            weGrowStock
          </p>
          {tier ? (
            <span
              className={cn(
                'mt-0.5 inline-flex whitespace-nowrap rounded-full px-1.5 py-px text-[10px] font-semibold uppercase tracking-wide',
                inverted
                  ? 'bg-white/20 text-white ring-1 ring-white/30'
                  : 'bg-accent-muted text-accent ring-1 ring-accent/20',
              )}
              data-testid="tier-badge"
            >
              {tier}
            </span>
          ) : (
            <span className={cn('mt-0.5 block text-[10px] font-medium uppercase tracking-wide', mutedClass)}>
              WMS
            </span>
          )}
        </div>
      )}
    </div>
  );
}

function BrandMark({ inverted, size }: { inverted: boolean; size: 'sm' | 'md' }) {
  const reactId = useId().replace(/:/g, '');
  const stackId = `wgs-stack-${reactId}`;
  const arrowId = `wgs-arrow-${reactId}`;
  const dense = size === 'sm';
  return (
    <div
      className={cn(
        'relative flex shrink-0 items-center justify-center overflow-hidden rounded-xl shadow-card',
        dense ? 'h-9 w-9' : 'h-11 w-11',
        inverted ? 'bg-white/20 ring-1 ring-white/30' : 'bg-accent text-text-inverse',
      )}
      title="weGrowStock"
      aria-hidden
    >
      <svg
        viewBox="0 0 32 32"
        className={cn(dense ? 'h-6 w-6' : 'h-7 w-7', inverted ? 'text-white' : 'text-accent')}
        fill="none"
      >
        <defs>
          <linearGradient id={stackId} x1="6" y1="28" x2="26" y2="6" gradientUnits="userSpaceOnUse">
            <stop stopColor="currentColor" stopOpacity="0.55" />
            <stop offset="1" stopColor="currentColor" stopOpacity="1" />
          </linearGradient>
          <linearGradient id={arrowId} x1="16" y1="22" x2="16" y2="4" gradientUnits="userSpaceOnUse">
            <stop stopColor={inverted ? '#ffffff' : '#f8fafc'} stopOpacity="0.85" />
            <stop offset="1" stopColor={inverted ? '#ffffff' : '#ffffff'} />
          </linearGradient>
        </defs>
        <rect x="5" y="18" width="14" height="8" rx="1.5" fill={`url(#${stackId})`} opacity="0.55" />
        <rect x="8" y="13" width="14" height="8" rx="1.5" fill={`url(#${stackId})`} opacity="0.8" />
        <rect x="11" y="8" width="14" height="8" rx="1.5" fill={`url(#${stackId})`} />
        <path
          d="M22 20.5V9.5L26.5 14"
          stroke={`url(#${arrowId})`}
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M22 9.5h5.2"
          stroke={`url(#${arrowId})`}
          strokeWidth="2.2"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}
