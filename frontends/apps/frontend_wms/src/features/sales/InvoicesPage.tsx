import { useMemo, useState, type MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FileText, Mail, MoreVertical, Plus } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Invoice, SalesOrder } from '@/api/types';
import { cn, formatCurrency } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { SavedFilterViews } from '@/components/ui/SavedFilterViews';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { TableDensityScope } from '@/hooks/useDensity';
import { useToast } from '@/components/ui/Toast';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { ListPageState } from '@/components/layout/ListPageState';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { Pagination } from '@/components/ui/Pagination';
import { useClientSort } from '@/hooks/useClientSort';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { listInvoices } from '@/api/operational';
import { ShippedSalesOrderCombobox } from '@/features/sales/ShippedSalesOrderCombobox';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  OPEN: 'bg-accent-muted text-accent',
  ISSUED: 'bg-accent-muted text-accent',
  PARTIALLY_PAID: 'bg-warning/10 text-warning',
  PAID: 'bg-success/10 text-success',
  VOID: 'bg-danger/10 text-danger',
  OVERDUE: 'bg-danger/10 text-danger',
};

export function isInvoiceOverdue(inv: Invoice) {
  if (inv.overdue) return true;
  if (!inv.dueAt || inv.status === 'PAID' || inv.status === 'VOID' || inv.status === 'CREDIT_MEMO' || inv.status === 'DRAFT') {
    return false;
  }
  return new Date(inv.dueAt).getTime() < Date.now();
}

export function invoiceBalanceDue(inv: Invoice) {
  if (inv.balanceDue != null) return Number(inv.balanceDue);
  return Math.max(0, Number(inv.total ?? 0) - Number(inv.amountPaid ?? 0));
}

function displayStatus(inv: Invoice) {
  return isInvoiceOverdue(inv) ? 'OVERDUE' : inv.status;
}

