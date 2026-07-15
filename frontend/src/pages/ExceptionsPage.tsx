import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { FulfillmentException } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ListPageState } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { cn } from '@/lib/utils';
import { useState } from 'react';

const STATUS_STYLES: Record<string, string> = {
  OPEN: 'bg-warning/15 text-warning',
  RESOLVED: 'bg-success/10 text-success',
  DISCARDED: 'bg-surface-overlay text-text-muted',
};

export function ExceptionsPage() {
  const queryClient = useQueryClient();
  const [lotById, setLotById] = useState<Record<string, string>>({});

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['office', 'exceptions'],
    queryFn: async () =>
      (await apiClient.get<FulfillmentException[]>('/api/v1/office/exceptions/list')).data,
  });

  const resolveMutation = useMutation({
    mutationFn: async (input: { id: string; action: string; lotNumber?: string }) => {
      await apiClient.post(`/api/v1/office/exceptions/${input.id}/resolve`, {
        action: input.action,
        lotNumber: input.lotNumber,
      });
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['office', 'exceptions'] }),
  });

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-center justify-between gap-4 border-b border-border/60 px-6 py-4">
        <div>
          <h1 className="text-2xl font-bold text-text">Fulfillment exceptions</h1>
          <p className="mt-1 text-sm text-text-muted">
            Damaged barcode shunts from the floor — clear, discard, or apply a manual lot
          </p>
        </div>
      </div>

      <div className="shrink-0 px-6 pt-4">
        <DataListToolbar />
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        <ListPageState
          isLoading={isLoading}
          isError={isError}
          error={error}
          data={data}
          refetch={refetch}
          emptyIcon={AlertTriangle}
          emptyTitle="No exceptions"
          emptyDescription="Floor Skip & Flag reports will appear here for manager resolution."
        >
          {(items) => (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Status</TableHead>
                  <TableHead>Allocation</TableHead>
                  <TableHead>Reported</TableHead>
                  <TableHead>Reason</TableHead>
                  <TableHead align="right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((ex) => (
                  <TableRow key={ex.id}>
                    <TableCell>
                      <span
                        className={cn(
                          'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                          STATUS_STYLES[ex.resolutionStatus] ?? 'bg-surface-overlay text-text-muted',
                        )}
                      >
                        {ex.resolutionStatus}
                      </span>
                    </TableCell>
                    <TableCell mono className="text-xs">
                      {ex.allocationId.slice(0, 8)}…
                    </TableCell>
                    <TableCell className="text-text-muted">
                      {new Date(ex.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-sm text-text-muted">
                      {String(ex.metadata?.reason ?? '—')}
                    </TableCell>
                    <TableCell align="right">
                      {ex.resolutionStatus === 'OPEN' ? (
                        <div className="flex flex-wrap items-center justify-end gap-2">
                          <Input
                            className="h-9 w-28"
                            placeholder="Lot #"
                            value={lotById[ex.id] ?? ''}
                            onChange={(e) =>
                              setLotById((prev) => ({ ...prev, [ex.id]: e.target.value }))
                            }
                          />
                          <Button
                            size="sm"
                            variant="secondary"
                            loading={resolveMutation.isPending}
                            onClick={() =>
                              resolveMutation.mutate({
                                id: ex.id,
                                action: 'LOT_OVERRIDE',
                                lotNumber: lotById[ex.id],
                              })
                            }
                            disabled={!lotById[ex.id]?.trim()}
                          >
                            Lot override
                          </Button>
                          <Button
                            size="sm"
                            loading={resolveMutation.isPending}
                            onClick={() => resolveMutation.mutate({ id: ex.id, action: 'CLEAR' })}
                          >
                            Clear
                          </Button>
                          <Button
                            size="sm"
                            variant="secondary"
                            loading={resolveMutation.isPending}
                            onClick={() =>
                              resolveMutation.mutate({ id: ex.id, action: 'DISCARD' })
                            }
                          >
                            Discard
                          </Button>
                        </div>
                      ) : (
                        <span className="text-xs text-text-muted">—</span>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </ListPageState>
      </div>
    </div>
  );
}
