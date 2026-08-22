import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { InvoiceDetail } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
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
  const [confirm, setConfirm] = useState<'issue' | 'void' | 'factor' | 'pay' | 'partial' | null>(null);
  const [paymentAmount, setPaymentAmount] = useState('');
  const [partialLineId, setPartialLineId] = useState<string | null>(null);
  const [partialQty, setPartialQty] = useState('1');

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

  const factorMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/invoices/${id}/factor`),
    onSuccess: async () => {
      await invalidate();
      toast('Invoice marked as factored.', { tone: 'success' });
    },
    onError: () => toast('Could not factor this invoice. Check fintech eligibility.', { tone: 'danger' }),
  });

  const payMutation = useMutation({
    mutationFn: async (amount: number) => apiClient.post(`/api/v1/invoices/${id}/payments`, { amount }),
    onSuccess: async () => {
      await invalidate();
      toast('Payment logged on the ledger.', { tone: 'success' });
    },
    onError: () => toast('Could not log this payment.', { tone: 'danger' }),
  });

  const partialCreditMutation = useMutation({
    mutationFn: async ({ lineId, qty }: { lineId: string; qty: number }) =>
      apiClient.post(`/api/v1/invoices/${id}/credit-memo`, { lines: [{ lineId, qty }] }),
    onSuccess: async () => {
      await invalidate();
      toast('Partial credit memo posted. The original invoice stays in history.', { tone: 'success' });
    },
    onError: () => toast('Could not issue a partial credit memo.', { tone: 'danger' }),
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
              <RequireRole roles={['OWNER', 'ADMIN', 'FINANCE_ADMIN']}>
                <Button
                  variant="secondary"
                  data-testid="mark-factored"
                  onClick={() => setConfirm('factor')}
                  loading={factorMutation.isPending}
                  disabled={!!invoice.factoringStatus && invoice.factoringStatus !== 'ELIGIBLE'}
                >
                  {invoice.factoringStatus === 'FUNDED' ? 'Factored' : 'Mark as Factored'}
                </Button>
              </RequireRole>
            ) : null}
            {issued ? (
              <RequireRole roles={['OWNER', 'ADMIN', 'FINANCE_ADMIN']}>
                <Button
                  variant="secondary"
                  data-testid="log-payment"
                  onClick={() => {
                    setPaymentAmount(String(invoice.total));
                    setConfirm('pay');
                  }}
                  loading={payMutation.isPending}
                >
                  Log Payment
                </Button>
              </RequireRole>
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

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Description</TableHead>
              <TableHead>Kind</TableHead>
              <TableHead align="right">Qty</TableHead>
              <TableHead align="right">Price</TableHead>
              <TableHead align="right">Amount</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-text-muted">
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
                  <TableCell>
                    {issued && line.kind !== 'TAX' && line.kind !== 'SURCHARGE' && line.kind !== 'CREDIT' ? (
                      <RequireRole roles={['FINANCE_ADMIN', 'WAREHOUSE_MANAGER', 'ADMIN']}>
                        <Button
                          size="sm"
                          variant="secondary"
                          data-testid={`partial-credit-${line.id}`}
                          onClick={() => {
                            setPartialLineId(line.id);
                            setPartialQty(String(line.qty));
                            setConfirm('partial');
                          }}
                        >
                          Issue Partial Credit Memo
                        </Button>
                      </RequireRole>
                    ) : (
                      <span className="text-xs text-text-muted">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
            <TableRow>
              <TableCell colSpan={5} className="text-right text-text-muted">
                Tax
              </TableCell>
              <TableCell align="right" className="font-mono">
                {formatCurrency(Number(invoice.tax ?? 0), invoice.currency)}
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell colSpan={5} className="text-right font-medium">
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
      {confirm === 'factor' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Mark this invoice as factored?"
          description="weGrowStock advances cash against this open invoice. Customer remittance still settles the original document."
          confirmLabel="Mark as Factored"
          confirming={factorMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            factorMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'pay' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Log a payment?"
          description="This records cash against the issued invoice. Partial amounts leave the document PARTIALLY PAID."
          confirmLabel="Log Payment"
          confirming={payMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            payMutation.mutate(Number(paymentAmount));
          }}
        >
          <div className="mb-4">
            <Input
              label="Amount"
              type="number"
              min="0.01"
              step="0.01"
              value={paymentAmount}
              onChange={(e) => setPaymentAmount(e.target.value)}
              data-testid="log-payment-amount"
            />
          </div>
        </AlertDialog>
      ) : null}
      {confirm === 'partial' && partialLineId ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Issue a partial credit memo?"
          description="Credit only the returned quantity. Do not void the whole invoice."
          confirmLabel="Issue Partial Credit Memo"
          confirming={partialCreditMutation.isPending}
          onConfirm={() => {
            const lineId = partialLineId;
            setConfirm(null);
            partialCreditMutation.mutate({ lineId, qty: Number(partialQty) });
          }}
        >
          <div className="mb-4">
            <Input
              label="Qty to credit"
              type="number"
              min="0.001"
              value={partialQty}
              onChange={(e) => setPartialQty(e.target.value)}
              data-testid="partial-credit-qty"
            />
          </div>
        </AlertDialog>
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
