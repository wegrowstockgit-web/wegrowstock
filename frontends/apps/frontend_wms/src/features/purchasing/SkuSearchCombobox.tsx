import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronsUpDown } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, ProductVariant } from '@/api/types';
import { unwrapPageItems } from '@/api/page';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

const RESULT_LIMIT = 20;

export function SkuSearchCombobox({
  value,
  selectedLabel,
  onSelect,
  label = 'Add item',
  testId = 'po-add-sku',
  optionTestId = 'po-sku-option',
}: {
  value: string;
  selectedLabel?: string;
  onSelect: (variant: ProductVariant) => void;
  label?: string;
  testId?: string;
  optionTestId?: string;
}) {
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handle = window.setTimeout(() => setDebounced(query.trim()), 250);
    return () => window.clearTimeout(handle);
  }, [query]);

  useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, []);

  const { data: results = [], isFetching } = useQuery({
    queryKey: ['variants', 'sku-search', debounced],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (debounced) params.set('search', debounced);
      params.set('limit', String(RESULT_LIMIT));
      const page = (await apiClient.get<PaginatedResponse<ProductVariant>>(`/api/v1/variants?${params}`)).data;
      return unwrapPageItems<ProductVariant>(page).slice(0, RESULT_LIMIT);
    },
    enabled: open,
  });

  const display = value && selectedLabel && !open ? selectedLabel : query;

  return (
    <div ref={rootRef} className="relative" data-testid="po-sku-combobox">
      <div className="relative">
        <Input
          label={label}
          role="combobox"
          aria-expanded={open}
          aria-controls="po-sku-listbox"
          aria-autocomplete="list"
          autoComplete="off"
          placeholder="Search SKU or name…"
          value={display}
          onFocus={() => setOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          data-testid={testId}
          className="pr-9"
        />
        <ChevronsUpDown
          className="pointer-events-none absolute right-3 top-[2.15rem] h-4 w-4 text-text-muted"
          aria-hidden
        />
      </div>
      {open ? (
        <ul
          id="po-sku-listbox"
          role="listbox"
          className="absolute z-30 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-border bg-surface-raised shadow-lg"
          data-testid="po-sku-results"
        >
          {results.map((variant) => (
            <li key={variant.id} role="option" aria-selected={variant.id === value}>
              <button
                type="button"
                className={cn(
                  'flex w-full flex-col items-start px-3 py-2 text-left text-sm hover:bg-surface-overlay',
                  variant.id === value && 'bg-accent-muted/40',
                )}
                data-testid={optionTestId}
                onClick={() => {
                  onSelect(variant);
                  setQuery('');
                  setOpen(false);
                }}
              >
                <span className="font-mono font-medium">{variant.sku}</span>
                <span className="text-xs text-text-muted">{variant.name}</span>
              </button>
            </li>
          ))}
          {results.length === 0 && !isFetching ? (
            <li className="px-3 py-2 text-sm text-text-muted">No matching SKUs</li>
          ) : null}
          {isFetching ? <li className="px-3 py-2 text-sm text-text-muted">Searching…</li> : null}
        </ul>
      ) : null}
    </div>
  );
}
