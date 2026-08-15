import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertCircle, CreditCard, FileText, RefreshCw, RotateCcw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type {
  PortalCatalogItem,
  PortalCreditSummary,
  PortalInvoice,
  PortalReorderLine,
  ShowroomBillingAccruals,
} from '@/api/types';
import { mapPortalCatalog, type PortalCatalogItemRaw } from '@/api/portal';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useClientSort } from '@/hooks/useClientSort';
import { cn } from '@/lib/utils';
import { useShowroomCart } from '@/showroom/useShowroomCart';

function PortalInvoicesTable({
  invoices,
  duplicatePending,
  onDuplicate,
}: {
  invoices: PortalInvoice[];
  duplicatePending: boolean;
  onDuplicate: (invoiceId: string) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    invoices,
    {
      number: (inv) => inv.number,
      status: (inv) => inv.status,
      total: (inv) => Number(inv.total),
      due: (inv) => inv.dueAt ?? '',
    },
    { key: 'due', dir: 'asc' },
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
          <TableHead sortable sortKey="due" sort={sort} onSort={toggle}>
            Due
          </TableHead>
          <TableHead align="right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((inv) => (
          <TableRow key={inv.id}>
            <TableCell mono>{inv.number}</TableCell>
            <TableCell>{inv.status}</TableCell>
            <TableCell align="right" mono>
              {Number(inv.total).toLocaleString(undefined, {
                style: 'currency',
                currency: inv.currency,
              })}
            </TableCell>
            <TableCell>
              {inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : '—'}
            </TableCell>
            <TableCell align="right">
              <Button
                size="sm"
                variant="secondary"
                loading={duplicatePending}
                onClick={() => onDuplicate(inv.id)}
              >
                <RotateCcw className="h-3.5 w-3.5" />
                Duplicate to cart
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function ShowroomBillingPage() {
  const navigate = useNavigate();
  const { addLines } = useShowroomCart();
  const {
    data: credit,
    isLoading: creditLoading,
    isError: creditError,
    refetch: refetchCredit,
  } = useQuery({
    queryKey: ['portal', 'credit'],
    queryFn: async () => {
      const res = await apiClient.get<PortalCreditSummary>('/api/v1/portal/credit');
      return res.data;
    },
    retry: false,
  });

  const {
    data: invoices = [],
    isLoading: invoicesLoading,
    isError: invoicesError,
    refetch: refetchInvoices,
  } = useQuery({
    queryKey: ['portal', 'invoices'],
    queryFn: async () => {
      const res = await apiClient.get<PortalInvoice[]>('/api/v1/portal/invoices');
      return res.data;
    },
    retry: false,
  });

  const { data: storageBilling, isLoading: storageLoading } = useQuery({
    queryKey: ['showroom', 'billing', 'accruals'],
    queryFn: async () =>
      (await apiClient.get<ShowroomBillingAccruals>('/api/v1/showroom/billing/accruals')).data,
    retry: false,
  });

  if (creditLoading) {
    return (
      <div data-testid="list-page-loading">
        <TableSkeleton rows={4} cols={3} />
      </div>
    );
  }

  if (creditError) {
    return (
      <div className="p-6" data-testid="list-page-error">
        <EmptyState
          icon={AlertCircle}
          title="Unable to load billing"
          description="Check your connection and try again."
          action={
            <Button
              onClick={() => {
                void refetchCredit();
                void refetchInvoices();
              }}
              data-testid="list-page-retry"
            >
              <RefreshCw className="h-4 w-4" />
              Retry
            </Button>
          }
        />
      </div>
    );
  }

  const limit = Number(credit?.creditLimit ?? 0);
  const available = Number(credit?.availableCredit ?? 0);
  const used = limit > 0 ? limit - available : 0;
  const utilization = limit > 0 ? (used / limit) * 100 : 0;
  const openInvoices = invoices.filter((inv) => inv.status === 'OPEN' || inv.status === 'PARTIALLY_PAID');

  const duplicateMutation = useMutation({
    mutationFn: async (invoiceId: string) => {
      const [linesRes, catalogRes] = await Promise.all([
        apiClient.get<PortalReorderLine[]>(`/api/v1/portal/invoices/${invoiceId}/reorder-lines`),
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
      addLines(lines.filter((l) => l.catalogItem) as Array<{ variantId: string; quantity: number; catalogItem: PortalCatalogItem }>);
      navigate('/showroom/catalog');
    },
  });

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Billing & Credit</h1>
        <p className="mt-1 text-sm text-text-muted">NET terms credit line and outstanding invoices</p>
      </div>

      <Card className="mb-6">
        <CardHeader title="Credit utilization" description={`Status: ${credit?.status ?? '—'}`} />
        <div className="mb-2 flex justify-between text-sm">
          <span className="text-text-muted">Used</span>
          <span className="font-mono font-medium text-text">
            {used.toLocaleString(undefined, { style: 'currency', currency: 'USD' })} /{' '}
            {limit.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
          </span>
        </div>
        <div className="h-3 overflow-hidden rounded-full bg-surface-overlay">
          <div
            className={cn(
              'h-full rounded-full transition-all',
              utilization > 80 ? 'bg-warning' : 'bg-accent',
            )}
            style={{ width: `${Math.min(100, utilization)}%` }}
          />
        </div>
        <p className="mt-3 text-sm text-text-muted">
          Available:{' '}
          <span className="font-mono font-semibold text-text">
            {available.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
          </span>
        </p>
      </Card>

      <Card className="mb-6" data-testid="showroom-storage-accruals">
        <CardHeader
          title="3PL storage (month-to-date)"
          description={
            storageBilling?.sla
              ? `${storageBilling.sla.storageMode.replaceAll('_', ' ')} · ${Number(
                  storageBilling.sla.ratePerUnit,
                ).toLocaleString(undefined, { style: 'currency', currency: 'USD' })} / unit`
              : 'Storage fees accrued under your warehouse SLA'
          }
        />
        {storageBilling?.sla && (
          <p
            className="mb-2 text-xs font-mono uppercase tracking-wide text-text-muted"
            data-testid="showroom-sla-mode"
          >
            {storageBilling.sla.storageMode}
          </p>
        )}
        {storageLoading ? (
          <TableSkeleton rows={3} cols={3} />
        ) : (
          <>
            <p className="mb-3 text-sm text-text-muted">
              MTD total:{' '}
              <span className="font-mono font-semibold text-text">
                {Number(storageBilling?.monthToDateTotal ?? 0).toLocaleString(undefined, {
                  style: 'currency',
                  currency: 'USD',
                })}
              </span>
            </p>
            {(storageBilling?.accruals?.length ?? 0) === 0 ? (
              <p className="text-sm text-text-muted">No storage accruals this month.</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead align="right">Amount</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {storageBilling?.accruals.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>
                        {new Date(row.accrualDate + 'T00:00:00').toLocaleDateString()}
                      </TableCell>
                      <TableCell>{row.status}</TableCell>
                      <TableCell align="right" mono>
                        {Number(row.amount).toLocaleString(undefined, {
                          style: 'currency',
                          currency: 'USD',
                        })}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </>
        )}
      </Card>

      <Card>
        <CardHeader
          title="Outstanding invoices"
          description="Pay balance to restore available credit"
          action={
            openInvoices.length > 0 ? (
              <span className="flex items-center gap-1 text-sm text-accent">
                <CreditCard className="h-4 w-4" />
                Contact AR to pay
              </span>
            ) : undefined
          }
        />
        {invoicesLoading ? (
          <div data-testid="list-page-loading">
            <TableSkeleton rows={4} cols={4} />
          </div>
        ) : invoicesError ? (
          <div data-testid="list-page-error">
            <EmptyState
              icon={AlertCircle}
              title="Unable to load invoices"
              description="Check your connection and try again."
              action={
                <Button onClick={() => void refetchInvoices()} data-testid="list-page-retry">
                  <RefreshCw className="h-4 w-4" />
                  Retry
                </Button>
              }
            />
          </div>
        ) : invoices.length === 0 ? (
          <p className="text-sm text-text-muted" data-testid="list-page-empty">
            No invoices yet.
          </p>
        ) : (
          <PortalInvoicesTable
            invoices={invoices}
            duplicatePending={duplicateMutation.isPending}
            onDuplicate={(id) => duplicateMutation.mutate(id)}
          />
        )}
      </Card>

      {openInvoices.length > 0 && (
        <Card className="mt-6 flex items-center gap-3 p-4">
          <FileText className="h-5 w-5 text-accent" />
          <p className="text-sm text-text-muted">
            {openInvoices.length} open invoice{openInvoices.length === 1 ? '' : 's'} — payment
            restores your credit line automatically.
          </p>
        </Card>
      )}
    </div>
  );
}
