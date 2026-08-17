import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { PackageOpen, RotateCcw, ShoppingCart } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PortalCatalogItem, PortalOrder, PortalReorderLine } from '@/api/types';
import { acceptPortalQuote, mapPortalCatalog, type PortalCatalogItemRaw } from '@/api/portal';
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
import { useClientSort } from '@/hooks/useClientSort';
import { useShowroomCart } from '@/showroom/useShowroomCart';
import { useTranslation } from 'react-i18next';
import { ReturnsWizard } from '@/features/showroom/ReturnsWizard';

const RETURNABLE = new Set(['SHIPPED', 'PARTIALLY_SHIPPED', 'CLOSED']);

function ShowroomOrdersTable({
  orders,
  reorderPending,
  onReorder,
  onReturn,
}: {
  orders: PortalOrder[];
  reorderPending: boolean;
  onReorder: (orderId: string) => void;
  onReturn: (order: PortalOrder) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    orders,
    {
      number: (o) => o.number,
      status: (o) => o.status,
      total: (o) => o.total,
      created: (o) => o.createdAt,
    },
    { key: 'created', dir: 'desc' },
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="number" sort={sort} onSort={toggle}>
            Number
          </TableHead>
          <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
            Status
          </TableHead>
          <TableHead sortable sortKey="total" sort={sort} onSort={toggle} align="right">
            Total
          </TableHead>
          <TableHead sortable sortKey="created" sort={sort} onSort={toggle}>
            Created
          </TableHead>
          <TableHead align="right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((order) => (
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
              <div className="flex flex-wrap justify-end gap-2">
                {RETURNABLE.has(order.status) && (
                  <Button size="sm" variant="secondary" onClick={() => onReturn(order)}>
                    <PackageOpen className="h-3.5 w-3.5" />
                    Return Items
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="secondary"
                  loading={reorderPending}
                  onClick={() => onReorder(order.id)}
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  Reorder
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function QuoteReadyBanner({
  order,
  accepting,
  onAccept,
}: {
  order: PortalOrder;
  accepting: boolean;
  onAccept: () => void;
}) {
  const { t } = useTranslation();
  const discount = Number(order.manualDiscountTotal ?? 0);
  const expires = order.quoteExpiresAt ? new Date(order.quoteExpiresAt) : null;
  return (
    <div
      className="mb-6 rounded-xl border border-accent/40 bg-accent-muted p-5"
      data-testid={`quote-ready-${order.id}`}
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-accent">{t('sales.quoteReady')}</p>
      <h2 className="mt-1 text-xl font-bold text-text">{order.number}</h2>
      <p className="mt-2 text-sm text-text-muted">
        Your sales rep applied custom pricing
        {discount > 0
          ? ` — ${discount.toLocaleString(undefined, { style: 'currency', currency: order.currency })} off`
          : ''}
        {expires ? ` · expires ${expires.toLocaleDateString()}` : ''}.
      </p>
      {order.quoteNotes && <p className="mt-2 text-sm text-text">{order.quoteNotes}</p>}
      <Button className="mt-4 w-full py-3 text-base" loading={accepting} onClick={onAccept}>
        {t('sales.acceptQuote')}
      </Button>
    </div>
  );
}

export function ShowroomOrdersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { addLines } = useShowroomCart();
  const [returnOrder, setReturnOrder] = useState<PortalOrder | null>(null);
  const { data, isLoading, isError, error, refetch } = useListQuery<PortalOrder>(
    ['portal', 'orders'],
    '/api/v1/portal/orders',
  );

  const acceptQuoteMutation = useMutation({
    mutationFn: (orderId: string) => acceptPortalQuote(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['portal', 'orders'] });
    },
  });

  const readyQuotes = (data ?? []).filter((order) => order.status === 'QUOTE_READY');

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
        }>,
      );
      navigate('/showroom/catalog');
    },
  });

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Order history</h1>
        <p className="mt-1 text-sm text-text-muted">
          Reorder past wholesale orders or start a self-serve return
        </p>
      </div>

      {readyQuotes.map((order) => (
        <QuoteReadyBanner
          key={order.id}
          order={order}
          accepting={acceptQuoteMutation.isPending}
          onAccept={() => acceptQuoteMutation.mutate(order.id)}
        />
      ))}

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
          <ShowroomOrdersTable
            orders={orders}
            reorderPending={reorderMutation.isPending}
            onReorder={(id) => reorderMutation.mutate(id)}
            onReturn={setReturnOrder}
          />
        )}
      </ListPageState>

      {returnOrder && (
        <ReturnsWizard
          open={!!returnOrder}
          onClose={() => setReturnOrder(null)}
          salesOrderId={returnOrder.id}
          salesOrderNumber={returnOrder.number}
        />
      )}
    </div>
  );
}
