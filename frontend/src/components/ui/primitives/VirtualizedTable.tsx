import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useDensity } from '@/hooks/useDensity';
import { useClientSort, type SortAccessors } from '@/hooks/useClientSort';
import { selectGridLayout, useGridColumnStore } from '@/stores/gridColumnStore';
import { cn } from '@/lib/utils';

export interface VirtualizedColumnDef<T> {
  id: string;
  header: ReactNode;
  /** Base width used for sticky left-offset calculation (px). */
  width: number;
  /**
   * When true, this column absorbs leftover viewport width so the grid
   * fills the screen instead of leaving a blank strip on the right.
   */
  flexGrow?: boolean;
  align?: 'left' | 'right' | 'center';
  /** When false, omitted from the column-visibility menu. Default true. */
  hideable?: boolean;
  /** When true, column starts hidden until toggled via Columns. */
  defaultHidden?: boolean;
  /** Enables header click sort when sortValue is provided. */
  sortable?: boolean;
  sortValue?: (row: T) => string | number | boolean | null | undefined | Date;
  className?: string;
  cell: (row: T, index: number) => ReactNode;
}

export interface VirtualizedTableProps<T> {
  columns: VirtualizedColumnDef<T>[];
  rows: T[];
  getRowId: (row: T) => string;
  onRowClick?: (row: T) => void;
  /** Controlled selection highlight (row id from getRowId). */
  selectedRowId?: string | null;
  /** Fire near the end of the list (infinite scroll). */
  onEndReached?: () => void;
  endReachedThreshold?: number;
  className?: string;
  empty?: ReactNode;
  /** Extra toolbar actions rendered beside density / columns (optional). */
  toolbarSlot?: ReactNode;
  /** Isolates column layout in localStorage (default: products). */
  gridId?: string;
}

const PIN_EDGE =
  'shadow-[inset_-8px_0_8px_-8px_rgba(0,0,0,0.2)] border-r border-border/80';

function alignClass(align: 'left' | 'right' | 'center' = 'left') {
  return {
    left: 'text-left',
    right: 'text-right',
    center: 'text-center',
  }[align];
}

function stickyStyle(left: number, isHeader: boolean): CSSProperties {
  return {
    position: 'sticky',
    left,
    ...(isHeader ? { top: 0 } : undefined),
    zIndex: isHeader ? 50 : 20,
  };
}

/**
 * Enterprise pinned-column virtualized data grid for Surface A.
 * Outer shell is overflow-hidden / fixed-viewport; only the inner scrollport
 * rolls. Near-end scroll triggers TanStack infinite append via onEndReached.
 */
