import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, Trash2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { enqueueMutation } from '@/offline/mutationQueue';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';

export interface ServerSyncConflict {
  id: string;
  payload: {
    method?: string;
    url?: string;
    body?: unknown;
    idempotencyKey?: string;
    errorCode?: string;
  };
  errorMessage: string | null;
  status: string;
  createdAt: string;
}

export function SyncConflictsPanel() {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['offline_sync_conflicts'],
    queryFn: async () =>
      (await apiClient.get<ServerSyncConflict[]>('/api/v1/offline-sync-conflicts', {
        params: { status: 'PENDING' },
      })).data,
    retry: false,
  });

  const dismissMutation = useMutation({
    mutationFn: async (id: string) =>
      (await apiClient.post(`/api/v1/offline-sync-conflicts/${id}/dismiss`)).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['offline_sync_conflicts'] });
      toast('Conflict dismissed', { tone: 'success' });
    },
    onError: () => toast('Could not dismiss conflict', { tone: 'danger' }),
  });

  const retryMutation = useMutation({
    mutationFn: async (conflict: ServerSyncConflict) => {
      await apiClient.post(`/api/v1/offline-sync-conflicts/${conflict.id}/retry`);
      const method = (conflict.payload.method ?? 'POST').toUpperCase() as
        | 'POST'
        | 'PUT'
        | 'PATCH'
        | 'DELETE';
      const url = conflict.payload.url;
      if (url) {
        await enqueueMutation({
          method,
          url,
          body: conflict.payload.body,
          idempotencyKey: crypto.randomUUID(),
        });
      }
      await apiClient.post(`/api/v1/offline-sync-conflicts/${conflict.id}/resolved`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['offline_sync_conflicts'] });
      toast('Conflict re-queued for force retry', { tone: 'success' });
    },
    onError: () => toast('Could not force retry', { tone: 'danger' }),
  });

  return (
    <section className="rounded-2xl bg-surface-raised p-5 shadow-card" data-testid="sync-conflicts-panel">
      <div className="mb-4">
        <h2 className="text-sm font-semibold text-text">Sync conflicts</h2>
        <p className="text-sm text-text-muted">
          Offline mutations that failed business rules (for example ATP). Force retry re-enqueues them; dismiss
          clears the server DLQ entry.
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-text-muted">Loading conflicts…</p>
      ) : isError ? (
        <p className="text-sm text-danger">Could not load sync conflicts.</p>
      ) : data.length === 0 ? (
        <p className="text-sm text-text-muted">No pending sync conflicts.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>When</TableHead>
              <TableHead>Error</TableHead>
              <TableHead>Endpoint</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((row) => (
              <TableRow key={row.id} data-testid={`sync-conflict-${row.id}`}>
                <TableCell className="whitespace-nowrap text-xs text-text-muted">
                  {row.createdAt
                    ? new Date(row.createdAt).toLocaleString(undefined, {
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })
                    : '—'}
                </TableCell>
                <TableCell className="max-w-xs truncate text-sm">{row.errorMessage ?? '—'}</TableCell>
                <TableCell className="font-mono text-xs">{row.payload.url ?? '—'}</TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      aria-label="Force retry"
                      data-testid={`force-retry-${row.id}`}
                      loading={retryMutation.isPending}
                      onClick={() => retryMutation.mutate(row)}
                    >
                      <RefreshCw className="h-3.5 w-3.5" />
                      Force Retry
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      aria-label="Dismiss conflict"
                      data-testid={`dismiss-conflict-${row.id}`}
                      loading={dismissMutation.isPending}
                      onClick={() => dismissMutation.mutate(row.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      Dismiss
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </section>
  );
}
