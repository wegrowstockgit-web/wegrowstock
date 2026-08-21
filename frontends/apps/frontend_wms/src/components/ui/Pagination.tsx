import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { PAGE_SIZES, type PageSize } from '@/hooks/useServerTable';
import { cn } from '@/lib/utils';

function pageItems(current: number, total: number): Array<number | 'ellipsis'> {
  if (total <= 0) return [];
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  const pages = new Set<number>([1, total, current, current - 1, current + 1]);
  const sorted = [...pages].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);
  const out: Array<number | 'ellipsis'> = [];
  for (const page of sorted) {
    const prev = out[out.length - 1];
    if (typeof prev === 'number' && page - prev > 1) {
      out.push('ellipsis');
    }
    out.push(page);
  }
  return out;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
  className,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  className?: string;
}) {
  const last = Math.max(totalPages, 1);
  const current = Math.min(Math.max(page, 1), last);
  const from = totalElements === 0 ? 0 : (current - 1) * size + 1;
  const to = Math.min(current * size, totalElements);

  return (
    <div
      className={cn(
        'mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/60 pt-4',
        className,
      )}
      data-testid="pagination"
    >
      <p className="text-sm text-text-muted" data-testid="pagination-summary">
        {totalElements === 0 ? 'No rows' : `Showing ${from}–${to} of ${totalElements}`}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-2 text-sm text-text-muted">
          Rows
          <select
            data-testid="page-size"
            className="h-9 rounded-md border border-border bg-surface-raised px-2 text-sm text-text"
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
          >
            {PAGE_SIZES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            data-testid="pagination-prev"
            disabled={current <= 1}
            onClick={() => onPageChange(current - 1)}
            aria-label="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
            Previous
          </Button>
          {pageItems(current, last).map((item, index) =>
            item === 'ellipsis' ? (
              <span key={`e-${index}`} className="px-1 text-text-muted">
                …
              </span>
            ) : (
              <Button
                key={item}
                type="button"
                variant={item === current ? 'primary' : 'secondary'}
                size="sm"
                data-testid={`pagination-page-${item}`}
                aria-current={item === current ? 'page' : undefined}
                onClick={() => onPageChange(item)}
              >
                {item}
              </Button>
            ),
          )}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            data-testid="pagination-next"
            disabled={current >= last}
            onClick={() => onPageChange(current + 1)}
            aria-label="Next page"
          >
            Next
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}

export type { PageSize };
