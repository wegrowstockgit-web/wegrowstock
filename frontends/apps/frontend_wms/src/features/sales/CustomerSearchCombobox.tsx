import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronsUpDown } from 'lucide-react';
import { apiClient } from '@/api/client';
import { unwrapPageItems } from '@/api/page';
import type { Customer } from '@/api/types';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

export function CustomerSearchCombobox({
  value,
  selectedLabel,
  onSelect,
}: {
  value: string;
  selectedLabel?: string;
  onSelect: (customer: Customer) => void;
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
    queryKey: ['customers', 'so-search', debounced],
    queryFn: async () =>
      unwrapPageItems<Customer>(
        (
          await apiClient.get('/api/v1/customers', {
            params: { page: 1, size: 25, search: debounced || undefined },
          })
        ).data,
      ),
    enabled: open,
  });

  const display = value && selectedLabel && !open ? selectedLabel : query;

  return (
    <div ref={rootRef} className="relative" data-testid="so-customer-combobox">
      <div className="relative">
        <Input
          label="Customer"
          role="combobox"
          aria-expanded={open}
          aria-controls="so-customer-listbox"
          aria-autocomplete="list"
          autoComplete="off"
          required
          placeholder="Search customer name…"
          value={display}
          onFocus={() => setOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          data-testid="so-customer-search"
          className="pr-9"
        />
        <ChevronsUpDown
          className="pointer-events-none absolute right-3 top-[2.15rem] h-4 w-4 text-text-muted"
          aria-hidden
        />
      </div>
      {open ? (
        <ul
          id="so-customer-listbox"
          role="listbox"
          className="absolute z-30 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-border bg-surface-raised shadow-lg"
          data-testid="so-customer-results"
        >
          {results.map((customer) => (
            <li key={customer.id} role="option" aria-selected={customer.id === value}>
              <button
                type="button"
                className={cn(
                  'flex w-full flex-col items-start px-3 py-2 text-left text-sm hover:bg-surface-overlay',
                  customer.id === value && 'bg-accent-muted/40',
                )}
                data-testid={`so-customer-option-${customer.name}`}
                onClick={() => {
                  onSelect(customer);
                  setQuery('');
                  setOpen(false);
                }}
              >
                <span className="font-medium">{customer.name}</span>
                <span className="text-xs text-text-muted">
                  {customer.paymentTerms ?? 'Terms on file'}
                </span>
              </button>
            </li>
          ))}
          {results.length === 0 && !isFetching ? (
            <li className="px-3 py-2 text-sm text-text-muted">No matching customers</li>
          ) : null}
          {isFetching ? <li className="px-3 py-2 text-sm text-text-muted">Searching…</li> : null}
        </ul>
      ) : null}
    </div>
  );
}
