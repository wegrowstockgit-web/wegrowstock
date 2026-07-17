import { useEffect, useState } from 'react';
import { Bookmark, ChevronDown, Plus } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';
import { useGridColumnStore } from '@/stores/gridColumnStore';
import { useSavedViewsStore } from '@/stores/useSavedViewsStore';
import { cn } from '@/lib/utils';

interface SavedGridViewsProps {
  gridId: string;
  className?: string;
}

const EMPTY_VIEWS: import('@/stores/useSavedViewsStore').SavedGridView[] = [];

/**
 * Persist / restore Product Master (and other) column layouts via /users/me/views.
 */
export function SavedGridViews({ gridId, className }: SavedGridViewsProps) {
  const { toast } = useToast();
  // Stable empty fallback — `?? []` would allocate every render and loop React #185
  const views = useSavedViewsStore((s) => s.viewsByGrid[gridId]) ?? EMPTY_VIEWS;
  const saveView = useSavedViewsStore((s) => s.saveView);
  const applyView = useSavedViewsStore((s) => s.applyView);
  const [naming, setNaming] = useState(false);
  const [viewName, setViewName] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void useSavedViewsStore.getState().fetchViews(gridId).catch(() => undefined);
  }, [gridId]);

  const onSave = async () => {
    const label = viewName.trim();
    if (!label) {
      toast('Enter a name for this view', { tone: 'danger' });
      return;
    }
    const state = useGridColumnStore.getState();
    setSaving(true);
    try {
      await saveView(label, gridId, {
        columnVisibility: state.columnVisibility,
        pinnedColumns: state.pinnedColumns,
        columnOrder: state.columnOrder,
      });
      toast(`Saved view “${label}”`, { tone: 'success' });
      setNaming(false);
      setViewName('');
    } catch {
      toast('Could not save view', { tone: 'danger' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={cn('relative inline-flex items-center gap-2', className)} data-testid="saved-grid-views">
      <div className="relative">
        <button
          type="button"
          data-testid="saved-views-dropdown"
          aria-haspopup="listbox"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((v) => !v)}
          className={cn(
            'inline-flex h-9 items-center gap-2 rounded-md border border-border bg-surface-raised px-3 text-sm font-medium text-text',
            'hover:bg-surface-overlay focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/30',
          )}
        >
          <Bookmark className="h-4 w-4 text-text-muted" aria-hidden />
          <span className="hidden sm:inline">Views</span>
          <ChevronDown className="h-3.5 w-3.5 text-text-muted" aria-hidden />
        </button>
        {menuOpen && (
          <ul
            role="listbox"
            aria-label="Saved grid views"
            className="absolute right-0 top-full z-50 mt-1 min-w-[12rem] overflow-hidden rounded-md border border-border-strong bg-surface-raised py-1 shadow-elevated"
          >
            {views.length === 0 ? (
              <li className="px-3 py-2 text-sm text-text-muted">No saved views yet</li>
            ) : (
              views.map((view) => (
                <li key={view.id} role="option">
                  <button
                    type="button"
                    data-testid={`saved-view-${view.name}`}
                    className="flex w-full px-3 py-2 text-left text-sm text-text hover:bg-surface-overlay"
                    onClick={() => {
                      applyView(view.id, gridId);
                      setMenuOpen(false);
                      toast(`Applied “${view.name}”`, { tone: 'success' });
                    }}
                  >
                    {view.name}
                  </button>
                </li>
              ))
            )}
          </ul>
        )}
      </div>

      {naming ? (
        <form
          className="flex flex-wrap items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void onSave();
          }}
        >
          <Input
            value={viewName}
            onChange={(e) => setViewName(e.target.value)}
            placeholder="View name"
            className="h-8 w-40"
            autoFocus
            aria-label="Grid view name"
            data-testid="save-view-name-input"
          />
          <Button type="submit" size="sm" loading={saving} data-testid="save-view-confirm">
            Save
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => {
              setNaming(false);
              setViewName('');
            }}
          >
            Cancel
          </Button>
        </form>
      ) : (
        <Button
          variant="ghost"
          size="sm"
          data-testid="save-view-button"
          onClick={() => setNaming(true)}
        >
          <Plus className="h-3.5 w-3.5" />
          Save view
        </Button>
      )}
    </div>
  );
}
