import { useCallback, useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { AuditLogItem, AuditTenantPage } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import {
  VirtualizedTable,
  type VirtualizedColumnDef,
} from '@/components/ui/primitives/VirtualizedTable';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { useConcurrentSearch } from '@/hooks/useConcurrentSearch';

const ENTITY_FILTERS = [
  '',
  'USER',
  'USERS',
  'INVITATION',
  'TENANT_SETTINGS',
  'USER_ROLES',
  'USER_WAREHOUSES',
];

const ACTION_FILTERS = [
  '',
  'UPDATE_USER',
  'INVITE_USER',
  'RESEND_INVITATION',
  'DEACTIVATE_USER',
  'TG_INSERT',
  'TG_UPDATE',
  'TG_DELETE',
  'USER_INVITE',
  'USER_ORG_UPDATE',
];

function formatWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function actorLabel(row: AuditLogItem): string {
  return row.actorDisplayName || row.actorEmail || row.actorUserId?.slice(0, 8) || '—';
}

function DiffCell({ diff }: { diff: Record<string, unknown> }) {
  const [open, setOpen] = useState(false);
  const preview = JSON.stringify(diff);
  return (
    <div className="max-w-md">
      <button
        type="button"
        className="w-full truncate text-left font-mono text-xs text-text-muted hover:text-text"
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        title={preview}
      >
        {open ? 'Hide JSON' : preview.slice(0, 80) + (preview.length > 80 ? '…' : '')}
      </button>
      {open && (
        <pre className="mt-1 max-h-40 overflow-auto rounded border border-border bg-surface-overlay/50 p-2 text-[11px] text-text">
          {JSON.stringify(diff, null, 2)}
        </pre>
      )}
    </div>
  );
}

/**
 * Global compliance grid for Settings → Operations (OWNER / ADMIN).
 */
export function AuditLogTable() {
  const [entityType, setEntityType] = useState('');
  const [action, setAction] = useState('');
  const {
    inputValue: actorInput,
    deferredValue: actorFilter,
    isPending: actorPending,
    setInputValue: setActorInput,
    startTransition,
  } = useConcurrentSearch('');

  const query = useInfiniteQuery({
    queryKey: ['audit', 'tenant', entityType, action],
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }) =>
      (
        await apiClient.get<AuditTenantPage>('/api/v1/audit/tenant', {
          params: {
            limit: 40,
            cursor: pageParam,
            entityType: entityType || undefined,
            action: action || undefined,
          },
        })
      ).data,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    retry: false,
  });

  const rows = useMemo(() => {
    const all = query.data?.pages.flatMap((p) => p.items) ?? [];
    const q = actorFilter.trim().toLowerCase();
    if (!q) return all;
    return all.filter((row) => actorLabel(row).toLowerCase().includes(q));
  }, [query.data?.pages, actorFilter]);

  const onEndReached = useCallback(() => {
    if (query.hasNextPage && !query.isFetchingNextPage) {
      void query.fetchNextPage();
    }
  }, [query]);

  const columns = useMemo<VirtualizedColumnDef<AuditLogItem>[]>(
    () => [
      {
        id: 'timestamp',
        header: 'Timestamp',
        width: 170,
        sortable: true,
        sortValue: (r) => r.createdAt ?? '',
        cell: (r) => <span className="text-sm tabular-nums">{formatWhen(r.createdAt)}</span>,
      },
      {
        id: 'actor',
        header: 'Actor',
        width: 180,
        flexGrow: true,
        cell: (r) => <span className="text-sm">{actorLabel(r)}</span>,
      },
      {
        id: 'action',
        header: 'Action',
        width: 150,
        cell: (r) => (
          <span className="rounded bg-accent-muted px-1.5 py-0.5 font-mono text-[11px] font-semibold text-accent">
            {r.action}
          </span>
        ),
      },
      {
        id: 'entityType',
        header: 'Entity Type',
        width: 140,
        cell: (r) => <span className="font-mono text-xs">{r.entityType}</span>,
      },
      {
        id: 'entityId',
        header: 'Entity ID',
        width: 120,
        cell: (r) => (
          <span className="font-mono text-xs text-text-muted" title={r.entityId}>
            {r.entityId.slice(0, 8)}…
          </span>
        ),
      },
      {
        id: 'diff',
        header: 'Changes (Diff)',
        width: 280,
        flexGrow: true,
        cell: (r) => <DiffCell diff={r.diff ?? {}} />,
      },
    ],
    [],
  );

  return (
    <Card data-testid="audit-log-table">
      <CardHeader
        title="Compliance audit log"
        description="Tenant-wide append-only trail — filter by entity type and action"
        action={
          <div className="flex flex-wrap gap-2">
            <Input
              aria-label="Filter by actor"
              value={actorInput}
              onChange={(e) => setActorInput(e.target.value)}
              placeholder="Filter by actor..."
              className="min-w-[10rem] max-w-[14rem]"
              aria-busy={actorPending || undefined}
              data-testid="audit-filter-actor"
            />
            <Select
              aria-label="Filter entity type"
              value={entityType}
              onChange={(e) => {
                const next = e.target.value;
                startTransition(() => setEntityType(next));
              }}
              data-testid="audit-filter-entity-type"
              className="min-w-[10rem]"
            >
              <option value="">All entity types</option>
              {ENTITY_FILTERS.filter(Boolean).map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filter action"
              value={action}
              onChange={(e) => {
                const next = e.target.value;
                startTransition(() => setAction(next));
              }}
              data-testid="audit-filter-action"
              className="min-w-[10rem]"
            >
              <option value="">All actions</option>
              {ACTION_FILTERS.filter(Boolean).map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </Select>
          </div>
        }
      />
      {query.isLoading ? (
        <TableSkeleton rows={8} cols={6} />
      ) : (
        <div className="h-[420px]" data-testid="audit-log-grid">
          <VirtualizedTable
            gridId="audit-compliance"
            columns={columns}
            rows={rows}
            getRowId={(r) => r.id}
            onEndReached={onEndReached}
            empty={<p className="p-4 text-sm text-text-muted">No audit entries match these filters.</p>}
          />
        </div>
      )}
      {query.isFetchingNextPage && (
        <p className="px-3 py-2 text-xs text-text-muted">Loading more…</p>
      )}
    </Card>
  );
}