function InvoicesTable({
  items,
  selected,
  onToggle,
  onToggleAll,
  onOpen,
  onLogPayment,
  onDownloadPdf,
  onEmail,
}: {
  items: Invoice[];
  selected: Set<string>;
  onToggle: (id: string) => void;
  onToggleAll: (ids: string[], checked: boolean) => void;
  onOpen: (id: string) => void;
  onLogPayment: (invoice: Invoice) => void;
  onDownloadPdf: (invoice: Invoice) => void;
  onEmail: (invoice: Invoice) => void;
}) {
  const hasRole = useSessionStore((s) => s.hasRole);
  const canDocs = hasRole('OWNER', 'ADMIN');
  const canPay = hasRole('OWNER', 'ADMIN', 'FINANCE_ADMIN');
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      number: (inv) => inv.number,
      customer: (inv) => inv.customerName,
      status: (inv) => displayStatus(inv),
      total: (inv) => inv.total,
      balance: (inv) => invoiceBalanceDue(inv),
      due: (inv) => inv.dueAt ?? '',
    },
    { key: 'number', dir: 'desc' },
  );
  const allIds = sorted.map((inv) => inv.id);
  const allSelected = allIds.length > 0 && allIds.every((id) => selected.has(id));
  return (
    <div className="min-w-0 w-full overflow-x-auto scrollbar-thin">
      <Table className="min-w-full table-auto">
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <input
                type="checkbox"
                aria-label="Select all invoices"
                data-testid="invoice-select-all"
                checked={allSelected}
                onChange={(e) => onToggleAll(allIds, e.target.checked)}
              />
            </TableHead>
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
            <TableHead sortable sortKey="balance" sort={sort} onSort={toggle} align="right">
              Balance Due
            </TableHead>
            <TableHead sortable sortKey="due" sort={sort} onSort={toggle}>
              Due
            </TableHead>
            <TableHead align="right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((inv) => {
            const overdue = isInvoiceOverdue(inv);
            const status = displayStatus(inv);
            const balance = invoiceBalanceDue(inv);
            const checked = selected.has(inv.id);
            return (
              <TableRow
                key={inv.id}
                className="cursor-pointer"
                selected={checked}
                onClick={() => onOpen(inv.id)}
                data-testid={`invoice-row-${inv.id}`}
              >
                <TableCell>
                  <input
                    type="checkbox"
                    aria-label={`Select ${inv.number}`}
                    data-testid={`invoice-select-${inv.id}`}
                    checked={checked}
                    onClick={(e: MouseEvent) => e.stopPropagation()}
                    onChange={() => onToggle(inv.id)}
                  />
                </TableCell>
                <TableCell mono>{inv.number}</TableCell>
                <TableCell>{inv.customerName}</TableCell>
                <TableCell>
                  <span
                    className={cn(
                      'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                      STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted',
                    )}
                    data-testid={`invoice-status-${inv.id}`}
                  >
                    {status.replaceAll('_', ' ')}
                  </span>
                </TableCell>
                <TableCell align="right" mono>
                  {formatCurrency(inv.total, inv.currency)}
                </TableCell>
                <TableCell align="right" mono data-testid={`invoice-balance-${inv.id}`}>
                  {formatCurrency(balance, inv.currency)}
                </TableCell>
                <TableCell
                  className={cn(overdue ? 'font-medium text-danger' : 'text-text-muted')}
                  data-testid={`invoice-due-${inv.id}`}
                >
                  {inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : '—'}
                </TableCell>
                <TableCell align="right">
                  <div onClick={(e: MouseEvent) => e.stopPropagation()}>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="sm"
                          data-testid={`invoice-row-actions-${inv.id}`}
                          aria-label={`Actions for ${inv.number}`}
                        >
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {canDocs && (
                          <DropdownMenuItem
                            data-testid={`invoice-download-pdf-${inv.id}`}
                            onClick={() => onDownloadPdf(inv)}
                          >
                            Download PDF
                          </DropdownMenuItem>
                        )}
                        {canDocs && (
                          <DropdownMenuItem
                            data-testid={`invoice-email-${inv.id}`}
                            onClick={() => onEmail(inv)}
                          >
                            Email invoice
                          </DropdownMenuItem>
                        )}
                        {canPay && balance > 0 && (
                          <DropdownMenuItem
                            data-testid={`invoice-log-payment-${inv.id}`}
                            onClick={() => onLogPayment(inv)}
                          >
                            Log Payment
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function CreateInvoiceModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [salesOrderId, setSalesOrderId] = useState('');
  const [salesOrderLabel, setSalesOrderLabel] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post(`/api/v1/invoices/from-sales-order/${salesOrderId}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setSalesOrderId('');
      setSalesOrderLabel('');
      onClose();
    },
    onError: () => setError('Could not create the invoice from that order.'),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Create invoice"
      description="Search shipped sales orders to bill"
    >
      <form
        data-testid="create-invoice-form"
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <ShippedSalesOrderCombobox
          value={salesOrderId}
          selectedLabel={salesOrderLabel}
          onSelect={(order: SalesOrder) => {
            setSalesOrderId(order.id);
            setSalesOrderLabel(`${order.number} — ${order.customerName}`);
          }}
        />

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
  const canDocs = hasRole('OWNER', 'ADMIN');
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [payInvoice, setPayInvoice] = useState<Invoice | null>(null);
  const [payAmount, setPayAmount] = useState('');

  const table = useServerTableQuery<Invoice>({
    queryKey: 'invoices',
    path: '/api/v1/invoices',
    defaultSort: 'createdAt,desc',
    extraParams: { status: statusFilter || undefined },
    fetcher: listInvoices,
  });
  const { items, isLoading, isError, error, refetch, search } = table;

  const selectedCount = selected.size;
  const selectedOnPage = useMemo(
    () => items.filter((inv) => selected.has(inv.id)).length,
    [items, selected],
  );

  const emailMutation = useMutation({
    mutationFn: async (invoiceId: string) =>
      (await apiClient.post<{ sent: boolean; to: string }>(
        `/api/v1/documents/invoice/${invoiceId}/email`,
      )).data,
    onSuccess: (result) => {
      toast(`Invoice emailed to ${result.to}`, { tone: 'success' });
    },
    onError: (err: Error) => {
      toast(err.message || 'Could not email invoice', { tone: 'danger' });
    },
  });

  const bulkEmailMutation = useMutation({
    mutationFn: async (invoiceIds: string[]) =>
      (await apiClient.post<{ sent: number; failed: number }>(
        '/api/v1/documents/invoices/email',
        { invoiceIds },
      )).data,
    onSuccess: (result) => {
      toast(`Emailed ${result.sent} invoice${result.sent === 1 ? '' : 's'}${result.failed ? ` (${result.failed} failed)` : ''}.`, {
        tone: result.failed ? 'danger' : 'success',
      });
      setSelected(new Set());
    },
    onError: (err: Error) => {
      toast(err.message || 'Could not email invoices', { tone: 'danger' });
    },
  });

  const payMutation = useMutation({
    mutationFn: async ({ id, amount }: { id: string; amount: number }) =>
      apiClient.post(`/api/v1/invoices/${id}/payments`, { amount }),
    onSuccess: () => {
      toast('Payment logged on the ledger.', { tone: 'success' });
      setPayInvoice(null);
      setPayAmount('');
      void queryClient.invalidateQueries({ queryKey: ['invoices'] });
    },
    onError: (err: Error) => {
      toast(err.message || 'Could not log this payment.', { tone: 'danger' });
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
    } catch (err) {
      toast((err as Error).message || 'Could not download PDF', { tone: 'danger' });
    }
  };

  const invoicePresets = [
    { id: 'all', label: 'All', filters: {} as Record<string, string> },
    { id: 'open', label: 'Open', filters: { status: 'OPEN' } },
    { id: 'paid', label: 'Paid', filters: { status: 'PAID' } },
    { id: 'partial', label: 'Partial', filters: { status: 'PARTIALLY_PAID' } },
  ];

  return (
    <TableDensityScope gridId="invoices">
    <div className="p-6" data-testid="invoices-page">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-text">Invoices</h1>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)} data-testid="new-invoice">
            <Plus className="h-4 w-4" />
            New invoice
          </Button>
        )}
      </div>

      <DataListToolbar gridId="invoices">
        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <DebouncedSearchInput
            value={search}
            onDebouncedChange={(value) => {
              table.setSearch(value);
              setSelected(new Set());
            }}
            placeholder="Search invoices or customers…"
          />
          <SavedFilterViews
            className="mb-0"
            storageKey="invoices-filters"
            activeFilters={{ status: statusFilter }}
            onApply={(f) => {
              setStatusFilter(f.status ?? '');
              table.setPage(1);
              setSelected(new Set());
            }}
            defaultPresets={invoicePresets}
          />
        </div>
      </DataListToolbar>

      {selectedCount > 0 && canDocs ? (
        <div
          className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-surface-raised px-3 py-2 shadow-sm"
          data-testid="invoice-bulk-bar"
        >
          <p className="text-sm text-text">
            {selectedCount} selected{selectedOnPage !== selectedCount ? ` (${selectedOnPage} on this page)` : ''}
          </p>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              data-testid="invoice-bulk-email"
              loading={bulkEmailMutation.isPending}
              onClick={() => bulkEmailMutation.mutate([...selected])}
            >
              <Mail className="h-4 w-4" />
              Email Invoices
            </Button>
            <Button variant="ghost" onClick={() => setSelected(new Set())}>
              Clear
            </Button>
          </div>
        </div>
      ) : null}

      <ListPageState
        isLoading={isLoading && items.length === 0}
        isError={isError}
        error={error}
        data={items}
        refetch={refetch}
        emptyIcon={FileText}
        emptyTitle={search ? 'No matching invoices' : 'No invoices yet'}
        emptyDescription=" "
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
            <InvoicesTable
              items={rows}
              selected={selected}
              onToggle={(id) => {
                setSelected((prev) => {
                  const next = new Set(prev);
                  if (next.has(id)) next.delete(id);
                  else next.add(id);
                  return next;
                });
              }}
              onToggleAll={(ids, checked) => {
                setSelected((prev) => {
                  const next = new Set(prev);
                  for (const id of ids) {
                    if (checked) next.add(id);
                    else next.delete(id);
                  }
                  return next;
                });
              }}
              onOpen={(id) => navigate(`/invoices/${id}`)}
              onLogPayment={(invoice) => {
                setPayInvoice(invoice);
                setPayAmount(String(invoiceBalanceDue(invoice)));
              }}
              onDownloadPdf={(invoice) => void downloadPdf(invoice.id, invoice.number)}
              onEmail={(invoice) => emailMutation.mutate(invoice.id)}
            />
            <Pagination
              page={table.page}
              totalPages={table.totalPages}
              totalElements={table.totalElements}
              size={table.size}
              onPageChange={(page) => {
                table.setPage(page);
                setSelected(new Set());
              }}
              onSizeChange={table.setSize}
            />
          </>
        )}
      </ListPageState>

      <CreateInvoiceModal open={modalOpen} onClose={() => setModalOpen(false)} />

      <Modal
        open={!!payInvoice}
        onClose={() => setPayInvoice(null)}
        title="Log payment"
        description={payInvoice ? payInvoice.number : undefined}
      >
        <form
          data-testid="log-payment-form"
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (!payInvoice) return;
            payMutation.mutate({ id: payInvoice.id, amount: Number(payAmount) });
          }}
        >
          <Input
            label="Amount"
            type="number"
            min="0.01"
            step="0.01"
            value={payAmount}
            onChange={(e) => setPayAmount(e.target.value)}
            required
            data-testid="log-payment-amount"
          />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setPayInvoice(null)}>
              Cancel
            </Button>
            <Button type="submit" loading={payMutation.isPending} data-testid="log-payment-submit">
              Log Payment
            </Button>
          </div>
        </form>
      </Modal>
    </div>
    </TableDensityScope>
  );
}
