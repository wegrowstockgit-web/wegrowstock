import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Lock } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Bom, ProductionOrder } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  COMPONENTS_ALLOCATED: 'bg-accent-muted text-accent',
  WIP: 'bg-warning/20 text-warning',
  IN_ROUTING: 'bg-warning/20 text-warning',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-danger/20 text-danger',
};

type OperationRow = { id: string; name: string; defaultHourlyRate?: number };

export function ProductionOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [confirm, setConfirm] = useState<'release' | 'scrap' | null>(null);
  const [scrapQty, setScrapQty] = useState('1');
  const [scrapVariantId, setScrapVariantId] = useState('');

  const orderQuery = useQuery({
    queryKey: ['manufacturing', 'orders', id],
    queryFn: async () => (await apiClient.get<ProductionOrder>(`/api/v1/manufacturing/orders/${id}`)).data,
    enabled: !!id,
  });

  const bomListQuery = useQuery({
    queryKey: ['manufacturing', 'boms'],
    queryFn: async () => (await apiClient.get<Bom[]>('/api/v1/manufacturing/boms')).data,
  });
  const matchedBomId = (bomListQuery.data ?? []).find((row) => row.parentVariantId === orderQuery.data?.parentVariantId)?.id;
  const bomQuery = useQuery({
    queryKey: ['manufacturing', 'boms', matchedBomId],
    queryFn: async () => (await apiClient.get<Bom>(`/api/v1/manufacturing/boms/${matchedBomId}`)).data,
    enabled: !!matchedBomId,
  });

  const operationsQuery = useQuery({
    queryKey: ['manufacturing', 'operations', id],
    queryFn: async () =>
      (await apiClient.get<OperationRow[]>(`/api/v1/manufacturing/orders/${id}/operations`)).data,
    enabled: !!id,
  });

  const order = orderQuery.data;
  const bom = bomQuery.data;
  const components = bom?.lines ?? [];
  const operations = operationsQuery.data ?? [];
  const draft = order?.status === 'DRAFT';
  const locked = !!order && order.status !== 'DRAFT';

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['manufacturing'] });
  };

  const releaseMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/manufacturing/orders/${id}/release`),
    onSuccess: async () => {
      await invalidate();
      toast('Released to the floor. The BOM definition is locked.', { tone: 'success' });
    },
    onError: () => toast('Could not release this production order.', { tone: 'danger' }),
  });

  const scrapMutation = useMutation({
    mutationFn: async () =>
      apiClient.post(`/api/v1/manufacturing/orders/${id}/scrap`, {
        variantId: scrapVariantId || components[0]?.componentVariantId,
        quantity: Number(scrapQty),
      }),
    onSuccess: async () => {
      await invalidate();
      toast('Scrap written to the ledger. The original consume stays in history.', { tone: 'success' });
    },
    onError: () => toast('Could not log scrap.', { tone: 'danger' }),
  });

  if (orderQuery.isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted" data-testid="mo-workspace-loading">
        Loading production order…
      </div>
    );
  }

  if (orderQuery.isError || !order) {
    return (
      <div className="space-y-4 p-6" data-testid="mo-workspace-error">
        <p className="text-sm text-danger">This production order could not be loaded.</p>
        <Button variant="secondary" onClick={() => navigate('/manufacturing/orders')}>
          Back to production orders
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="mo-workspace" data-locked={locked ? 'true' : 'false'}>
      <header className="shrink-0 border-b border-border/60 px-6 py-4">
        <Link
          to="/manufacturing/orders"
          className="inline-flex items-center gap-1.5 text-sm text-text-muted transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Production orders
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold text-text" data-testid="mo-workspace-title">
                {order.number}
              </h1>
              <span
                className={cn(
                  'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                  STATUS_STYLES[order.status] ?? 'bg-surface-overlay text-text-muted',
                )}
                data-testid="mo-workspace-status"
              >
                {order.status.replaceAll('_', ' ')}
              </span>
            </div>
            <p className="mt-1 text-sm text-text-muted">
              BOM {bom?.id ? bom.id.slice(0, 8) : '—'} · Expected yield {order.qtyTarget} · Produced {order.qtyProduced}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {draft ? (
              <Button data-testid="release-to-floor" onClick={() => setConfirm('release')} loading={releaseMutation.isPending}>
                Release to Floor
              </Button>
            ) : null}
            {locked && order.status !== 'COMPLETED' && order.status !== 'CANCELLED' ? (
              <RequireRole roles={['WAREHOUSE_MANAGER', 'PRODUCTION_SUPERVISOR', 'ADMIN']}>
                <Button
                  variant="danger"
                  data-testid="log-scrap"
                  onClick={() => setConfirm('scrap')}
                  loading={scrapMutation.isPending}
                >
                  Log Scrap
                </Button>
              </RequireRole>
            ) : null}
          </div>
        </div>
      </header>

      {locked ? (
        <div className="mx-6 mt-4 flex items-start gap-3 rounded-lg border border-border bg-surface-overlay/60 px-4 py-3">
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-text-muted" aria-hidden />
          <p className="text-sm text-text">
            This BOM is locked for the run. Damaged components are written off with Log Scrap — weGrowStock never
            silently edits a posted consume.
          </p>
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <div className="grid gap-6 lg:grid-cols-2">
          <section>
            <h2 className="mb-3 text-sm font-semibold text-text">Components Required</h2>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>SKU</TableHead>
                  <TableHead align="right">Qty / unit</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {components.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={2} className="text-text-muted">
                      No BOM components found for this finished good.
                    </TableCell>
                  </TableRow>
                ) : (
                  components.map((line) => (
                    <TableRow key={line.id} data-testid="mo-component-row">
                      <TableCell mono>{line.componentSku ?? line.componentVariantId.slice(0, 8)}</TableCell>
                      <TableCell align="right" mono>
                        {line.quantityRequired}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </section>
          <section>
            <h2 className="mb-3 text-sm font-semibold text-text">Routing Steps</h2>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Step</TableHead>
                  <TableHead>Phase</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {operations.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={2} className="text-text-muted">
                      No routing steps on this BOM yet.
                    </TableCell>
                  </TableRow>
                ) : (
                  operations.map((step, index) => (
                    <TableRow key={step.id} data-testid="mo-routing-row">
                      <TableCell mono>{index + 1}</TableCell>
                      <TableCell>{step.name}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </section>
        </div>
      </div>

      {confirm === 'release' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Release to floor?"
          description="This reserves components and locks the BOM for this run."
          confirmLabel="Release to Floor"
          confirming={releaseMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            releaseMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'scrap' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Log scrap?"
          description="This permanently writes the damaged component to the scrap ledger."
          confirmLabel="Log Scrap"
          confirming={scrapMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            scrapMutation.mutate();
          }}
        >
          <div className="mb-4 space-y-3">
            <Input
              label="Component variant"
              value={scrapVariantId || components[0]?.componentVariantId || ''}
              onChange={(e) => setScrapVariantId(e.target.value)}
            />
            <Input label="Quantity" type="number" min="0.001" value={scrapQty} onChange={(e) => setScrapQty(e.target.value)} />
          </div>
        </AlertDialog>
      ) : null}
    </div>
  );
}
