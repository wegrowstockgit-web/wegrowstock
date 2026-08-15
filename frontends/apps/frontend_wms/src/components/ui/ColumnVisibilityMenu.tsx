import { memo } from 'react';
import { Columns3 } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  selectColumnPinned,
  selectColumnVisible,
  useGridColumnStore,
} from '@/stores/gridColumnStore';
import { cn } from '@/lib/utils';

export interface ColumnVisibilityItem {
  id: string;
  label: string;
}

interface ColumnVisibilityMenuProps {
  columns: ColumnVisibilityItem[];
  /** Isolates localStorage layout per table (default: products). */
  gridId?: string;
  className?: string;
  /**
   * When set, shows Show all / Ops only presets. Ops only keeps these ids
   * visible and hides every other toggleable column.
   */
  opsOnlyColumnIds?: readonly string[];
}

interface AtomicColumnToggleProps {
  gridId: string;
  columnId: string;
  label: string;
}

/** One checkbox — re-renders only when this column's visibility/pin flips. */
const AtomicVisibilityToggle = memo(function AtomicVisibilityToggle({
  gridId,
  columnId,
  label,
}: AtomicColumnToggleProps) {
  const visible = useGridColumnStore((s) => selectColumnVisible(s, gridId, columnId));
  const pinned = useGridColumnStore((s) => selectColumnPinned(s, gridId, columnId));
  const toggleColumnVisibility = useGridColumnStore((s) => s.toggleColumnVisibility);

  return (
    <DropdownMenuCheckboxItem
      checked={visible}
      disabled={pinned}
      onCheckedChange={() => toggleColumnVisibility(gridId, columnId)}
      onSelect={(e) => e.preventDefault()}
      data-testid={`column-visibility-${columnId}`}
    >
      <span className="flex-1">{label}</span>
      {pinned && (
        <span className="ml-2 text-[10px] font-semibold uppercase tracking-wide text-text-muted">
          pinned
        </span>
      )}
    </DropdownMenuCheckboxItem>
  );
});

const AtomicPinToggle = memo(function AtomicPinToggle({
  gridId,
  columnId,
  label,
}: AtomicColumnToggleProps) {
  const pinned = useGridColumnStore((s) => selectColumnPinned(s, gridId, columnId));
  const pinColumn = useGridColumnStore((s) => s.pinColumn);
  const unpinColumn = useGridColumnStore((s) => s.unpinColumn);

  return (
    <DropdownMenuCheckboxItem
      checked={pinned}
      onCheckedChange={(next) => {
        if (next) pinColumn(gridId, columnId);
        else unpinColumn(gridId, columnId);
      }}
      onSelect={(e) => e.preventDefault()}
      data-testid={`column-pin-${columnId}`}
    >
      Pin {label}
    </DropdownMenuCheckboxItem>
  );
});

/**
 * Top-rail column visibility toggle — shadcn/Radix DropdownMenu with
 * checkbox items bound atomically to gridColumnStore layouts[gridId].
 */
export function ColumnVisibilityMenu({
  columns,
  gridId = 'products',
  className,
  opsOnlyColumnIds,
}: ColumnVisibilityMenuProps) {
  const setColumnVisibilityMap = useGridColumnStore((s) => s.setColumnVisibilityMap);

  const applyShowAll = () => {
    const visibility: Record<string, boolean> = {};
    for (const col of columns) visibility[col.id] = true;
    setColumnVisibilityMap(gridId, visibility);
  };

  const applyOpsOnly = () => {
    if (!opsOnlyColumnIds) return;
    const ops = new Set(opsOnlyColumnIds);
    const visibility: Record<string, boolean> = {};
    for (const col of columns) visibility[col.id] = ops.has(col.id);
    setColumnVisibilityMap(gridId, visibility);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          data-testid="column-visibility-toggle"
          className={cn(
            'inline-flex h-9 items-center gap-2 rounded-md border border-border bg-surface-raised px-3 text-sm font-medium text-text',
            'hover:bg-surface-overlay focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/30',
            'active:scale-[0.98] transition-transform duration-150 ease-out',
            className,
          )}
        >
          <Columns3 className="h-4 w-4 text-text-muted" aria-hidden />
          <span className="hidden sm:inline">Columns</span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        side="bottom"
        collisionPadding={12}
        data-testid="column-visibility-menu"
        className="flex max-h-[min(70vh,28rem)] w-56 flex-col overflow-y-auto overscroll-contain p-1"
      >
        {opsOnlyColumnIds && opsOnlyColumnIds.length > 0 && (
          <>
            <div className="sticky top-0 z-10 flex gap-1 bg-surface-raised p-1">
              <button
                type="button"
                data-testid="column-preset-show-all"
                className="flex-1 rounded-md border border-border px-2 py-1.5 text-xs font-medium text-text hover:bg-surface-overlay active:scale-[0.98] transition-transform duration-150 ease-out"
                onClick={applyShowAll}
              >
                Show all
              </button>
              <button
                type="button"
                data-testid="column-preset-ops-only"
                className="flex-1 rounded-md border border-border px-2 py-1.5 text-xs font-medium text-text hover:bg-surface-overlay active:scale-[0.98] transition-transform duration-150 ease-out"
                onClick={applyOpsOnly}
              >
                Ops only
              </button>
            </div>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuLabel className="sticky top-0 z-10 bg-surface-raised">
          Toggle columns
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {columns.map((col) => (
          <AtomicVisibilityToggle
            key={col.id}
            gridId={gridId}
            columnId={col.id}
            label={col.label}
          />
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="sticky top-0 z-10 bg-surface-raised">
          Pin identifiers
        </DropdownMenuLabel>
        {columns.map((col) => (
          <AtomicPinToggle
            key={`pin-${col.id}`}
            gridId={gridId}
            columnId={col.id}
            label={col.label}
          />
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
