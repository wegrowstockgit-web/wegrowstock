import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Download, PackageOpen } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PortalRmaEligibleLine, PortalRmaResponse } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { cn } from '@/lib/utils';

const REASONS = [
  { value: 'DAMAGED', label: 'Damaged in transit / defective' },
  { value: 'WRONG_ITEM', label: 'Wrong item received' },
  { value: 'NOT_AS_DESCRIBED', label: 'Not as described' },
  { value: 'CHANGED_MIND', label: 'Changed mind' },
  { value: 'OTHER', label: 'Other' },
] as const;

type Step = 'lines' | 'reason' | 'result';

interface ReturnsWizardProps {
  open: boolean;
  onClose: () => void;
  salesOrderId: string;
  salesOrderNumber: string;
}

export function ReturnsWizard({
  open,
  onClose,
  salesOrderId,
  salesOrderNumber,
}: ReturnsWizardProps) {
  const [step, setStep] = useState<Step>('lines');
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [mediaByLine, setMediaByLine] = useState<Record<string, string>>({});
  const [reasonCode, setReasonCode] = useState('');
  const [result, setResult] = useState<PortalRmaResponse | null>(null);
  const [error, setError] = useState('');

  const { data: eligible = [], isLoading } = useQuery({
    queryKey: ['showroom', 'returns', 'eligible', salesOrderId],
    queryFn: async () =>
      (
        await apiClient.get<PortalRmaEligibleLine[]>(
          `/api/v1/showroom/returns/eligible/${salesOrderId}`,
        )
      ).data,
    enabled: open && !!salesOrderId,
    retry: false,
  });

  useEffect(() => {
    if (!open) {
      setStep('lines');
      setQuantities({});
      setMediaByLine({});
      setReasonCode('');
      setResult(null);
      setError('');
    }
  }, [open]);

  // Poll while office review is outstanding so portal flips to APPROVED / ship-at-own-expense.
  const pendingReturnId =
    open && step === 'result' && result?.status === 'PENDING_REVIEW' ? result.id : null;
  useEffect(() => {
    if (!pendingReturnId) return;
    const timer = window.setInterval(() => {
      void apiClient
        .get<PortalRmaResponse>(`/api/v1/showroom/returns/${pendingReturnId}`)
        .then((res) => setResult(res.data))
        .catch(() => undefined);
    }, 2_000);
    return () => window.clearInterval(timer);
  }, [pendingReturnId]);

  const selectedLines = eligible.filter((line) => Number(quantities[line.salesOrderLineId] ?? 0) > 0);
  const damaged = reasonCode === 'DAMAGED';
  const damagedNeedsPhoto =
    damaged && selectedLines.some((line) => !mediaByLine[line.salesOrderLineId]);

  const submitMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<PortalRmaResponse>('/api/v1/showroom/returns', {
        salesOrderId,
        reasonCode,
        lines: selectedLines.map((line) => ({
          salesOrderLineId: line.salesOrderLineId,
          quantity: Number(quantities[line.salesOrderLineId]),
          mediaObjectId: mediaByLine[line.salesOrderLineId] || undefined,
        })),
      });
      return res.data;
    },
    onSuccess: (data) => {
      setResult(data);
      setStep('result');
      setError('');
    },
    onError: () =>
      setError('Could not submit the return. Check quantities, photos, and try again.'),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Return items"
      description={`RMA for order ${salesOrderNumber}`}
    >
      <div className="space-y-4" data-testid="returns-wizard">
        {step === 'lines' && (
          <>
            {isLoading && <p className="text-sm text-text-muted">Loading returnable lines…</p>}
            {!isLoading && eligible.length === 0 && (
              <p className="text-sm text-text-muted">No returnable shipped quantity on this order.</p>
            )}
            <div className="space-y-3">
              {eligible.map((line) => (
                <div
                  key={line.salesOrderLineId}
                  className="rounded-md border border-border bg-surface-raised p-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-mono text-sm font-medium text-text">{line.sku}</p>
                      <p className="text-sm text-text-muted">{line.name}</p>
                      <p className="mt-1 text-xs text-text-muted">
                        Returnable {Number(line.qtyReturnable)} ·{' '}
                        {Number(line.unitPrice).toLocaleString(undefined, {
                          style: 'currency',
                          currency: 'USD',
                        })}
                        {line.requiresReview ? ' · review required' : ''}
                      </p>
                    </div>
                    <Input
                      aria-label={`Qty ${line.sku}`}
                      type="number"
                      min="0"
                      max={Number(line.qtyReturnable)}
                      className="w-24"
                      value={quantities[line.salesOrderLineId] ?? ''}
                      onChange={(e) =>
                        setQuantities((prev) => ({
                          ...prev,
                          [line.salesOrderLineId]: e.target.value,
                        }))
                      }
                    />
                  </div>
                </div>
              ))}
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>
                Cancel
              </Button>
              <Button
                type="button"
                disabled={selectedLines.length === 0}
                onClick={() => setStep('reason')}
              >
                Continue
              </Button>
            </div>
          </>
        )}

        {step === 'reason' && (
          <>
            <Select
              label="Return reason"
              value={reasonCode}
              onChange={(e) => setReasonCode(e.target.value)}
              required
            >
              <option value="">Select reason…</option>
              {REASONS.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </Select>

            {damaged && (
              <div className="space-y-3">
                <p className="text-sm font-medium text-text">Damage evidence (required)</p>
                {selectedLines.map((line) => (
                  <div key={line.salesOrderLineId} className="rounded-md border border-border p-3">
                    <p className="mb-2 font-mono text-xs text-text-muted">{line.sku}</p>
                    <MediaPicker
                      kind="EVIDENCE"
                      label="Upload damage photo"
                      capture
                      onUploaded={(uploaded) => {
                        if (uploaded.id) {
                          setMediaByLine((prev) => ({
                            ...prev,
                            [line.salesOrderLineId]: uploaded.id,
                          }));
                        }
                      }}
                    />
                    {mediaByLine[line.salesOrderLineId] && (
                      <p className="mt-1 text-xs text-success">Photo attached</p>
                    )}
                  </div>
                ))}
              </div>
            )}

            {error && <p className="text-sm text-danger">{error}</p>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setStep('lines')}>
                Back
              </Button>
              <Button
                type="button"
                loading={submitMutation.isPending}
                disabled={!reasonCode || damagedNeedsPhoto}
                onClick={() => submitMutation.mutate()}
              >
                Submit return
              </Button>
            </div>
          </>
        )}

        {step === 'result' && result && (
          <>
            {result.status === 'APPROVED' || result.status === 'EXPECTED' ? (
              <div
                className="space-y-3 rounded-md border border-success/40 bg-surface p-4"
                data-testid="rma-approved-banner"
              >
                <div className="flex items-center gap-2 text-success">
                  <PackageOpen className="h-5 w-5" />
                  <p className="font-semibold">Return approved — {result.number}</p>
                </div>
                {result.labelPurchaseMode === 'CUSTOMER' ||
                (result.shippingInstruction &&
                  /own expense/i.test(result.shippingInstruction)) ? (
                  <p className="text-sm text-text" data-testid="rma-customer-ship-instruction">
                    {result.shippingInstruction ??
                      'Please ship this return at your own expense. A prepaid label was not purchased.'}
                  </p>
                ) : (
                  <p className="text-sm text-text-muted">
                    {result.shippingInstruction ??
                      'A prepaid return label was generated. Print it and ship the items back.'}
                  </p>
                )}
                {result.returnLabelUrl && (
                  <a
                    href={result.returnLabelUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex"
                  >
                    <Button type="button">
                      <Download className="h-4 w-4" />
                      Download Return Label
                    </Button>
                  </a>
                )}
              </div>
            ) : (
              <div
                className={cn(
                  'rounded-md border border-warning/50 bg-warning/10 p-4 text-sm text-text',
                )}
                data-testid="rma-pending-banner"
              >
                <p className="font-semibold">Return Request Submitted — Pending Office Approval</p>
                <p className="mt-1 text-text-muted">
                  {result.reviewReason ??
                    'Our team will review your request and email you when a decision is made.'}
                </p>
                <p className="mt-2 font-mono text-xs text-text-muted">{result.number}</p>
                {result.estimatedLabelCost != null && (
                  <p className="mt-2 text-xs text-text-muted">
                    Est. return postage{' '}
                    {Number(result.estimatedLabelCost).toLocaleString(undefined, {
                      style: 'currency',
                      currency: 'USD',
                    })}
                  </p>
                )}
              </div>
            )}
            <div className="flex justify-end">
              <Button type="button" onClick={onClose}>
                Done
              </Button>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
