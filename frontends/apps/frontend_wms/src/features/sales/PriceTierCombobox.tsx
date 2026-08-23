import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronsUpDown } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { CustomerPriceTier } from '@/api/types';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

export function PriceTierCombobox({
  value,
  selectedLabel,
  onSelect,
}: {
  value: string;
  selectedLabel?: string;
  onSelect: (tier: CustomerPriceTier | null) => void;
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
    queryKey: ['customer-price-tiers', debounced],
    queryFn: async () =>
      (
        await apiClient.get<CustomerPriceTier[]>('/api/v1/customers/price-tiers', {
          params: { search: debounced || undefined },
        })
      ).data,
    enabled: open,
  });

  const display = value && selectedLabel && !open ? selectedLabel : query;

  return (
    <div ref={rootRef} className="relative" data-testid="customer-price-tier-combobox">
      <div className="relative">
        <Input
          label="Price tier"
          role="combobox"
          aria-expanded={open}
          autoComplete="off"
          placeholder="Search Wholesale, VIP, Tier 1…"
          value={display}
          onFocus={() => setOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          data-testid="customer-price-tier-search"
          className="pr-9"
        />
        <ChevronsUpDown
          className="pointer-events-none absolute right-3 top-[2.15rem] h-4 w-4 text-text-muted"
          aria-hidden
        />
      </div>
      {open ? (
        <ul
          role="listbox"
          className="absolute z-30 mt-1 max-h-56 w-full overflow-auto rounded-lg border border-border bg-surface-raised shadow-lg"
          data-testid="customer-price-tier-results"
        >
          <li>
            <button
              type="button"
              className="w-full px-3 py-2 text-left text-sm text-text-muted hover:bg-surface-overlay"
              onClick={() => {
                onSelect(null);
                setQuery('');
                setOpen(false);
              }}
            >
              List (no discount)
            </button>
          </li>
          {results.map((tier) => (
            <li key={tier.id} role="option" aria-selected={tier.id === value}>
              <button
                type="button"
                className={cn(
                  'flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-surface-overlay',
                  tier.id === value && 'bg-accent-muted/40',
                )}
                data-testid={`customer-tier-option-${tier.name}`}
                onClick={() => {
                  onSelect(tier);
                  setQuery('');
                  setOpen(false);
                }}
              >
                <span>{tier.name}</span>
                <span className="text-xs text-text-muted">{Number(tier.discountPercent)}% off</span>
              </button>
            </li>
          ))}
          {results.length === 0 && !isFetching ? (
            <li className="px-3 py-2 text-sm text-text-muted">No matching tiers</li>
          ) : null}
        </ul>
      ) : null}
    </div>
  );
}
