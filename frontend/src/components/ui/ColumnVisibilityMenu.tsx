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
}: ColumnVisibilityMenuProps) {
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
