import { useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { ClipboardList } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { AuditLogItem, AuditTenantPage } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { useConcurrentSearch } from '@/hooks/useConcurrentSearch';
import { cn } from '@/lib/utils';
import {
  actorLabel,
  auditFieldChanges,
  formatActionLabel,
  formatEntityLabel,
  formatLoginAuditLine,
  isLoginAuditAction,
  summarizeAuditDiff,
} from './auditDiffCopy';

const ENTITY_FILTERS = [
  { value: 'USER', label: 'User' },
  { value: 'USERS', label: 'Users' },
  { value: 'INVITATION', label: 'Invitation' },
  { value: 'TENANT_SETTINGS', label: 'Workspace settings' },
  { value: 'USER_ROLES', label: 'User role' },
  { value: 'USER_WAREHOUSES', label: 'Warehouse access' },
];

const ACTION_FILTERS = [
  { value: 'UPDATE_USER', label: 'Updated user' },
  { value: 'INVITE_USER', label: 'Sent invitation' },
  { value: 'RESEND_INVITATION', label: 'Resent invitation' },
  { value: 'DEACTIVATE_USER', label: 'Deactivated user' },
  { value: 'TG_INSERT', label: 'Created' },
  { value: 'TG_UPDATE', label: 'Updated' },
  { value: 'TG_DELETE', label: 'Deleted' },
  { value: 'USER_INVITE', label: 'Sent invitation' },
  { value: 'USER_ORG_UPDATE', label: 'Updated access' },
  { value: 'POS_LINE_VOID', label: 'Voided register line' },
  { value: 'LOGIN_SUCCESS', label: 'Signed in' },
  { value: 'LOGIN_BLOCKED_CIDR', label: 'Blocked sign-in (off-network)' },
];

function formatWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function ActionBadge({ action }: { action: string }) {
  const tone =
    action.includes('DELETE') || action.includes('DEACTIVATE') || action.includes('VOID') || action.includes('BLOCKED')
      ? 'bg-danger/10 text-danger'
      : action.includes('INSERT') || action.includes('INVITE') || action === 'LOGIN_SUCCESS'
        ? 'bg-success/10 text-success'
        : 'bg-accent-muted text-accent';
  return (
    <span className={cn('inline-flex rounded-md px-2 py-0.5 text-xs font-semibold', tone)}>
      {formatActionLabel(action)}
    </span>
  );
}

/**
 * Global compliance grid for Settings → Operations (OWNER / ADMIN).
 */
export function AuditLogTable() {
  const [entityType, setEntityType] = useState('');
  const [action, setAction] = useState('');
  const [selected, setSelected] = useState<AuditLogItem | null>(null);
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

  const selectedChanges = selected ? auditFieldChanges(selected.diff) : [];

  return (
    <Card padding="none" className="min-w-0 overflow-hidden" data-testid="audit-log-table">
      <div className="space-y-4 px-6 pt-6 pb-4">
        <div>
          <h3 className="text-lg font-semibold text-text">Compliance audit log</h3>
          <p className="mt-1 text-sm text-text-muted">
            Who changed what, in plain language. Click a row for the full before-and-after.
          </p>
        </div>
        <div className="flex min-w-0 flex-wrap gap-2">
          <Input
            aria-label="Filter by actor"
            value={actorInput}
            onChange={(e) => setActorInput(e.target.value)}
            placeholder="Filter by person..."
            className="w-full min-w-[10rem] sm:w-auto sm:max-w-[16rem]"
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
            className="min-w-[10rem] flex-1 sm:flex-none"
          >
            <option value="">All records</option>
            {ENTITY_FILTERS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
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
            className="min-w-[10rem] flex-1 sm:flex-none"
          >
            <option value="">All actions</option>
            {ACTION_FILTERS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {query.isLoading ? (
        <div className="px-6 pb-6">
          <TableSkeleton rows={8} cols={5} />
        </div>
      ) : rows.length === 0 ? (
        <div className="px-6 pb-6">
          <EmptyState
            icon={ClipboardList}
            title="No matching activity"
            description="Try clearing the person, record, or action filters."
          />
        </div>
      ) : (
        <div className="min-w-0 overflow-x-auto" data-testid="audit-log-grid">
          <Table className="min-w-[52rem]">
            <TableHeader>
              <TableRow>
                <TableHead>Timestamp</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Entity Type</TableHead>
                <TableHead>Entity ID</TableHead>
                <TableHead>Changes (Diff)</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="h-auto min-h-11 align-top"
                  onClick={() => setSelected(row)}
                  selected={selected?.id === row.id}
                  data-testid={`audit-row-${row.id}`}
                >
                  <TableCell className="whitespace-nowrap text-sm tabular-nums text-text-muted">
                    {formatWhen(row.createdAt)}
                  </TableCell>
                  <TableCell className="text-sm text-text">{actorLabel(row)}</TableCell>
                  <TableCell>
                    <ActionBadge action={row.action} />
                  </TableCell>
                  <TableCell className="text-sm text-text">{formatEntityLabel(row.entityType)}</TableCell>
                  <TableCell>
                    <span className="font-mono text-xs text-text-muted" title={row.entityId}>
                      {row.entityId.slice(0, 8)}…
                    </span>
                  </TableCell>
                  <TableCell className="max-w-sm">
                    <p className="text-sm leading-5 text-text">{summarizeAuditDiff(row.diff, row.action)}</p>
                    {isLoginAuditAction(row.action) && formatLoginAuditLine(row.diff) ? (
                      <p className="mt-0.5 text-sm text-text-muted" data-testid="audit-login-meta">
                        {formatLoginAuditLine(row.diff)}
                      </p>
                    ) : (
                      <p className="mt-0.5 text-xs text-text-muted">View details</p>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {query.hasNextPage && (
        <div className="flex justify-center border-t border-border px-6 py-3">
          <Button
            variant="secondary"
            size="sm"
            loading={query.isFetchingNextPage}
            onClick={() => void query.fetchNextPage()}
          >
            Load more activity
          </Button>
        </div>
      )}

      <RightPeekDrawer
        open={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? formatActionLabel(selected.action) : 'Change details'}
        description={
          selected
            ? `${actorLabel(selected)} · ${formatEntityLabel(selected.entityType)} · ${formatWhen(selected.createdAt)}`
            : undefined
        }
        width="md"
      >
        {selected && (
          <div className="space-y-5" data-testid="audit-diff-detail">
            <p className="text-sm text-text">{summarizeAuditDiff(selected.diff, selected.action)}</p>
            {isLoginAuditAction(selected.action) && formatLoginAuditLine(selected.diff) ? (
              <p className="text-sm text-text-muted" data-testid="audit-login-meta-detail">
                {formatLoginAuditLine(selected.diff)}
              </p>
            ) : selectedChanges.length > 0 ? (
              <dl className="divide-y divide-border overflow-hidden rounded-lg border border-border">
                {selectedChanges.map((change) => (
                  <div key={change.field} className="grid grid-cols-[8rem_1fr] gap-3 px-3 py-2.5 sm:grid-cols-[10rem_1fr]">
                    <dt className="text-xs font-medium text-text-muted">{change.field}</dt>
                    <dd className="text-sm text-text">
                      {change.from && change.to ? (
                        <>
                          <span className="text-text-muted line-through">{change.from}</span>
                          <span className="mx-1.5 text-text-muted">→</span>
                          <span>{change.to}</span>
                        </>
                      ) : (
                        change.to ?? change.from ?? '—'
                      )}
                    </dd>
                  </div>
                ))}
              </dl>
            ) : (
              <p className="text-sm text-text-muted">No field-level details for this event.</p>
            )}
            <p className="text-xs text-text-muted">Record ID {selected.entityId}</p>
          </div>
        )}
      </RightPeekDrawer>
    </Card>
  );
}
