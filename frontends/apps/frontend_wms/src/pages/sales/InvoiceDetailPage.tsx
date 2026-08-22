import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Lock } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { InvoiceDetail } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import { InlineEditableCell } from '@/components/ui/InlineEditableCell';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useToast } from '@/components/ui/Toast';
import { cn, formatCurrency } from '@/lib/utils';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  OPEN: 'bg-accent-muted text-accent',
  ISSUED: 'bg-accent-muted text-accent',
  PARTIALLY_PAID: 'bg-warning/10 text-warning',
  PAID: 'bg-success/10 text-success',
  VOID: 'bg-danger/10 text-danger',
  CREDIT_MEMO: 'bg-danger/10 text-danger',
};

const ISSUED = new Set(['OPEN', 'ISSUED', 'PARTIALLY_PAID']);

export function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [confirm, setConfirm] = useState<'issue' | 'void' | null>(null);

  const invoiceQuery = useQuery({
    queryKey: ['invoices', id],
    queryFn: async () => (await apiClient.get<InvoiceDetail>(`/api/v1/invoices/${id}`)).data,
    enabled: !!id,
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['invoices'] });
  };

  const issueMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/invoices/${id}/issue`),
    onSuccess: async () => {
      await invalidate();
      toast('Invoice issued. The PDF is archived and lines are locked.', { tone: 'success' });
    },
    onError: () => toast('Could not issue this invoice.', { tone: 'danger' }),
  });

  const voidMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/invoices/${id}/void`),
    onSuccess: async () => {
      await invalidate();
      toast('Invoice voided. A reversing credit memo was posted to the ledger.', { tone: 'success' });
    },
    onError: () => toast('Could not void this invoice.', { tone: 'danger' }),
  });

  const updateLineMutation = useMutation({
    mutationFn: async ({ lineId, qty, unitPrice }: { lineId: string; qty?: number; unitPrice?: number }) =>
      apiClient.patch(`/api/v1/invoices/${id}/lines/${lineId}`, { qty, unitPrice }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: () => toast('Issued invoices cannot be edited. Void and issue a credit memo.', { tone: 'danger' }),
  });

  const invoice = invoiceQuery.data;
  const draft = invoice?.status === 'DRAFT';
  const issued = !!invoice && ISSUED.has(invoice.status);
  const lines = invoice?.lines ?? [];

  if (invoiceQuery.isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted" data-testid="invoice-workspace-loading">
        Loading invoice…
      </div>
    );
  }

  if (invoiceQuery.isError || !invoice) {
    return (
      <div className="space-y-4 p-6" data-testid="invoice-workspace-error">
        <p className="text-sm text-danger">This invoice could not be loaded.</p>
        <Button variant="secondary" onClick={() => navigate('/invoices')}>
          Back to invoices
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="invoice-workspace" data-locked={draft ? 'false' : 'true'}>
      <header className="shrink-0 border-b border-border/60 px-6 py-4">
        <Link
          to="/invoices"
          className="inline-flex items-center gap-1.5 text-sm text-text-muted transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Invoices
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold text-text" data-testid="invoice-workspace-title">
                {invoice.number}
              </h1>
              <span
                className={cn(
                  'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                  STATUS_STYLES[invoice.status] ?? 'bg-surface-overlay text-text-muted',
                )}
                data-testid="invoice-workspace-status"
              >
                {invoice.status.replaceAll('_', ' ')}
              </span>
            </div>
            <p className="mt-1 text-sm text-text-muted">
              {invoice.customerName} · {formatCurrency(invoice.total, invoice.currency)}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {draft ? (
              <Button data-testid="issue-invoice" onClick={() => setConfirm('issue')} loading={issueMutation.isPending}>
                Issue Invoice
              </Button>
            ) : null}
            {issued ? (
              <RequireRole roles={['FINANCE_ADMIN', 'WAREHOUSE_MANAGER', 'ADMIN']}>
                <Button
                  variant="danger"
                  data-testid="void-credit-memo"
                  onClick={() => setConfirm('void')}
                  loading={voidMutation.isPending}
                >
                  Void & Issue Credit Memo
                </Button>
              </RequireRole>
            ) : null}
          </div>
        </div>
      </header>

      {!draft ? (
        <div
          className="mx-6 mt-4 flex items-start gap-3 rounded-lg border border-border bg-surface-overlay/60 px-4 py-3"
          data-testid="invoice-workspace-lock"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-text-muted" aria-hidden />
          <p className="text-sm text-text">
            Issued invoices are immutable in weGrowStock. Deleting one is forbidden — void it to post a reversing
            credit memo.
          </p>
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Description</TableHead>
              <TableHead>Kind</TableHead>
              <TableHead align="right">Qty</TableHead>
              <TableHead align="right">Price</TableHead>
              <TableHead align="right">Amount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-text-muted">
                  No billed lines on this invoice.
                </TableCell>
              </TableRow>
            ) : (
              lines.map((line) => (
                <TableRow key={line.id} data-testid="invoice-workspace-line">
                  <TableCell>{line.description}</TableCell>
                  <TableCell className="text-text-muted">{line.kind ?? 'ITEM'}</TableCell>
                  <TableCell align="right">
                    {draft && line.kind !== 'TAX' && line.kind !== 'SURCHARGE' ? (
                      <InlineEditableCell
                        testId={`invoice-line-qty-${line.id}`}
                        value={line.qty}
                        inputType="number"
                        onSave={async (value) => {
                          await updateLineMutation.mutateAsync({ lineId: line.id, qty: Number(value) });
                        }}
                      />
                    ) : (
                      <span className="font-mono tabular-nums">{line.qty}</span>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {draft && line.kind !== 'TAX' && line.kind !== 'SURCHARGE' ? (
                      <InlineEditableCell
                        testId={`invoice-line-price-${line.id}`}
                        value={line.unitPrice}
                        inputType="number"
                        formatDisplay={(value) => formatCurrency(Number(value), invoice.currency)}
                        onSave={async (value) => {
                          await updateLineMutation.mutateAsync({ lineId: line.id, unitPrice: Number(value) });
                        }}
                      />
                    ) : (
                      <span className="font-mono tabular-nums">
                        {formatCurrency(Number(line.unitPrice), invoice.currency)}
                      </span>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <span className="font-mono tabular-nums">{formatCurrency(Number(line.amount), invoice.currency)}</span>
                  </TableCell>
                </TableRow>
              ))
            )}
            <TableRow>
              <TableCell colSpan={4} className="text-right text-text-muted">
                Tax
              </TableCell>
              <TableCell align="right" className="font-mono">
                {formatCurrency(Number(invoice.tax ?? 0), invoice.currency)}
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell colSpan={4} className="text-right font-medium">
                Total
              </TableCell>
              <TableCell align="right" className="font-mono font-semibold" data-testid="invoice-workspace-total">
                {formatCurrency(invoice.total, invoice.currency)}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>

      {confirm === 'issue' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Issue invoice?"
          description="This locks the document and archives the PDF. weGrowStock will not allow silent edits after issue."
          confirmLabel="Issue Invoice"
          confirming={issueMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            issueMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'void' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Void and issue a credit memo?"
          description="The original invoice stays in history. weGrowStock posts a reversing ledger entry — it never deletes an issued invoice."
          confirmLabel="Void & Issue Credit Memo"
          confirming={voidMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            voidMutation.mutate();
          }}
        />
      ) : null}
    </div>
  );
}
