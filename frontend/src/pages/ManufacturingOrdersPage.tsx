import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList, Plus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, ProductVariant, ProductionOrder, TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import { DensityToggle } from '@/components/ui/DensityToggle';
import { VariantThumb } from '@/components/ui/VariantThumb';
import { useClientSort } from '@/hooks/useClientSort';
import { useSessionStore } from '@/stores/session';
import { cn } from '@/lib/utils';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  COMPONENTS_ALLOCATED: 'bg-accent-muted text-accent',
  WIP: 'bg-warning/20 text-warning',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-danger/20 text-danger',
};

function CreateProductionOrderModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [parentVariantId, setParentVariantId] = useState('');
  const [qtyTarget, setQtyTarget] = useState('1');
  const [error, setError] = useState('');

  const { data: variantsPage } = useQuery({
    queryKey: ['variants', 'all'],
    queryFn: async () =>
      (await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=200')).data,
    enabled: open,
  });
  const variants = variantsPage?.items ?? [];

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/manufacturing/orders', {
        parentVariantId,
        qtyTarget: Number(qtyTarget),
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['manufacturing'] });
      setParentVariantId('');
      setQtyTarget('1');
      onClose();
    },
    onError: () => setError('Could not create production order. Ensure a BOM exists for the variant.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="New production order" description="Build finished goods from a BOM">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select
          label="Finished good"
          value={parentVariantId}
          onChange={(e) => setParentVariantId(e.target.value)}
          required
        >
          <option value="" disabled>Select variant…</option>
          {variants.map((v) => (
            <option key={v.id} value={v.id}>{v.sku} — {v.name}</option>
          ))}
        </Select>
        <Input
          label="Quantity to build"
          type="number"
          min="1"
          value={qtyTarget}
          onChange={(e) => setQtyTarget(e.target.value)}
          required
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending} disabled={!parentVariantId}>Create order</Button>
        </div>
      </form>
    </Modal>
  );
}

function DisassembleModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [variantId, setVariantId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [error, setError] = useState('');

  const { data: variantsPage } = useQuery({
    queryKey: ['variants', 'all'],
    queryFn: async () =>
      (await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=200')).data,
    enabled: open,
  });
  const variants = variantsPage?.items ?? [];

  const { data: locations = [] } = useQuery({
    queryKey: ['locations'],
    queryFn: async () => (await apiClient.get<TenantLocation[]>('/api/v1/locations')).data,
    enabled: open,
  });

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/manufacturing/disassemble', {
        variantId,
        locationId,
        quantity: Number(quantity),
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['manufacturing'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setVariantId('');
      setLocationId('');
      setQuantity('1');
      onClose();
    },
    onError: () => setError('Could not disassemble. Check stock and BOM.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Disassemble" description="Split finished goods back into components">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select label="Finished good" value={variantId} onChange={(e) => setVariantId(e.target.value)} required>
          <option value="" disabled>Select variant…</option>
          {variants.map((v) => (
            <option key={v.id} value={v.id}>{v.sku} — {v.name}</option>
          ))}
        </Select>
        <Select label="Location" value={locationId} onChange={(e) => setLocationId(e.target.value)} required>
          <option value="" disabled>Select location…</option>
          {locations.map((loc) => (
            <option key={loc.id} value={loc.id}>{loc.path ?? loc.name}</option>
          ))}
        </Select>
        <Input label="Quantity" type="number" min="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending} disabled={!variantId || !locationId}>Disassemble</Button>
        </div>
      </form>
    </Modal>
  );
}

function ProductionOrdersTable({
  orders,
  canManage,
  allocatePending,
  onAllocate,
  onOpenTerminal,
}: {
  orders: ProductionOrder[];
  canManage: boolean;
  allocatePending: boolean;
  onAllocate: (orderId: string) => void;
  onOpenTerminal: () => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    orders,
    {
      number: (o) => o.number,
      product: (o) => o.parentSku ?? o.parentName ?? o.parentVariantId,
      status: (o) => o.status,
      target: (o) => o.qtyTarget,
      produced: (o) => o.qtyProduced,
    },
    { key: 'number', dir: 'desc' },
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="number" sort={sort} onSort={toggle}>
            Number
          </TableHead>
          <TableHead sortable sortKey="product" sort={sort} onSort={toggle}>
            Product
          </TableHead>
          <TableHead className="w-12">
            <span className="sr-only">Thumbnail</span>
          </TableHead>
          <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
            Status
          </TableHead>
          <TableHead sortable sortKey="target" sort={sort} onSort={toggle} align="right">
            Target
          </TableHead>
          <TableHead sortable sortKey="produced" sort={sort} onSort={toggle} align="right">
            Produced
          </TableHead>
          <TableHead>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((order) => (
          <TableRow key={order.id}>
            <TableCell mono>{order.number}</TableCell>
            <TableCell>{order.parentSku ?? order.parentName ?? order.parentVariantId}</TableCell>
            <TableCell>
              <VariantThumb
                url={order.primaryMediaUrl}
                alt={order.parentName ?? order.parentSku ?? 'Finished good'}
                previewCaption={order.parentSku ?? order.parentName ?? order.number}
                size="sm"
              />
            </TableCell>
            <TableCell>
              <span
                className={cn(
                  'rounded-full px-2 py-0.5 text-xs font-medium',
                  STATUS_STYLES[order.status] ?? 'bg-surface-overlay text-text-muted',
                )}
              >
                {order.status.replace(/_/g, ' ')}
              </span>
            </TableCell>
            <TableCell align="right" mono>
              {order.qtyTarget}
            </TableCell>
            <TableCell align="right" mono>
              {order.qtyProduced}
            </TableCell>
            <TableCell>
              {canManage && order.status === 'DRAFT' && (
                <Button
                  variant="secondary"
                  size="sm"
                  loading={allocatePending}
                  onClick={() => onAllocate(order.id)}
                >
                  Allocate
                </Button>
              )}
              {order.status === 'COMPONENTS_ALLOCATED' || order.status === 'WIP' ? (
                <Button variant="ghost" size="sm" onClick={onOpenTerminal}>
                  Terminal
                </Button>
              ) : null}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function ManufacturingOrdersPage() {
  const navigate = useNavigate();
  const canManage = useSessionStore((s) => s.canManageInventory());
  const [modalOpen, setModalOpen] = useState(false);
  const [disassembleOpen, setDisassembleOpen] = useState(false);

  const { data, isLoading, isError, error, refetch } = useListQuery<ProductionOrder>(
    ['manufacturing', 'orders'],
    '/api/v1/manufacturing/orders',
  );

  const queryClient = useQueryClient();
  const allocateMutation = useMutation({
    mutationFn: async (orderId: string) => {
      await apiClient.post(`/api/v1/manufacturing/orders/${orderId}/allocate`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['manufacturing'] });
    },
  });

  return (
    <div className="mx-auto min-h-0 w-full max-w-7xl overflow-y-auto overscroll-contain p-4 sm:p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Production Orders</h1>
          <p className="mt-1 text-sm text-text-muted">Assembly and kitting workflows</p>
        </div>
        <div className="flex gap-3">
          <DensityToggle />
          <Button variant="secondary" onClick={() => navigate('/manufacturing/terminal')}>
            Production terminal
          </Button>
          {canManage && (
            <>
              <Button variant="secondary" onClick={() => setDisassembleOpen(true)}>
                Disassemble
              </Button>
              <Button onClick={() => setModalOpen(true)}>
                <Plus className="h-4 w-4" />
                New order
              </Button>
            </>
          )}
        </div>
      </div>

      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={data}
        refetch={refetch}
        emptyIcon={ClipboardList}
        emptyTitle="No production orders"
        emptyDescription="Create a production order to allocate components and build finished goods."
        emptyAction={
          canManage ? (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              Create production order
            </Button>
          ) : undefined
        }
      >
        {(orders) => (
          <ProductionOrdersTable
            orders={orders}
            canManage={canManage}
            allocatePending={allocateMutation.isPending}
            onAllocate={(id) => allocateMutation.mutate(id)}
            onOpenTerminal={() => navigate('/manufacturing/terminal')}
          />
        )}
      </ListPageState>

      <CreateProductionOrderModal open={modalOpen} onClose={() => setModalOpen(false)} />
      <DisassembleModal open={disassembleOpen} onClose={() => setDisassembleOpen(false)} />
    </div>
  );
}
