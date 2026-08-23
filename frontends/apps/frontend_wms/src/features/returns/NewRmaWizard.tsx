import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { unwrapPageItems } from '@/api/page';
import type { SalesOrder, SalesOrderDetail } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';

const RETURNABLE_STATUSES = ['SHIPPED', 'PARTIALLY_SHIPPED', 'CLOSED'];

const REASON_CODES = [
  'DEFECTIVE_PRODUCT',
  'WRONG_ITEM_SHIPPED',
  'DAMAGED_IN_TRANSIT',
  'BUYER_REMORSE',
  'SIZE_FIT_EXCHANGE',
] as const;

const RESOLUTIONS = [
  { value: 'REFUND_CREDIT_MEMO', label: 'Refund / Credit Memo' },
  { value: 'REPLACEMENT_ORDER', label: 'Replacement Order' },
  { value: 'REPAIR', label: 'Repair' },
] as const;

type Step = 1 | 2 | 3;

export function NewRmaWizard({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [step, setStep] = useState<Step>(1);
  const [search, setSearch] = useState('');
  const [salesOrderId, setSalesOrderId] = useState('');
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [resolutionType, setResolutionType] = useState('REFUND_CREDIT_MEMO');
  const [generateLabel, setGenerateLabel] = useState(false);
  const [error, setError] = useState('');

  const { data: orders = [], isFetching: searching } = useQuery({
    queryKey: ['sales-orders', 'rma-search', search],
    queryFn: async () =>
      unwrapPageItems<SalesOrder>(
        (
          await apiClient.get('/api/v1/sales-orders', {
            params: { page: 1, size: 25, search: search.trim() || undefined },
          })
        ).data,
      ),
    enabled: open,
  });

  const returnable = orders.filter((o) => RETURNABLE_STATUSES.includes(o.status));

  const { data: orderDetail } = useQuery({
    queryKey: ['sales-orders', salesOrderId],
    queryFn: async () =>
      (await apiClient.get<SalesOrderDetail>(`/api/v1/sales-orders/${salesOrderId}`)).data,
    enabled: open && !!salesOrderId,
  });

  const reset = () => {
    setStep(1);
    setSearch('');
    setSalesOrderId('');
    setSelected({});
    setQuantities({});
    setReasons({});
    setResolutionType('REFUND_CREDIT_MEMO');
    setGenerateLabel(false);
    setError('');
  };

  const mutation = useMutation({
    mutationFn: async () => {
      if (!orderDetail) return;
      const lines = orderDetail.lines
        .filter((line) => selected[line.id] && Number(quantities[line.id] ?? 0) > 0)
        .map((line) => ({
          salesOrderLineId: line.id,
          quantityExpected: Number(quantities[line.id]),
          reasonCode: reasons[line.id],
        }));
      await apiClient.post('/api/v1/returns', {
        salesOrderId,
        lines,
        resolutionType,
        generateLabel,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['returns'] });
      await queryClient.refetchQueries({ queryKey: ['returns', 'infinite'] });
      reset();
      onClose();
    },
    onError: () => setError('Could not create the RMA. Check quantities vs shipped amounts.'),
  });

  const shippedLines = (orderDetail?.lines ?? []).filter((line) => Number(line.qtyShipped) > 0);
  const selectedCount = shippedLines.filter((line) => selected[line.id]).length;
  const step2Ready =
    selectedCount > 0 &&
    shippedLines
      .filter((line) => selected[line.id])
      .every((line) => Number(quantities[line.id] ?? 0) > 0 && !!reasons[line.id]);

  return (
    <Modal
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title="New RMA"
      description={
        step === 1
          ? 'Step 1 of 3 — search a delivered sales order'
          : step === 2
            ? 'Step 2 of 3 — select returned items and reason codes'
            : 'Step 3 of 3 — resolution and inbound shipping'
      }
      size="xl"
    >
      <div className="space-y-4" data-testid="rma-wizard">
        <ol className="flex gap-2 text-xs font-medium uppercase tracking-wide text-text-muted">
          {['Order & Customer', 'Items & Reasons', 'Resolution'].map((label, idx) => (
            <li
              key={label}
              className={step === idx + 1 ? 'text-accent' : step > idx + 1 ? 'text-text' : ''}
            >
              {idx + 1}. {label}
            </li>
          ))}
        </ol>

        {step === 1 && (
          <div className="space-y-3">
            <Input
              label="Search"
              data-testid="rma-wizard-search"
              placeholder="Customer name, sales order #, or customer PO #"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="max-h-72 overflow-auto rounded-md border border-border">
              {searching && (
                <p className="p-3 text-sm text-text-muted">Searching delivered orders…</p>
              )}
              {!searching && returnable.length === 0 && (
                <p className="p-3 text-sm text-text-muted">
                  No shipped, partially shipped, or closed orders match.
                </p>
              )}
              {returnable.map((order) => (
                <button
                  key={order.id}
                  type="button"
                  data-testid={`rma-wizard-order-${order.number}`}
                  className="flex w-full items-center justify-between border-b border-border px-3 py-2 text-left last:border-b-0 hover:bg-surface-overlay"
                  onClick={() => {
                    setSalesOrderId(order.id);
                    setSelected({});
                    setQuantities({});
                    setReasons({});
                    setError('');
                    setStep(2);
                  }}
                >
                  <span className="font-mono text-sm font-semibold">{order.number}</span>
                  <span className="text-sm text-text-muted">
                    {order.customerName}
                    {order.customerPoNumber ? ` · PO ${order.customerPoNumber}` : ''}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}

        {step === 2 && orderDetail && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Select</TableHead>
                <TableHead>SKU & Name</TableHead>
                <TableHead>Shipped</TableHead>
                <TableHead>Return Qty</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shippedLines.map((line) => {
                const sku = line.sku ?? line.variantId;
                const max = Number(line.qtyShipped);
                return (
                  <TableRow key={line.id}>
                    <TableCell>
                      <input
                        type="checkbox"
                        data-testid={`rma-line-check-${sku}`}
                        checked={!!selected[line.id]}
                        onChange={(e) => {
                          const checked = e.target.checked;
                          setSelected((prev) => ({ ...prev, [line.id]: checked }));
                          if (checked) {
                            setQuantities((prev) => ({
                              ...prev,
                              [line.id]: prev[line.id] || String(max),
                            }));
                          }
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <p className="font-mono text-sm">{sku}</p>
                      <p className="text-xs text-text-muted">{line.name ?? '—'}</p>
                    </TableCell>
                    <TableCell mono>{max}</TableCell>
                    <TableCell>
                      <Input
                        aria-label={`Return quantity ${sku}`}
                        data-testid={`rma-return-qty-${sku}`}
                        type="number"
                        min="0"
                        max={max}
                        className="w-24"
                        disabled={!selected[line.id]}
                        value={quantities[line.id] ?? ''}
                        onChange={(e) =>
                          setQuantities((prev) => ({ ...prev, [line.id]: e.target.value }))
                        }
                      />
                    </TableCell>
                    <TableCell>
                      <Select
                        data-testid={`rma-reason-${sku}`}
                        disabled={!selected[line.id]}
                        value={reasons[line.id] ?? ''}
                        onChange={(e) =>
                          setReasons((prev) => ({ ...prev, [line.id]: e.target.value }))
                        }
                      >
                        <option value="" disabled>
                          Reason…
                        </option>
                        {REASON_CODES.map((code) => (
                          <option key={code} value={code}>
                            {code.replaceAll('_', ' ')}
                          </option>
                        ))}
                      </Select>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <Select
              label="Resolution type"
              data-testid="rma-resolution"
              value={resolutionType}
              onChange={(e) => setResolutionType(e.target.value)}
            >
              {RESOLUTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
            <label className="flex items-center gap-2 text-sm text-text">
              <input
                type="checkbox"
                data-testid="rma-generate-label"
                checked={generateLabel}
                onChange={(e) => setGenerateLabel(e.target.checked)}
              />
              Generate EasyPost Return Label
            </label>
          </div>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              if (step === 1) {
                reset();
                onClose();
                return;
              }
              setStep((s) => (s === 3 ? 2 : 1));
            }}
          >
            {step === 1 ? 'Cancel' : 'Back'}
          </Button>
          {step === 2 && (
            <Button type="button" disabled={!step2Ready} onClick={() => setStep(3)}>
              Continue
            </Button>
          )}
          {step === 3 && (
            <Button
              type="button"
              data-testid="rma-wizard-submit"
              loading={mutation.isPending}
              onClick={() => {
                setError('');
                mutation.mutate();
              }}
            >
              Create RMA
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}
