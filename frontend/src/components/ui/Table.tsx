import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useDensity } from '@/hooks/useDensity';
import type { SortState } from '@/hooks/useClientSort';
import { cn } from '@/lib/utils';

interface TableProps {
  children: ReactNode;
  className?: string;
}

export function Table({ children, className }: TableProps) {
  const { tableClass } = useDensity();
  return (
    // No overflow here — the page/panel provides the single scrollport.
    // A nested overflow-auto (plus page overflow-auto) caused double scrollbars.
    // Sticky <th> pins to that outer scrollport when this shell stays overflow:visible.
    <div className="w-full rounded-lg border border-border" data-table-shell="true">
      <table
        className={cn('w-full border-separate border-spacing-0', tableClass, className)}
      >
        {children}
      </table>
    </div>
  );
}

export function TableHeader({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <thead className={cn('table-head-accent', className)}>
      {children}
    </thead>
  );
}

export function TableBody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-border">{children}</tbody>;
}

export function TableRow({
  children,
  className,
  onClick,
  selected = false,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
  selected?: boolean;
}) {
  const { rowClass } = useDensity();
  return (
    <tr
      data-state={selected ? 'selected' : undefined}
      aria-selected={selected || undefined}
      className={cn(
        'table-row-interactive bg-surface-raised',
        'focus-within:outline-none focus-within:ring-2 focus-within:ring-inset focus-within:ring-accent/40',
        rowClass,
        onClick && 'cursor-pointer',
        className,
      )}
      onClick={onClick}
    >
      {children}
    </tr>
  );
}

export function TableHead({
  children,
  className,
  align = 'left',
  sortable = false,
  sortKey,
  sort,
  onSort,
}: {
  children: ReactNode;
  className?: string;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  sortKey?: string;
  sort?: SortState | null;
  onSort?: (key: string) => void;
}) {
  const { headClass } = useDensity();
  const alignClass = {
    left: 'text-left',
    right: 'text-right',
    center: 'text-center',
  };
  const active = sortable && sortKey && sort?.key === sortKey;
  const ariaSort = active ? (sort!.dir === 'asc' ? 'ascending' : 'descending') : sortable ? 'none' : undefined;

  const label = (
    <span className="inline-flex items-center gap-1.5">
      <span>{children}</span>
      {sortable && (
        <span className="inline-flex opacity-90" aria-hidden>
          {active ? (
            sort!.dir === 'asc' ? (
              <ArrowUp className="h-3.5 w-3.5" />
            ) : (
              <ArrowDown className="h-3.5 w-3.5" />
            )
          ) : (
            <ArrowUpDown className="h-3.5 w-3.5 opacity-70" />
          )}
        </span>
      )}
    </span>
  );

  return (
    <th
      scope="col"
      aria-sort={ariaSort}
      className={cn(
        'table-head-cell sticky top-0 z-20 font-semibold uppercase tracking-wide text-[var(--color-table-header-fg)]',
        'bg-[var(--color-table-header)]',
        headClass,
        alignClass[align],
        className,
      )}
    >
      {sortable && sortKey && onSort ? (
        <button
          type="button"
          className={cn(
            'inline-flex min-h-11 w-full items-center gap-1.5 touch-target',
            'text-[var(--color-table-header-fg)] hover:opacity-90',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70',
            align === 'right' && 'justify-end',
            align === 'center' && 'justify-center',
          )}
          onClick={() => onSort(sortKey)}
        >
          {label}
        </button>
      ) : (
        label
      )}
    </th>
  );
}

export function TableCell({
  children,
  className,
  align = 'left',
  mono = false,
  colSpan,
}: {
  children: ReactNode;
  className?: string;
  align?: 'left' | 'right' | 'center';
  mono?: boolean;
  colSpan?: number;
}) {
  const { cellClass } = useDensity();
  const alignClass = {
    left: 'text-left',
    right: 'text-right',
    center: 'text-center',
  };

  return (
    <td
      colSpan={colSpan}
      className={cn(
        'text-text',
        cellClass,
        alignClass[align],
        mono && 'font-mono tabular-nums',
        className,
      )}
    >
      {children}
    </td>
  );
}
