import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { RotateCcw, ShoppingCart } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PortalCatalogItem, PortalOrder, PortalReorderLine } from '@/api/types';
import { mapPortalCatalog, type PortalCatalogItemRaw } from '@/api/portal';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import { useShowroomCart } from '@/showroom/useShowroomCart';

export function ShowroomOrdersPage() {
  const navigate = useNavigate();
  const { addLines } = useShowroomCart();
  const { data, isLoading, isError, error, refetch } = useListQuery<PortalOrder>(
    ['portal', 'orders'],
    '/api/v1/portal/orders'
  );

  const reorderMutation = useMutation({
    mutationFn: async (orderId: string) => {
      const [linesRes, catalogRes] = await Promise.all([
        apiClient.get<PortalReorderLine[]>(`/api/v1/portal/orders/${orderId}/reorder-lines`),
        apiClient.get<PortalCatalogItemRaw[]>('/api/v1/portal/catalog'),
      ]);
      const catalog = mapPortalCatalog(catalogRes.data);
      const catalogById = new Map(catalog.map((c) => [c.id, c]));
      return linesRes.data.map((line) => ({
        variantId: line.variantId,
        quantity: Number(line.quantity),
        catalogItem: catalogById.get(line.variantId),
      }));
    },
    onSuccess: (lines) => {
      addLines(
        lines.filter((l) => l.catalogItem) as Array<{
          variantId: string;
          quantity: number;
          catalogItem: PortalCatalogItem;
        }>
      );
      navigate('/showroom/catalog');
    },
  });

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Order history</h1>
        <p className="mt-1 text-sm text-text-muted">
          Reorder past wholesale orders in one click
        </p>
      </div>

      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={data}
        refetch={refetch}
        emptyIcon={ShoppingCart}
        emptyTitle="No orders yet"
        emptyDescription="Place your first order from the catalog."
        emptyAction={
          <Button onClick={() => navigate('/showroom/catalog')}>Browse catalog</Button>
        }
      >
        {(orders) => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Number</TableHead>
                <TableHead>Status</TableHead>
                <TableHead align="right">Total</TableHead>
                <TableHead>Created</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {orders.map((order) => (
                <TableRow key={order.id}>
                  <TableCell mono>{order.number}</TableCell>
                  <TableCell>
                    <StatusBadge status={order.status} />
                  </TableCell>
                  <TableCell align="right" mono>
                    {order.total.toLocaleString(undefined, {
                      style: 'currency',
                      currency: order.currency,
                    })}
                  </TableCell>
                  <TableCell>{new Date(order.createdAt).toLocaleDateString()}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={reorderMutation.isPending}
                      onClick={() => reorderMutation.mutate(order.id)}
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      Reorder
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </ListPageState>
    </div>
  );
}
