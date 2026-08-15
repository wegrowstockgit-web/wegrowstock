import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

const SEGMENT_LABELS = ['WH', 'ZONE', 'AISLE', 'BIN'] as const;

interface LocationBreadcrumbProps {
  locationPath: string;
  className?: string;
  /** Highlight the deepest (current) segment — default true for progressive path UI. */
  progressive?: boolean;
}

/**
 * Progressive visual breadcrumb for warehouse paths, e.g. WH-01 > Z-A > A-1 > B-01
 */
export function LocationBreadcrumb({
  locationPath,
  className,
  progressive = true,
}: LocationBreadcrumbProps) {
  const segments = locationPath.split('/').filter(Boolean);

  if (segments.length === 0) return null;

  return (
    <nav
      aria-label="Pick path"
      className={cn('flex flex-wrap items-center gap-0.5 text-sm', className)}
    >
      {segments.map((segment, index) => {
        const label = SEGMENT_LABELS[Math.min(index, SEGMENT_LABELS.length - 1)];
        const isLeaf = index === segments.length - 1;
        return (
          <span key={`${segment}-${index}`} className="inline-flex items-center gap-1">
            {index > 0 && (
              <ChevronRight
                className="mx-0.5 h-3.5 w-3.5 shrink-0 text-text-muted"
                aria-hidden
              />
            )}
            <span
              className={cn(
                'inline-flex items-baseline gap-1 rounded px-1.5 py-0.5 transition-colors duration-200',
                progressive && isLeaf && 'bg-accent/20 text-accent',
                progressive && !isLeaf && 'text-text-muted'
              )}
            >
              <span className="text-[10px] font-semibold uppercase tracking-wider opacity-70">
                {label}
              </span>
              <span
                className={cn(
                  'font-mono font-semibold',
                  progressive && isLeaf ? 'text-accent' : 'text-text'
                )}
              >
                {segment}
              </span>
            </span>
          </span>
        );
      })}
    </nav>
  );
}
