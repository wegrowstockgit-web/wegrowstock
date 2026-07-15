import {
  useEffect,
  useMemo,
  useRef,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useDensity } from '@/hooks/useDensity';
import { useGridColumnStore } from '@/stores/gridColumnStore';
import { cn } from '@/lib/utils';

export interface VirtualizedColumnDef<T> {
  id: string;
  header: ReactNode;
  /** Fixed width used for sticky left-offset calculation (px). */
  width: number;
  align?: 'left' | 'right' | 'center';
  /** When false, omitted from the column-visibility menu. Default true. */
  hideable?: boolean;
  className?: string;
  cell: (row: T, index: number) => ReactNode;
}

export interface VirtualizedTableProps<T> {
  columns: VirtualizedColumnDef<T>[];
  rows: T[];
  getRowId: (row: T) => string;
  onRowClick?: (row: T) => void;
  /** Fire near the end of the list (infinite scroll). */
  onEndReached?: () => void;
  endReachedThreshold?: number;
  className?: string;
  empty?: ReactNode;
  /** Extra toolbar actions rendered beside density / columns (optional). */
  toolbarSlot?: ReactNode;
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
    zIndex: isHeader ? 30 : 20,
  };
}

/**
 * Enterprise pinned-column virtualized data grid for Surface A.
 * Sticky left offsets are derived from column widths in pin order.
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
}: VirtualizedTableProps<T>) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const { rowPx, styles: densityStyles } = useDensity();
  const columnVisibility = useGridColumnStore((s) => s.columnVisibility);
  const pinnedColumns = useGridColumnStore((s) => s.pinnedColumns);
  const columnOrder = useGridColumnStore((s) => s.columnOrder);
  const ensureColumns = useGridColumnStore((s) => s.ensureColumns);

  const columnIds = useMemo(() => columns.map((c) => c.id), [columns]);

  useEffect(() => {
    ensureColumns(columnIds, {
      pinnedColumns: ['sku', 'name'].filter((id) => columnIds.includes(id)),
      columnOrder: columnIds,
    });
  }, [columnIds, ensureColumns]);

  const orderedVisible = useMemo(() => {
    const byId = new Map(columns.map((c) => [c.id, c]));
    const order =
      columnOrder.length > 0
        ? [
            ...columnOrder.filter((id) => byId.has(id)),
            ...columnIds.filter((id) => !columnOrder.includes(id)),
          ]
        : columnIds;

    // Pinned columns lead the row (stable freeze block).
    const pinned = pinnedColumns.filter(
      (id) => byId.has(id) && columnVisibility[id] !== false,
    );
    const rest = order.filter(
      (id) =>
        !pinned.includes(id) && byId.has(id) && columnVisibility[id] !== false,
    );
    return [...pinned, ...rest]
      .map((id) => byId.get(id)!)
      .filter(Boolean);
  }, [columns, columnIds, columnOrder, columnVisibility, pinnedColumns]);

  const pinOffsets = useMemo(() => {
    const offsets = new Map<string, number>();
    let left = 0;
    for (const col of orderedVisible) {
      if (!pinnedColumns.includes(col.id)) continue;
      offsets.set(col.id, left);
      left += col.width;
    }
    return offsets;
  }, [orderedVisible, pinnedColumns]);

  const lastPinnedId = useMemo(() => {
    const pinned = orderedVisible.filter((c) => pinnedColumns.includes(c.id));
    return pinned[pinned.length - 1]?.id;
  }, [orderedVisible, pinnedColumns]);

  const minTableWidth = useMemo(
    () => orderedVisible.reduce((sum, c) => sum + c.width, 0),
    [orderedVisible],
  );

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => Math.max(rowPx, 32),
    overscan: 12,
  });

  useEffect(() => {
    virtualizer.measure();
  }, [rowPx, virtualizer, orderedVisible.length]);

  const virtualItems = virtualizer.getVirtualItems();
  const paddingTop = virtualItems[0]?.start ?? 0;
  const paddingBottom =
    virtualizer.getTotalSize() - (virtualItems[virtualItems.length - 1]?.end ?? 0);
  const lastVisible = virtualItems[virtualItems.length - 1]?.index ?? -1;

  useEffect(() => {
    if (!onEndReached || lastVisible < 0) return;
    if (lastVisible >= rows.length - endReachedThreshold) {
      onEndReached();
    }
  }, [endReachedThreshold, lastVisible, onEndReached, rows.length]);

  if (rows.length === 0 && empty) {
    return <>{empty}</>;
  }

  return (
    <div
      className={cn(
        'overflow-x-auto overflow-y-hidden max-w-full containment-layout',
        'min-h-0 flex-1 bg-background',
        className,
      )}
      data-testid="virtualized-table"
    >
      <div
        ref={scrollRef}
        className="h-[calc(100vh-16rem)] max-h-full overflow-auto"
      >
        <table
          className={cn('w-full border-collapse', densityStyles.typography)}
          style={{ minWidth: minTableWidth }}
        >
          <thead className="sticky top-0 z-40">
            <tr
              className={cn(
                densityStyles.row,
                'border-b border-border bg-surface-overlay font-medium uppercase tracking-wide text-text-muted',
              )}
            >
              {orderedVisible.map((col) => {
                const pinned = pinOffsets.has(col.id);
                const isPinEdge = col.id === lastPinnedId;
                return (
                  <th
                    key={col.id}
                    scope="col"
                    className={cn(
                      'whitespace-nowrap font-medium',
                      densityStyles.cell,
                      alignClass(col.align),
                      pinned && 'bg-surface-overlay',
                      isPinEdge && PIN_EDGE,
                      col.className,
                    )}
                    style={{
                      width: col.width,
                      minWidth: col.width,
                      maxWidth: col.width,
                      ...(pinned ? stickyStyle(pinOffsets.get(col.id)!, true) : undefined),
                    }}
                  >
                    {col.header}
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
              const row = rows[virtualRow.index];
              if (!row) return null;

              const zebra = virtualRow.index % 2 === 1;
              const rowBg = zebra ? 'bg-muted/40' : 'bg-background';

              return (
                <tr
                  key={getRowId(row)}
                  data-index={virtualRow.index}
                  className={cn(
                    densityStyles.row,
                    rowBg,
                    'border-b border-border transition-colors',
                    onRowClick && 'cursor-pointer hover:bg-surface-overlay',
                  )}
                  style={{ height: virtualRow.size }}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {orderedVisible.map((col) => {
                    const pinned = pinOffsets.has(col.id);
                    const isPinEdge = col.id === lastPinnedId;
                    return (
                      <td
                        key={col.id}
                        className={cn(
                          'align-middle',
                          densityStyles.cell,
                          alignClass(col.align),
                          pinned && rowBg,
                          isPinEdge && PIN_EDGE,
                          col.className,
                        )}
                        style={{
                          width: col.width,
                          minWidth: col.width,
                          maxWidth: col.width,
                          ...(pinned
                            ? stickyStyle(pinOffsets.get(col.id)!, false)
                            : undefined),
                        }}
                      >
                        {col.cell(row, virtualRow.index)}
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
