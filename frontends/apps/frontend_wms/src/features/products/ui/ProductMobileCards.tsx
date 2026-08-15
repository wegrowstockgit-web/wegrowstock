import { useEffect, useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { MapPin, Package } from 'lucide-react';
import type { ProductVariant } from '@/api/types';
import { cn } from '@/lib/utils';

function qty(value: number | null | undefined): string {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return String(value);
}

/**
 * Phone (<768px) product browse — discrete touch blocks instead of a flat table.
 * Virtualized for large catalogs; each card keeps SKU/Name + stock metrics.
 */
export function ProductMobileCards({
  rows,
  selectedRowId,
  onRowClick,
  onEndReached,
}: {
  rows: ProductVariant[];
  selectedRowId?: string | null;
  onRowClick?: (row: ProductVariant) => void;
  onEndReached?: () => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const endLock = useRef(false);

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => 120,
    overscan: 8,
    getItemKey: (index) => rows[index]?.id ?? index,
  });

  const items = virtualizer.getVirtualItems();
  const last = items[items.length - 1]?.index ?? -1;

  useEffect(() => {
    endLock.current = false;
  }, [rows.length]);

  useEffect(() => {
    if (!onEndReached || last < 0 || endLock.current) return;
    if (last >= rows.length - 4) {
      endLock.current = true;
      onEndReached();
      const t = window.setTimeout(() => {
        endLock.current = false;
      }, 400);
      return () => window.clearTimeout(t);
    }
  }, [last, onEndReached, rows.length]);

  if (rows.length === 0) {
    return (
      <div
        className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center"
        data-testid="products-mobile-empty"
      >
        <Package className="h-10 w-10 text-text-muted" aria-hidden />
        <p className="text-sm text-text-muted">No products yet</p>
      </div>
    );
  }

  return (
    <div
      ref={scrollRef}
      className="min-h-0 min-w-0 flex-1 overflow-y-auto overscroll-contain px-3 pb-4 pt-1"
      data-testid="products-mobile-list"
      data-layout="mobile"
    >
      <div className="relative w-full" style={{ height: virtualizer.getTotalSize() }}>
        {items.map((virtualRow) => {
          const product = rows[virtualRow.index];
          if (!product) return null;
          const selected = selectedRowId === product.id;
          const locationLabel =
            product.storageTempZone?.trim() ||
            (product.defaultLocationId
              ? `Loc ${product.defaultLocationId.slice(0, 8)}`
              : 'Floor');

          return (
            <button
              key={product.id}
              type="button"
              data-testid="products-mobile-card"
              data-row-id={product.id}
              aria-selected={selected || undefined}
              onClick={() => onRowClick?.(product)}
              className={cn(
                'absolute left-0 right-0 flex w-full flex-col gap-2 rounded-lg border border-border/80 bg-surface-raised px-3 py-3 text-left',
                'min-h-12 transition-[transform,colors] duration-150 ease-out active:scale-[0.99]',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50',
                selected && 'border-accent/50 bg-accent/5',
              )}
              style={{
                height: virtualRow.size - 8,
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              <div className="flex min-w-0 items-start justify-between gap-3">
                <div className="min-w-0 flex-1 overflow-hidden">
                  <p className="truncate font-mono text-sm font-bold text-text">{product.sku}</p>
                  <p className="mt-0.5 truncate text-sm text-text-muted" title={product.name}>
                    {product.name}
                  </p>
                </div>
                <div className="shrink-0 text-right tabular-nums">
                  <p className="text-[10px] font-semibold uppercase tracking-wide text-text-muted">
                    ATP
                  </p>
                  <p className="text-base font-bold text-text">{qty(product.atp)}</p>
                </div>
              </div>

              <div className="flex min-w-0 items-center justify-between gap-2">
                <span
                  className="inline-flex max-w-[55%] items-center gap-1 truncate rounded-md bg-surface-overlay px-2 py-1 text-xs font-medium text-text"
                  data-testid="products-mobile-location"
                >
                  <MapPin className="h-3.5 w-3.5 shrink-0 text-accent" aria-hidden />
                  <span className="truncate">{locationLabel}</span>
                </span>
                <div className="flex shrink-0 items-center gap-3 text-xs tabular-nums text-text-muted">
                  <span>
                    OH <strong className="text-text">{qty(product.onHand)}</strong>
                  </span>
                  <span>
                    Alloc <strong className="text-text">{qty(product.allocated)}</strong>
                  </span>
                </div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
