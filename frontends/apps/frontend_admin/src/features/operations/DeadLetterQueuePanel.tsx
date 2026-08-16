import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  PageSkeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useToast,
} from '@invsys/shared-ui';
import {
  fetchDeadLetter,
  fetchDeadLetterGroups,
  retryDeadLetter,
  type DeadLetterDetail,
} from './api';
import { PageHeader } from '@/features/layout/PageHeader';

export function DeadLetterQueuePanel() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [eventId, setEventId] = useState('');
  const [detail, setDetail] = useState<DeadLetterDetail | null>(null);

  const groupsQuery = useQuery({
    queryKey: ['control-plane', 'queues', 'dead-letters'],
    queryFn: fetchDeadLetterGroups,
  });

  const inspectMutation = useMutation({
    mutationFn: (id: string) => fetchDeadLetter(id),
    onSuccess: (row) => setDetail(row),
    onError: () => {
      setDetail(null);
      toast.danger('Dead-letter event not found.');
    },
  });

  const retryMutation = useMutation({
    mutationFn: (id: string) => retryDeadLetter(id),
    onSuccess: (row) => {
      setDetail(row);
      void queryClient.invalidateQueries({
        queryKey: ['control-plane', 'queues', 'dead-letters'],
      });
      toast.success('Event re-queued as PENDING');
    },
    onError: () => {
      toast.danger('Could not retry dead-letter event.');
    },
  });

  return (
    <div className="space-y-8" data-testid="dead-letter-queue">
      <PageHeader
        title="Dead letter queue"
        description="Failed outbox events grouped by tenant. Inspect or retry a specific event by ID."
      />

      {groupsQuery.isLoading ? (
        <PageSkeleton label="Loading dead letters…" />
      ) : groupsQuery.isError ? (
        <p className="text-sm text-danger">Failed to load dead-letter groups.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tenant</TableHead>
              <TableHead>Failed events</TableHead>
              <TableHead>Latest</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(groupsQuery.data ?? []).length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} className="text-text-muted">
                  No failed outbox events.
                </TableCell>
              </TableRow>
            ) : (
              (groupsQuery.data ?? []).map((row) => (
                <TableRow key={row.tenantId}>
                  <TableCell className="font-mono text-xs">{row.tenantId}</TableCell>
                  <TableCell className="font-medium">{row.count}</TableCell>
                  <TableCell className="text-text-muted">
                    {row.latestAt ? new Date(row.latestAt).toLocaleString() : '—'}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}

      <section className="admin-card space-y-3 p-5">
        <h3 className="text-sm font-semibold text-text">Inspect / retry event</h3>
        <div className="flex flex-wrap gap-2">
          <input
            className="admin-field min-w-[280px] flex-1 font-mono"
            placeholder="Outbox event UUID"
            value={eventId}
            onChange={(e) => setEventId(e.target.value)}
            data-testid="dlq-event-id"
          />
          <button
            type="button"
            className="rounded border border-border px-3 py-2 text-sm font-medium hover:bg-surface"
            disabled={!eventId || inspectMutation.isPending}
            onClick={() => inspectMutation.mutate(eventId.trim())}
          >
            Inspect
          </button>
          <button
            type="button"
            className="rounded border border-accent bg-accent/15 px-3 py-2 text-sm font-medium text-accent disabled:opacity-50"
            disabled={!eventId || retryMutation.isPending}
            onClick={() => retryMutation.mutate(eventId.trim())}
          >
            Retry
          </button>
        </div>
        {detail ? (
          <dl className="grid gap-2 text-sm sm:grid-cols-2" data-testid="dlq-detail">
            <div>
              <dt className="text-text-muted">Status</dt>
              <dd className="font-medium">{detail.status}</dd>
            </div>
            <div>
              <dt className="text-text-muted">Event type</dt>
              <dd>{detail.eventType}</dd>
            </div>
            <div>
              <dt className="text-text-muted">Aggregate</dt>
              <dd className="font-mono text-xs">
                {detail.aggregateType} / {detail.aggregateId}
              </dd>
            </div>
            <div>
              <dt className="text-text-muted">Retries</dt>
              <dd>{detail.retryCount}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-text-muted">Last error</dt>
              <dd className="mt-1 whitespace-pre-wrap break-all rounded border border-border bg-surface px-3 py-2 font-mono text-xs">
                {detail.lastError ?? '—'}
              </dd>
            </div>
          </dl>
        ) : null}
      </section>
    </div>
  );
}
