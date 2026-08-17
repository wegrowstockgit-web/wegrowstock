import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { FulfillmentException } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ListPageState } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { TableDensityScope } from '@/hooks/useDensity';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { SyncConflictsPanel } from '@/features/offline/SyncConflictsPanel';
import { useClientSort } from '@/hooks/useClientSort';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';
import { useState, type Dispatch, type SetStateAction } from 'react';

const STATUS_STYLES: Record<string, string> = {
  OPEN: 'bg-warning/15 text-warning',
  RESOLVED: 'bg-success/10 text-success',
  DISCARDED: 'bg-surface-overlay text-text-muted',
};

type ActionTab = 'holds' | 'sync';

function ExceptionsTable({
  items,
  lotById,
  setLotById,
  resolveMutation,
  initiateRtv,
}: {
  items: FulfillmentException[];
  lotById: Record<string, string>;
  setLotById: Dispatch<SetStateAction<Record<string, string>>>;
  resolveMutation: {
    isPending: boolean;
    mutate: (vars: { id: string; action: string; lotNumber?: string }) => void;
  };
  initiateRtv: {
    isPending: boolean;
    mutate: (id: string) => void;
  };
}) {
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      status: (ex) => ex.resolutionStatus,
      allocation: (ex) => ex.allocationId,
      reported: (ex) => ex.createdAt,
      reason: (ex) => String(ex.metadata?.reason ?? ''),
    },
    { key: 'reported', dir: 'desc' },
  );
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
            Status
          </TableHead>
          <TableHead sortable sortKey="allocation" sort={sort} onSort={toggle}>
            Allocation
          </TableHead>
          <TableHead sortable sortKey="reported" sort={sort} onSort={toggle}>
            Reported
          </TableHead>
          <TableHead sortable sortKey="reason" sort={sort} onSort={toggle}>
            Reason
          </TableHead>
          <TableHead align="right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((ex) => (
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
                    onClick={() => resolveMutation.mutate({ id: ex.id, action: 'DISCARD' })}
                  >
                    Discard
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    data-testid={`initiate-rtv-${ex.id}`}
                    loading={initiateRtv.isPending}
                    onClick={() => initiateRtv.mutate(ex.id)}
                  >
                    Initiate RTV
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
  );
}

export function ExceptionsPage() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const activeTab: ActionTab = tabParam === 'sync' ? 'sync' : 'holds';
  const [lotById, setLotById] = useState<Record<string, string>>({});

  const setTab = (next: ActionTab) => {
    setSearchParams(next === 'holds' ? {} : { tab: next }, { replace: true });
  };

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['office', 'exceptions'],
    queryFn: async () =>
      (await apiClient.get<FulfillmentException[]>('/api/v1/office/exceptions/list')).data,
    enabled: activeTab === 'holds',
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

  const initiateRtv = useMutation({
    mutationFn: async (exceptionId: string) => {
      const suppliers = (
        await apiClient.get<Array<{ id: string }>>('/api/v1/suppliers')
      ).data;
      const supplierId = suppliers[0]?.id;
      if (!supplierId) {
        throw new Error('No suppliers');
      }
      await apiClient.post('/api/v1/rtv/from-exception', {
        exceptionId,
        reasonCode: 'DEFECTIVE',
        qty: 1,
        supplierId,
      });
    },
    onSuccess: () => {
      toast('RTV draft created', { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['rtv-orders'] });
    },
    onError: () => toast('Could not initiate RTV — ensure a supplier exists', { tone: 'danger' }),
  });

  return (
    <TableDensityScope gridId="exceptions">
    <div className="flex h-full min-h-0 flex-col" data-testid="action-required-hub">
      <div className="flex shrink-0 flex-col gap-4 border-b border-border/60 px-4 py-4 sm:px-6">
        <div>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="text-2xl font-bold text-text">Action required</h1>
              <p className="mt-1 text-sm text-text-muted">
                Fulfillment holds and offline sync conflicts that need a manager decision
              </p>
            </div>
            <Link
              to="/purchasing/rtv"
              className="text-sm font-medium text-accent hover:underline"
              data-testid="exceptions-rtv-link"
            >
              RTV workspace →
            </Link>
          </div>
        </div>
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Action required tabs">
          <Button
            type="button"
            role="tab"
            aria-selected={activeTab === 'holds'}
            variant={activeTab === 'holds' ? 'primary' : 'secondary'}
            size="sm"
            data-testid="exceptions-tab-holds"
            onClick={() => setTab('holds')}
          >
            <AlertTriangle className="h-4 w-4" />
            Fulfillment Holds
          </Button>
          <Button
            type="button"
            role="tab"
            aria-selected={activeTab === 'sync'}
            variant={activeTab === 'sync' ? 'primary' : 'secondary'}
            size="sm"
            data-testid="exceptions-tab-sync"
            onClick={() => setTab('sync')}
          >
            <RefreshCw className="h-4 w-4" />
            Sync Conflicts
          </Button>
        </div>
      </div>

      {activeTab === 'holds' && (
        <>
          <div className="shrink-0 px-4 pt-4 sm:px-6">
            <DataListToolbar gridId="exceptions" />
          </div>
          <div className="min-h-0 flex-1 overflow-auto" data-list-scrollport="true">
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
                <ExceptionsTable
                  items={items}
                  lotById={lotById}
                  setLotById={setLotById}
                  resolveMutation={resolveMutation}
                  initiateRtv={initiateRtv}
                />
              )}
            </ListPageState>
          </div>
        </>
      )}

      {activeTab === 'sync' && (
        <div className="min-h-0 flex-1 overflow-auto p-4 sm:p-6" data-testid="exceptions-sync-tab">
          <SyncConflictsPanel />
        </div>
      )}
    </div>
    </TableDensityScope>
  );
}
