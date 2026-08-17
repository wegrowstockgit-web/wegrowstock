import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Check, Rows3 } from 'lucide-react';
import {
  ColumnVisibilityMenu,
  type ColumnVisibilityItem,
} from '@/components/ui/ColumnVisibilityMenu';
import { DENSITY_LABELS, DENSITY_MODES, type DensityMode } from '@/stores/preferencesStore';
import { useDensity } from '@/hooks/useDensity';
import { cn } from '@/lib/utils';

/**
 * Top-rail density control for Surface A master grids.
 * Pass `gridId` (or wrap the page in TableDensityScope) so the choice stays on that table.
 */
export function DensityToggle({
  className,
  gridId,
}: {
  className?: string;
  gridId?: string;
}) {
  const { densityMode, setDensityMode } = useDensity(gridId);
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const pick = (mode: DensityMode) => {
    setDensityMode(mode);
    setOpen(false);
  };

  return (
    <div ref={rootRef} className={cn('relative inline-flex', className)}>
      <button
        type="button"
        data-testid="density-toggle"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={`Table density: ${DENSITY_LABELS[densityMode]}`}
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'inline-flex h-9 items-center gap-2 rounded-md border border-border bg-surface-raised px-3 text-sm font-medium text-text',
          'hover:bg-surface-overlay focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/30',
          'active:scale-[0.98] transition-transform duration-150 ease-out',
        )}
      >
        <Rows3 className="h-4 w-4 text-text-muted" aria-hidden />
        <span className="hidden sm:inline">{DENSITY_LABELS[densityMode]}</span>
      </button>

      {open && (
        <ul
          role="listbox"
          aria-label="Table density"
          className="absolute right-0 top-full z-50 mt-1 min-w-[10rem] overflow-hidden rounded-md border border-border-strong bg-surface-raised py-1 shadow-elevated"
        >
          {DENSITY_MODES.map((mode) => {
            const selected = mode === densityMode;
            return (
              <li key={mode} role="option" aria-selected={selected}>
                <button
                  type="button"
                  data-testid={`density-option-${mode}`}
                  onClick={() => pick(mode)}
                  className={cn(
                    'flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm transition-colors',
                    selected
                      ? 'bg-accent-muted text-text'
                      : 'text-text hover:bg-surface-overlay',
                  )}
                >
                  {DENSITY_LABELS[mode]}
                  {selected && <Check className="h-3.5 w-3.5 text-accent" aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/** Flex row for list-page toolbars: filters on the left, views + columns + density on the right. */
export function DataListToolbar({
  children,
  className,
  columnItems,
  gridId = 'products',
  opsOnlyColumnIds,
  trailing,
}: {
  children?: ReactNode;
  className?: string;
  /** When provided, shows the Column Visibility Toggle menu in the action deck. */
  columnItems?: ColumnVisibilityItem[];
  /** Isolates column layout and Cozy/Compact/Spacious density for this grid. */
  gridId?: string;
  /** Ops-only preset ids for the Columns menu (Show all / Ops only). */
  opsOnlyColumnIds?: readonly string[];
  /** Extra toolbar controls rendered before Columns. */
  trailing?: ReactNode;
}) {
  return (
    <div
      className={cn(
        'mb-4 flex flex-wrap items-center justify-between gap-3',
        className,
      )}
      data-testid="data-list-toolbar"
    >
      <div className="min-w-0 flex-1">{children}</div>
      <div className="flex shrink-0 items-center gap-2">
        {trailing}
        {columnItems && columnItems.length > 0 && (
          <ColumnVisibilityMenu
            columns={columnItems}
            gridId={gridId}
            opsOnlyColumnIds={opsOnlyColumnIds}
          />
        )}
        <DensityToggle gridId={gridId} />
      </div>
    </div>
  );
}
