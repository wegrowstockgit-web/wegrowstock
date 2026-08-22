import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, FileText, Mail, Plus } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Invoice, InvoiceDetail, SalesOrder } from '@/api/types';
import { cn, formatCurrency } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import { SavedFilterViews } from '@/components/ui/SavedFilterViews';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { TableDensityScope } from '@/hooks/useDensity';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { useToast } from '@/components/ui/Toast';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState } from '@/components/layout/ListPageState';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { Pagination } from '@/components/ui/Pagination';
import { useClientSort } from '@/hooks/useClientSort';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { unwrapPageItems } from '@/api/page';
import { listInvoices } from '@/api/operational';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  OPEN: 'bg-accent-muted text-accent',
  PARTIALLY_PAID: 'bg-warning/10 text-warning',
  PAID: 'bg-success/10 text-success',
  VOID: 'bg-danger/10 text-danger',
};

function InvoicesTable({
  items,
  onPeek,
}: {
  items: Invoice[];
  onPeek: (id: string) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      number: (inv) => inv.number,
      customer: (inv) => inv.customerName,
      status: (inv) => inv.status,
      total: (inv) => inv.total,
      due: (inv) => inv.dueAt ?? '',
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
          <TableHead sortable sortKey="customer" sort={sort} onSort={toggle}>
            Customer
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
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((inv) => (
          <TableRow key={inv.id} className="cursor-pointer" onClick={() => onPeek(inv.id)}>
            <TableCell mono>{inv.number}</TableCell>
            <TableCell>{inv.customerName}</TableCell>
            <TableCell>
              <span
                className={cn(
                  'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                  STATUS_STYLES[inv.status] ?? 'bg-surface-overlay text-text-muted',
                )}
              >
                {inv.status.replaceAll('_', ' ')}
              </span>
            </TableCell>
            <TableCell align="right" mono>
              {formatCurrency(inv.total, inv.currency)}
            </TableCell>
            <TableCell className="text-text-muted">
              {inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : '—'}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function CreateInvoiceModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [salesOrderId, setSalesOrderId] = useState('');
  const [error, setError] = useState('');

  const { data: orders = [] } = useQuery({
    queryKey: ['sales-orders', 'lookup'],
    queryFn: async () =>
      unwrapPageItems<SalesOrder>(
        (await apiClient.get('/api/v1/sales-orders', { params: { page: 1, size: 100 } })).data,
      ),
    enabled: open,
  });

  // Invoices are generated from fulfilled or allocated orders that still have billable qty
  const invoiceable = orders.filter(
    (o) =>
      ['ALLOCATED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED'].includes(o.status) &&
      o.billingStatus !== 'INVOICED'
  );

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post(`/api/v1/invoices/from-sales-order/${salesOrderId}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setSalesOrderId('');
      onClose();
    },
    onError: () => setError('Could not create the invoice from that order.'),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Create invoice"
      description="Invoices are generated from a sales order"
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select
          label="Sales order"
          value={salesOrderId}
          onChange={(e) => setSalesOrderId(e.target.value)}
          required
        >
          <option value="" disabled>
            {invoiceable.length === 0 ? 'No invoiceable orders (allocate one first)' : 'Select a sales order…'}
          </option>
          {invoiceable.map((o) => (
            <option key={o.id} value={o.id}>
              {o.number} — {o.customerName} ({o.status})
            </option>
          ))}
        </Select>

        {error && <p className="text-sm text-danger">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} disabled={!salesOrderId}>
            Create invoice
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function InvoicesPage() {
  const navigate = useNavigate();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN');
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [peekInvoiceId, setPeekInvoiceId] = useState<string | null>(null);

  const table = useServerTableQuery<Invoice>({
    queryKey: 'invoices',
    path: '/api/v1/invoices',
    defaultSort: 'createdAt,desc',
    extraParams: { status: statusFilter || undefined },
    fetcher: listInvoices,
  });
  const { items, isLoading, isError, error, refetch, search } = table;

  const { data: peekInvoice } = useQuery({
    queryKey: ['invoices', peekInvoiceId],
    queryFn: async () =>
      (await apiClient.get<InvoiceDetail>(`/api/v1/invoices/${peekInvoiceId}`)).data,
    enabled: !!peekInvoiceId,
  });

  const emailMutation = useMutation({
    mutationFn: async (invoiceId: string) =>
      (await apiClient.post<{ sent: boolean; to: string }>(
        `/api/v1/documents/invoice/${invoiceId}/email`,
      )).data,
    onSuccess: (result) => {
      toast(`Invoice emailed to ${result.to}`, { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['invoices', peekInvoiceId] });
    },
    onError: (err: Error) => {
      toast(err.message || 'Could not email invoice', { tone: 'danger' });
    },
  });

  const downloadPdf = async (invoiceId: string, number: string) => {
    try {
      const res = await apiClient.get(`/api/v1/documents/invoice/${invoiceId}/pdf`, {
        responseType: 'blob',
      });
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${number || 'invoice'}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      void queryClient.invalidateQueries({ queryKey: ['invoices', invoiceId] });
    } catch (err) {
      toast((err as Error).message || 'Could not download PDF', { tone: 'danger' });
    }
  };

  const filtered = items;

  const invoicePresets = [
    { id: 'all', label: 'All', filters: {} as Record<string, string> },
    { id: 'open', label: 'Open', filters: { status: 'OPEN' } },
    { id: 'paid', label: 'Paid', filters: { status: 'PAID' } },
    { id: 'partial', label: 'Partial', filters: { status: 'PARTIALLY_PAID' } },
  ];

  return (
    <TableDensityScope gridId="invoices">
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Invoices</h1>
          <p className="mt-1 text-sm text-text-muted">Billing and payments</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            New invoice
          </Button>
        )}
      </div>

      <DataListToolbar gridId="invoices">
        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <DebouncedSearchInput
            value={search}
            onDebouncedChange={table.setSearch}
            placeholder="Search invoices or customers…"
          />
          <SavedFilterViews
            className="mb-0"
            storageKey="invoices-filters"
            activeFilters={{ status: statusFilter }}
            onApply={(f) => {
              setStatusFilter(f.status ?? '');
              table.setPage(1);
            }}
            defaultPresets={invoicePresets}
          />
        </div>
      </DataListToolbar>

      <ListPageState
        isLoading={isLoading && items.length === 0}
        isError={isError}
        error={error}
        data={filtered}
        refetch={refetch}
        emptyIcon={FileText}
        emptyTitle={search ? 'No matching invoices' : 'No invoices yet'}
        emptyDescription={
          canCreate
            ? 'Create an invoice from an allocated or shipped sales order.'
            : 'Invoices will appear here once created by an admin.'
        }
        emptyAction={
          canCreate ? (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              Create invoice
            </Button>
          ) : undefined
        }
      >
        {(rows) => (
          <>
            <InvoicesTable items={rows} onPeek={setPeekInvoiceId} />
            <Pagination
              page={table.page}
              totalPages={table.totalPages}
              totalElements={table.totalElements}
              size={table.size}
              onPageChange={table.setPage}
              onSizeChange={table.setSize}
            />
          </>
        )}
      </ListPageState>

      <CreateInvoiceModal open={modalOpen} onClose={() => setModalOpen(false)} />

      <RightPeekDrawer
        open={!!peekInvoiceId}
        onClose={() => setPeekInvoiceId(null)}
        title={peekInvoice?.number ?? 'Invoice'}
        description={peekInvoice ? `${peekInvoice.customerName} · ${peekInvoice.status.replaceAll('_', ' ')}` : undefined}
      >
        {peekInvoice ? (
          <div className="space-y-4">
            <Button
              className="w-full"
              data-testid="open-invoice-workspace"
              onClick={() => {
                setPeekInvoiceId(null);
                navigate(`/invoices/${peekInvoice.id}`);
              }}
            >
              Open Workspace
            </Button>
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between">
                <dt className="text-text-muted">Total</dt>
                <dd className="font-mono font-semibold">{formatCurrency(peekInvoice.total, peekInvoice.currency)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-muted">Due</dt>
                <dd>{peekInvoice.dueAt ? new Date(peekInvoice.dueAt).toLocaleDateString() : '—'}</dd>
              </div>
              {peekInvoice.salesOrderId && (
                <div className="flex justify-between">
                  <dt className="text-text-muted">Sales order</dt>
                  <dd className="font-mono text-xs">{peekInvoice.salesOrderId.slice(0, 8)}…</dd>
                </div>
              )}
              {peekInvoice.documentUrl ? (
                <div className="flex justify-between gap-2">
                  <dt className="text-text-muted">Archived PDF</dt>
                  <dd className="truncate font-mono text-xs text-text-muted" title={peekInvoice.documentUrl}>
                    {peekInvoice.documentUrl}
                  </dd>
                </div>
              ) : null}
            </dl>
            {canCreate ? (
              <div className="flex flex-col gap-2 border-t border-border pt-4">
                <Button
                  type="button"
                  variant="secondary"
                  className="w-full"
                  data-testid="invoice-download-pdf"
                  onClick={() => void downloadPdf(peekInvoice.id, peekInvoice.number)}
                >
                  <Download className="h-4 w-4" />
                  Download PDF
                </Button>
                <Button
                  type="button"
                  className="w-full"
                  data-testid="invoice-email-pdf"
                  loading={emailMutation.isPending}
                  onClick={() => emailMutation.mutate(peekInvoice.id)}
                >
                  <Mail className="h-4 w-4" />
                  Email invoice
                </Button>
              </div>
            ) : null}
          </div>
        ) : (
          <p className="text-sm text-text-muted">Loading…</p>
        )}
      </RightPeekDrawer>
    </div>
    </TableDensityScope>
  );
}
