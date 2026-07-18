import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, History } from 'lucide-react';
import { listLedgerTransactions } from '@/api/inventory';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, ProductVariant } from '@/api/types';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { formatNumber } from '@/lib/utils';

function relativeTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (!Number.isFinite(ms) || ms < 0) return 'just now';
  const mins = Math.floor(ms / 60_000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min${mins === 1 ? '' : 's'} ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} hr${hours === 1 ? '' : 's'} ago`;
  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

function formatDelta(value: number | string): string {
  const n = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(n)) return String(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${formatNumber(n)}`;
}

function humanizeMovement(type: string): string {
  const map: Record<string, string> = {
    RECEIVE: 'Received',
    SHIP: 'Shipped',
    ADJUST: 'Adjusted',
    TRANSFER_IN: 'Transferred in',
    TRANSFER_OUT: 'Transferred out',
    PICK: 'Picked',
    PACK: 'Packed',
  };
  return map[type] ?? type.replaceAll('_', ' ').toLowerCase();
}

/** Compact last-5 ledger feed for the dashboard command center. */
export function RecentLedgerActivity() {
  const { data = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['inventory_ledger', 'recent-activity'],
    queryFn: () => listLedgerTransactions(5),
    staleTime: 15_000,
    retry: false,
  });

  const variantIds = [...new Set(data.map((r) => r.variantId))];

  const { data: skuByVariant = {} } = useQuery({
    queryKey: ['inventory_ledger', 'sku-map', variantIds],
    enabled: variantIds.length > 0,
    queryFn: async () => {
      const res = await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants', {
        params: { limit: 50 },
      });
      const map: Record<string, string> = {};
      for (const v of res.data.items ?? []) {
        map[v.id] = v.sku;
      }
      return map;
    },
    staleTime: 60_000,
    retry: false,
  });

  return (
    <section
      className="flex h-64 flex-col rounded-2xl bg-surface-raised p-5 shadow-card"
      data-testid="recent-ledger-activity"
    >
      <div className="mb-3 flex items-start justify-between gap-2">
        <div className="flex items-start gap-2">
          <History className="mt-0.5 h-5 w-5 text-accent" aria-hidden />
          <div>
            <h2 className="text-sm font-semibold text-text">Recent activity</h2>
            <p className="text-xs text-text-muted">Last 5 inventory movements</p>
          </div>
        </div>
        <Link
          to="/reports?tab=audit"
          className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
          data-testid="recent-ledger-view-audit"
        >
          Full audit
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </div>

      <div className="min-h-0 flex-1 overflow-hidden">
        {isLoading ? (
          <div className="space-y-2" data-testid="list-page-loading">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-8 w-full" />
            ))}
          </div>
        ) : isError ? (
          <div data-testid="list-page-error">
            <EmptyState
              title="Unable to load activity"
              description="Check your connection and try again."
              action={
                <button
                  type="button"
                  className="text-sm font-medium text-accent hover:underline"
                  onClick={() => void refetch()}
                >
                  Retry
                </button>
              }
            />
          </div>
        ) : data.length === 0 ? (
          <p className="text-sm text-text-muted" data-testid="list-page-empty">
            No ledger movements yet.
          </p>
        ) : (
          <ul className="space-y-2">
            {data.map((row) => {
              const sku = skuByVariant[row.variantId] ?? row.variantId.slice(0, 8);
              return (
                <li
                  key={row.id}
                  className="truncate text-sm text-text"
                  data-testid={`recent-ledger-row-${row.id}`}
                >
                  <span className="font-mono font-medium tabular-nums">
                    {formatDelta(row.quantityDelta)}
                  </span>{' '}
                  <span className="font-mono text-text-muted">{sku}</span>{' '}
                  <span className="text-text-muted">{humanizeMovement(row.movementType)}</span>
                  <span className="text-text-muted">
                    {' '}
                    · {row.createdAt ? relativeTime(row.createdAt) : '—'}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
