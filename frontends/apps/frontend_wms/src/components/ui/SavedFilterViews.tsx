import { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';

export interface FilterPreset {
  id: string;
  label: string;
  filters: Record<string, string>;
}

interface SavedFilterViewsProps {
  storageKey: string;
  activeFilters: Record<string, string>;
  onApply: (filters: Record<string, string>) => void;
  defaultPresets?: FilterPreset[];
  className?: string;
}

const DEFAULT_PRESETS: FilterPreset[] = [
  { id: 'all', label: 'All', filters: {} },
];

export function SavedFilterViews({
  storageKey,
  activeFilters,
  onApply,
  defaultPresets = DEFAULT_PRESETS,
  className,
}: SavedFilterViewsProps) {
  const { toast } = useToast();
  const [presets, setPresets] = useState<FilterPreset[]>(defaultPresets);
  const [activeId, setActiveId] = useState('all');
  const [naming, setNaming] = useState(false);
  const [viewName, setViewName] = useState('');

  useEffect(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        const saved = JSON.parse(raw) as FilterPreset[];
        setPresets([...defaultPresets, ...saved.filter((p) => !defaultPresets.some((d) => d.id === p.id))]);
      }
    } catch {
      /* ignore corrupt storage */
    }
  }, [storageKey, defaultPresets]);

  const matchesPreset = (preset: FilterPreset) =>
    Object.entries(preset.filters).every(([k, v]) => activeFilters[k] === v) &&
    Object.keys(activeFilters).every((k) => preset.filters[k] === activeFilters[k] || !activeFilters[k]);

  useEffect(() => {
    const match = presets.find((p) => matchesPreset(p));
    if (match) setActiveId(match.id);
  }, [activeFilters, presets]);

  const saveCurrent = () => {
    const label = viewName.trim();
    if (!label) {
      toast('Enter a name for this view', { tone: 'danger' });
      return;
    }
    const preset: FilterPreset = {
      id: `custom-${Date.now()}`,
      label,
      filters: { ...activeFilters },
    };
    const custom = presets.filter((p) => p.id.startsWith('custom-'));
    const next = [...defaultPresets, ...custom, preset];
    setPresets(next);
    localStorage.setItem(storageKey, JSON.stringify(custom.concat(preset)));
    setActiveId(preset.id);
    setNaming(false);
    setViewName('');
    toast(`Saved view “${label}”`, { tone: 'success' });
  };

  return (
    <div className={cn('mb-4 flex flex-wrap items-center gap-2', className)}>
      <div
        className="inline-flex flex-wrap gap-1 rounded-lg border border-border bg-surface-overlay p-1"
        role="tablist"
        aria-label="Saved filter views"
      >
        {presets.map((preset) => (
          <button
            key={preset.id}
            type="button"
            role="tab"
            aria-selected={activeId === preset.id}
            className={cn(
              'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
              activeId === preset.id
                ? 'bg-surface-raised text-text shadow-sm'
                : 'text-text-muted hover:text-text'
            )}
            onClick={() => {
              setActiveId(preset.id);
              onApply(preset.filters);
            }}
          >
            {preset.label}
          </button>
        ))}
      </div>
      {naming ? (
        <form
          className="flex flex-wrap items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            saveCurrent();
          }}
        >
          <Input
            value={viewName}
            onChange={(e) => setViewName(e.target.value)}
            placeholder="View name"
            className="h-8 w-40"
            autoFocus
            aria-label="Filter view name"
          />
          <Button type="submit" size="sm">
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
        <Button variant="ghost" size="sm" onClick={() => setNaming(true)}>
          <Plus className="h-3.5 w-3.5" />
          Save view
        </Button>
      )}
    </div>
  );
}
