import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

const SEGMENT_LABELS = ['WH', 'ZONE', 'AISLE', 'BIN'] as const;

interface LocationBreadcrumbProps {
  locationPath: string;
  className?: string;
}

export function LocationBreadcrumb({ locationPath, className }: LocationBreadcrumbProps) {
  const segments = locationPath.split('/').filter(Boolean);

  if (segments.length === 0) return null;

  return (
    <nav aria-label="Pick path" className={cn('flex flex-wrap items-center gap-1 text-sm', className)}>
      {segments.map((segment, index) => {
        const label = SEGMENT_LABELS[Math.min(index, SEGMENT_LABELS.length - 1)];
        return (
          <span key={`${segment}-${index}`} className="inline-flex items-center gap-1">
            {index > 0 && <ChevronRight className="h-3.5 w-3.5 text-text-muted" aria-hidden />}
            <span className="text-xs font-medium uppercase tracking-wide text-text-muted">{label}</span>
            <span className="font-mono font-semibold text-text">{segment}</span>
          </span>
        );
      })}
    </nav>
  );
}