export function VirtualizedTable<T>({
  columns,
  rows,
  getRowId,
  onRowClick,
  onEndReached,
  endReachedThreshold = 5,
  className,
  empty,
  selectedRowId,
  gridId = 'products',
}: VirtualizedTableProps<T>) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const endReachedLock = useRef(false);
  const { rowPx, styles: densityStyles } = useDensity();
  const layout = useGridColumnStore((s) => selectGridLayout(s, gridId));
  const columnVisibility = layout.columnVisibility;
  const pinnedColumns = layout.pinnedColumns;
  const columnOrder = layout.columnOrder;
  const ensureColumns = useGridColumnStore((s) => s.ensureColumns);
  const [internalSelected, setInternalSelected] = useState<string | null>(null);
  /** Viewport width of the table scrollport — used to stretch columns to fill. */
  const [viewportWidth, setViewportWidth] = useState(0);

  const controlled = selectedRowId !== undefined;
  const activeSelected = controlled ? selectedRowId : internalSelected;

  const columnIds = useMemo(() => columns.map((c) => c.id), [columns]);

  const sortAccessors = useMemo(() => {
    const map: SortAccessors<T> = {};
    for (const col of columns) {
      if (col.sortable !== false && col.sortValue) {
        map[col.id] = col.sortValue;
      }
    }
    return map;
  }, [columns]);

  const { sort, toggle, sorted: sortedRows } = useClientSort(rows, sortAccessors);

  const defaultColumnVisibility = useMemo(() => {
    const visibility: Record<string, boolean> = {};
    for (const col of columns) {
      if (col.defaultHidden) {
        visibility[col.id] = false;
      }
    }
    return visibility;
  }, [columns]);

  useEffect(() => {
    ensureColumns(gridId, columnIds, {
      pinnedColumns: ['sku', 'name'].filter((id) => columnIds.includes(id)),
      columnOrder: columnIds,
      columnVisibility: defaultColumnVisibility,
    });
  }, [columnIds, defaultColumnVisibility, ensureColumns, gridId]);

  const orderedVisible = useMemo(() => {
    const byId = new Map(columns.map((c) => [c.id, c]));
    const order =
      columnOrder.length > 0
        ? [
            ...columnOrder.filter((id) => byId.has(id)),
            ...columnIds.filter((id) => !columnOrder.includes(id)),
          ]
        : columnIds;

    const isVisible = (id: string) =>
      byId.has(id) && columnVisibility[id] !== false;

    // Non-hideable leading columns (e.g. product thumb) stay before pinned
    // identifiers — never squeezed between Name and Barcode.
    const leadingFixed = order.filter((id) => {
      const col = byId.get(id);
      return Boolean(col && col.hideable === false && isVisible(id));
    });
    const pinned = pinnedColumns.filter(
      (id) => isVisible(id) && !leadingFixed.includes(id),
    );
    const rest = order.filter(
      (id) =>
        isVisible(id) && !leadingFixed.includes(id) && !pinned.includes(id),
    );
    return [...leadingFixed, ...pinned, ...rest]
      .map((id) => byId.get(id)!)
      .filter(Boolean);
  }, [columns, columnIds, columnOrder, columnVisibility, pinnedColumns]);

  /** Sticky freeze group: leading fixed columns + user-pinned identifiers. */
  const stickyColumnIds = useMemo(() => {
    const ids: string[] = [];
    for (const col of orderedVisible) {
      if (col.hideable === false || pinnedColumns.includes(col.id)) {
        ids.push(col.id);
      } else {
        break;
      }
    }
    return ids;
  }, [orderedVisible, pinnedColumns]);

  const minTableWidth = useMemo(
    () => orderedVisible.reduce((sum, c) => sum + c.width, 0),
    [orderedVisible],
  );

  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const measure = () => setViewportWidth(el.clientWidth);
    measure();
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    ro?.observe(el);
    return () => ro?.disconnect();
  }, []);

  /** Resolved px widths — grow flex columns (default: name) so the grid fills the viewport. */
  const columnWidths = useMemo(() => {
    const widths = new Map<string, number>();
    for (const col of orderedVisible) {
      widths.set(col.id, col.width);
    }
    const target = Math.max(viewportWidth, minTableWidth);
    const extra = target - minTableWidth;
    if (extra <= 0 || orderedVisible.length === 0) return widths;

    const growCandidates = orderedVisible.filter(
      (c) => c.flexGrow || c.id === 'name',
    );
    const growers =
      growCandidates.length > 0
        ? growCandidates
        : orderedVisible.filter((c) => !stickyColumnIds.includes(c.id));
    if (growers.length === 0) {
      const last = orderedVisible[orderedVisible.length - 1]!;
      widths.set(last.id, last.width + extra);
      return widths;
    }
    const share = Math.floor(extra / growers.length);
    let remainder = extra - share * growers.length;
    for (const col of growers) {
      const bump = share + (remainder > 0 ? 1 : 0);
      if (remainder > 0) remainder -= 1;
      widths.set(col.id, (widths.get(col.id) ?? col.width) + bump);
    }
    return widths;
  }, [orderedVisible, stickyColumnIds, viewportWidth, minTableWidth]);

  const tableWidth = useMemo(() => {
    let sum = 0;
    for (const col of orderedVisible) {
      sum += columnWidths.get(col.id) ?? col.width;
    }
    return Math.max(sum, viewportWidth, minTableWidth);
  }, [orderedVisible, columnWidths, viewportWidth, minTableWidth]);

  const pinOffsets = useMemo(() => {
    const offsets = new Map<string, number>();
    let left = 0;
    for (const id of stickyColumnIds) {
      const w = columnWidths.get(id) ?? orderedVisible.find((c) => c.id === id)?.width ?? 0;
      offsets.set(id, left);
      left += w;
    }
    return offsets;
  }, [orderedVisible, stickyColumnIds, columnWidths]);

  const lastPinnedId = stickyColumnIds[stickyColumnIds.length - 1];

  const virtualizer = useVirtualizer({
    count: sortedRows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => rowPx,
    overscan: 12,
    scrollMargin: 0,
  });

  useEffect(() => {
    virtualizer.measure();
  }, [rowPx, virtualizer, orderedVisible.length, tableWidth]);

  const virtualItems = virtualizer.getVirtualItems();
  const paddingTop = virtualItems[0]?.start ?? 0;
  const paddingBottom =
    virtualizer.getTotalSize() - (virtualItems[virtualItems.length - 1]?.end ?? 0);
  const lastVisible = virtualItems[virtualItems.length - 1]?.index ?? -1;

  useEffect(() => {
    endReachedLock.current = false;
  }, [sortedRows.length]);

  useEffect(() => {
    if (!onEndReached || lastVisible < 0 || endReachedLock.current) return;
    if (lastVisible >= sortedRows.length - endReachedThreshold) {
      endReachedLock.current = true;
      onEndReached();
      const unlock = window.setTimeout(() => {
        endReachedLock.current = false;
      }, 400);
      return () => window.clearTimeout(unlock);
    }
  }, [endReachedThreshold, lastVisible, onEndReached, sortedRows.length]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el || !onEndReached) return;

    const onScroll = () => {
      const remaining = el.scrollHeight - el.scrollTop - el.clientHeight;
      if (remaining <= Math.max(rowPx * endReachedThreshold, 48) && !endReachedLock.current) {
        endReachedLock.current = true;
        onEndReached();
        window.setTimeout(() => {
          endReachedLock.current = false;
        }, 400);
      }
    };

    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  }, [endReachedThreshold, onEndReached, rowPx, sortedRows.length]);

  if (sortedRows.length === 0 && empty) {
    return <>{empty}</>;
  }

  const handleRowClick = (row: T) => {
    const id = getRowId(row);
    if (!controlled) {
      setInternalSelected(id);
    }
    onRowClick?.(row);
  };

  return (
    <div
      className={cn(
        'flex min-h-0 flex-1 flex-col overflow-hidden max-w-full containment-layout',
        'bg-background',
        className,
      )}
      data-testid="virtualized-table"
    >
      {/* Sole scrollport — page/window stays static; table stretches to viewport width */}
      <div
        ref={scrollRef}
        className="min-h-0 flex-1 overflow-auto overscroll-contain"
        data-testid="virtualized-table-scrollport"
      >
        <table
          className={cn(
            'w-full border-separate border-spacing-0 table-fixed',
            densityStyles.typography,
          )}
          style={{ width: tableWidth, minWidth: '100%' }}
          data-testid="virtualized-table-grid"
          data-visible-columns={orderedVisible.map((c) => c.id).join(',')}
          data-table-width={String(tableWidth)}
        >
          <thead className="table-head-accent">
            <tr
              className={cn(
                densityStyles.row,
                'border-b border-[#155a9c] font-semibold uppercase tracking-wide',
              )}
            >
              {orderedVisible.map((col) => {
                const pinned = pinOffsets.has(col.id);
                const isPinEdge = col.id === lastPinnedId;
                const canSort = Boolean(col.sortValue) && col.sortable !== false;
                const active = canSort && sort?.key === col.id;
                const colW = columnWidths.get(col.id) ?? col.width;
                return (
                  <th
                    key={col.id}
                    scope="col"
                    data-column-id={col.id}
                    data-pinned={pinned ? 'true' : undefined}
                    aria-sort={
                      active ? (sort!.dir === 'asc' ? 'ascending' : 'descending') : canSort ? 'none' : undefined
                    }
                    className={cn(
                      'table-head-cell sticky top-0 z-40 overflow-hidden whitespace-nowrap bg-[var(--color-table-header)] text-[var(--color-table-header-fg)]',
                      densityStyles.cell,
                      alignClass(col.align),
                      isPinEdge && PIN_EDGE,
                      col.className,
                    )}
                    style={{
                      width: colW,
                      minWidth: colW,
                      maxWidth: colW,
                      ...(pinned ? stickyStyle(pinOffsets.get(col.id)!, true) : undefined),
                    }}
                  >
                    {canSort ? (
                      <button
                        type="button"
                        className={cn(
                          'inline-flex min-h-11 w-full items-center gap-1.5 touch-target',
                          'text-[var(--color-table-header-fg)] hover:opacity-90',
                          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70',
                          col.align === 'right' && 'justify-end',
                          col.align === 'center' && 'justify-center',
                        )}
                        onClick={() => toggle(col.id)}
                      >
                        <span>{col.header}</span>
                        {active ? (
                          sort!.dir === 'asc' ? (
                            <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                          ) : (
                            <ArrowDown className="h-3.5 w-3.5" aria-hidden />
                          )
                        ) : (
                          <ArrowUpDown className="h-3.5 w-3.5 opacity-70" aria-hidden />
                        )}
                      </button>
                    ) : (
                      col.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {paddingTop > 0 && (
              <tr aria-hidden>
                <td
                  colSpan={orderedVisible.length}
                  style={{ height: paddingTop, padding: 0, border: 'none' }}
                />
              </tr>
            )}
            {virtualItems.map((virtualRow) => {
              const row = sortedRows[virtualRow.index];
              if (!row) return null;

              const rowId = getRowId(row);
              const selected = activeSelected === rowId;
              const zebra = virtualRow.index % 2 === 1;
              const rowBg = selected
                ? 'bg-blue-100/60 dark:bg-slate-700/80'
                : zebra
                  ? 'bg-muted/40'
                  : 'bg-background';

              return (
                <tr
                  key={rowId}
                  data-index={virtualRow.index}
                  data-state={selected ? 'selected' : undefined}
                  aria-selected={selected || undefined}
                  className={cn(
                    'table-row-interactive density-row',
                    densityStyles.row,
                    rowBg,
                    'border-b border-border',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent/40',
                    onRowClick && 'cursor-pointer',
                  )}
                  style={{ height: virtualRow.size }}
                  tabIndex={onRowClick ? 0 : undefined}
                  onClick={onRowClick ? () => handleRowClick(row) : undefined}
                  onKeyDown={
                    onRowClick
                      ? (e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            handleRowClick(row);
                          }
                        }
                      : undefined
                  }
                >
                  {orderedVisible.map((col) => {
                    const pinned = pinOffsets.has(col.id);
                    const isPinEdge = col.id === lastPinnedId;
                    const colW = columnWidths.get(col.id) ?? col.width;
                    return (
                      <td
                        key={col.id}
                        className={cn(
                          'align-middle overflow-hidden',
                          densityStyles.cell,
                          alignClass(col.align),
                          pinned && rowBg,
                          isPinEdge && PIN_EDGE,
                          col.className,
                        )}
                        style={{
                          width: colW,
                          minWidth: colW,
                          maxWidth: colW,
                          ...(pinned
                            ? stickyStyle(pinOffsets.get(col.id)!, false)
                            : undefined),
                        }}
                      >
                        <div className="min-w-0 max-w-full overflow-hidden">
                          {col.cell(row, virtualRow.index)}
                        </div>
                      </td>
                    );
                  })}
                </tr>
              );
            })}
            {paddingBottom > 0 && (
              <tr aria-hidden>
                <td
                  colSpan={orderedVisible.length}
                  style={{ height: paddingBottom, padding: 0, border: 'none' }}
                />
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Columns eligible for the visibility toggle (hideable !== false). */
export function hideableColumnMeta(columns: VirtualizedColumnDef<unknown>[]) {
  return columns
    .filter((c) => c.hideable !== false)
    .map((c) => ({
      id: c.id,
      label: typeof c.header === 'string' ? c.header : c.id,
    }));
}
