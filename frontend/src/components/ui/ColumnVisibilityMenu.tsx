import { Columns3 } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useGridColumnStore } from '@/stores/gridColumnStore';
import { cn } from '@/lib/utils';

export interface ColumnVisibilityItem {
  id: string;
  label: string;
}

interface ColumnVisibilityMenuProps {
  columns: ColumnVisibilityItem[];
  className?: string;
}

/**
 * Top-rail column visibility toggle — shadcn/Radix DropdownMenu with
 * checkbox items bound to gridColumnStore.columnVisibility.
 */
export function ColumnVisibilityMenu({ columns, className }: ColumnVisibilityMenuProps) {
  const columnVisibility = useGridColumnStore((s) => s.columnVisibility);
  const pinnedColumns = useGridColumnStore((s) => s.pinnedColumns);
  const toggleColumnVisibility = useGridColumnStore((s) => s.toggleColumnVisibility);
  const pinColumn = useGridColumnStore((s) => s.pinColumn);
  const unpinColumn = useGridColumnStore((s) => s.unpinColumn);

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
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>Toggle columns</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {columns.map((col) => {
          const pinned = pinnedColumns.includes(col.id);
          const visible = columnVisibility[col.id] !== false;
          return (
            <DropdownMenuCheckboxItem
              key={col.id}
              checked={visible}
              disabled={pinned}
              onCheckedChange={() => toggleColumnVisibility(col.id)}
              onSelect={(e) => e.preventDefault()}
              data-testid={`column-visibility-${col.id}`}
            >
              <span className="flex-1">{col.label}</span>
              {pinned && (
                <span className="ml-2 text-[10px] font-semibold uppercase tracking-wide text-text-muted">
                  pinned
                </span>
              )}
            </DropdownMenuCheckboxItem>
          );
        })}
        <DropdownMenuSeparator />
        <DropdownMenuLabel>Pin identifiers</DropdownMenuLabel>
        {columns.map((col) => {
          const pinned = pinnedColumns.includes(col.id);
          return (
            <DropdownMenuCheckboxItem
              key={`pin-${col.id}`}
              checked={pinned}
              onCheckedChange={(next) => {
                if (next) pinColumn(col.id);
                else unpinColumn(col.id);
              }}
              onSelect={(e) => e.preventDefault()}
              data-testid={`column-pin-${col.id}`}
            >
              Pin {col.label}
            </DropdownMenuCheckboxItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
