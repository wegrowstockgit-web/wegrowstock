import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronsUpDown } from 'lucide-react';
import { apiClient } from '@/api/client';
import { unwrapPageItems } from '@/api/page';
import type { SalesOrder } from '@/api/types';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

function shippedLabel(order: SalesOrder) {
  const raw = order.requestedShipDate || order.createdAt;
  if (!raw) return 'Ship date pending';
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return 'Ship date pending';
  return `Shipped ${date.toLocaleDateString()}`;
}

export function ShippedSalesOrderCombobox({
  value,
  selectedLabel,
  onSelect,
}: {
  value: string;
  selectedLabel?: string;
  onSelect: (order: SalesOrder) => void;
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
    queryKey: ['sales-orders', 'shipped-search', debounced],
    queryFn: async () =>
      unwrapPageItems<SalesOrder>(
        (
          await apiClient.get('/api/v1/sales-orders', {
            params: {
              page: 1,
              size: 25,
              status: 'SHIPPED',
              search: debounced || undefined,
            },
          })
        ).data,
      ),
    enabled: open,
  });

  const invoiceable = results.filter((order) => order.billingStatus !== 'INVOICED');
  const display = value && selectedLabel && !open ? selectedLabel : query;

  return (
    <div ref={rootRef} className="relative" data-testid="invoice-so-combobox">
      <div className="relative">
        <Input
          label="Sales order"
          role="combobox"
          aria-expanded={open}
          aria-controls="invoice-so-listbox"
          aria-autocomplete="list"
          autoComplete="off"
          required
          placeholder="Search shipped sales orders…"
          value={display}
          onFocus={() => setOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          data-testid="invoice-so-search"
          className="pr-9"
        />
        <ChevronsUpDown
          className="pointer-events-none absolute right-3 top-[2.15rem] h-4 w-4 text-text-muted"
          aria-hidden
        />
      </div>
      {open ? (
        <ul
          id="invoice-so-listbox"
          role="listbox"
          className="absolute z-30 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-border bg-surface-raised shadow-lg"
          data-testid="invoice-so-results"
        >
          {invoiceable.map((order) => (
            <li key={order.id} role="option" aria-selected={order.id === value}>
              <button
                type="button"
                className={cn(
                  'flex w-full flex-col items-start px-3 py-2 text-left text-sm hover:bg-surface-overlay',
                  order.id === value && 'bg-accent-muted/40',
                )}
                data-testid={`invoice-so-option-${order.number}`}
                onClick={() => {
                  onSelect(order);
                  setQuery('');
                  setOpen(false);
                }}
              >
                <span className="font-medium">{order.number}</span>
                <span className="text-xs text-text-muted">
                  {order.customerName} · {shippedLabel(order)}
                </span>
              </button>
            </li>
          ))}
          {invoiceable.length === 0 && !isFetching ? (
            <li className="px-3 py-2 text-sm text-text-muted">No matching shipped orders</li>
          ) : null}
          {isFetching ? <li className="px-3 py-2 text-sm text-text-muted">Searching…</li> : null}
        </ul>
      ) : null}
    </div>
  );
}
