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



export function ManufacturingOrdersPage() {

  const navigate = useNavigate();

  const canManage = useSessionStore((s) => s.canManageInventory());

  const [modalOpen, setModalOpen] = useState(false);

  const [disassembleOpen, setDisassembleOpen] = useState(false);



  const { data, isLoading, isError, error, refetch } = useListQuery<ProductionOrder>(

    ['manufacturing', 'orders'],

    '/api/v1/manufacturing/orders'

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

    <div className="p-6">

      <div className="mb-6 flex items-center justify-between">

        <div>

          <h1 className="text-2xl font-bold text-text">Production Orders</h1>

          <p className="mt-1 text-sm text-text-muted">Assembly and kitting workflows</p>

        </div>

        <div className="flex gap-3">

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

          <Table>

            <TableHeader>

              <TableRow>

                <TableHead>Number</TableHead>

                <TableHead>Product</TableHead>

                <TableHead>Status</TableHead>

                <TableHead align="right">Target</TableHead>

                <TableHead align="right">Produced</TableHead>

                <TableHead>Actions</TableHead>

              </TableRow>

            </TableHeader>

            <TableBody>

              {orders.map((order) => (

                <TableRow key={order.id}>

                  <TableCell mono>{order.number}</TableCell>

                  <TableCell>{order.parentSku ?? order.parentName ?? order.parentVariantId}</TableCell>

                  <TableCell>

                    <span

                      className={cn(

                        'rounded-full px-2 py-0.5 text-xs font-medium',

                        STATUS_STYLES[order.status] ?? 'bg-surface-overlay text-text-muted'

                      )}

                    >

                      {order.status.replace(/_/g, ' ')}

                    </span>

                  </TableCell>

                  <TableCell align="right" mono>{order.qtyTarget}</TableCell>

                  <TableCell align="right" mono>{order.qtyProduced}</TableCell>

                  <TableCell>
                    {canManage && order.status === 'DRAFT' && (
                      <Button
                        variant="secondary"
                        size="sm"
                        loading={allocateMutation.isPending}
                        onClick={() => allocateMutation.mutate(order.id)}
                      >
                        Allocate
                      </Button>
                    )}
                    {order.status === 'COMPONENTS_ALLOCATED' || order.status === 'WIP' ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate('/manufacturing/terminal')}
                      >
                        Terminal
                      </Button>
                    ) : null}
                  </TableCell>

                </TableRow>

              ))}

            </TableBody>

          </Table>

        )}

      </ListPageState>



      <CreateProductionOrderModal open={modalOpen} onClose={() => setModalOpen(false)} />

      <DisassembleModal open={disassembleOpen} onClose={() => setDisassembleOpen(false)} />

    </div>

  );

}


