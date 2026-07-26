import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Truck } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';

export interface RankedRate {
  carrier: string;
  service: string;
  rateId: string;
  rate: number;
  currency: string;
  transitDays: number;
  meetsSla: boolean;
  recommended: boolean;
}

export interface RateQuoteResponse {
  salesOrderId: string;
  cartonId: string;
  cartonName: string;
  billableWeightLb: number;
  volumetricWeightLb: number;
  rates: RankedRate[];
  recommended: RankedRate | null;
}

interface Props {
  salesOrderId: string;
  cartonId?: string;
  onLabelPurchased?: (trackingNumber?: string) => void;
}

export function RateShoppingWidget({ salesOrderId, cartonId, onLabelPurchased }: Props) {
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [quotes, setQuotes] = useState<RateQuoteResponse | null>(null);

  const shopMutation = useMutation({
    mutationFn: async () =>
      (
        await apiClient.post<RateQuoteResponse>('/api/v1/shipments/rate-shop', {
          salesOrderId,
          cartonId: cartonId || null,
        })
      ).data,
    onSuccess: (data) => {
      setQuotes(data);
      setOpen(true);
    },
    onError: () => toast('Rate shop failed — check address and carton dims', { tone: 'danger' }),
  });

  const buyMutation = useMutation({
    mutationFn: async () =>
      (
        await apiClient.post<{ trackingNumber?: string; labelRef?: string }>(
          '/api/v1/shipments/auto-buy-label',
          { salesOrderId, cartonId: cartonId || null },
        )
      ).data,
    onSuccess: (data) => {
      toast(`Label purchased${data.trackingNumber ? ` · ${data.trackingNumber}` : ''}`, {
        tone: 'success',
      });
      setOpen(false);
      onLabelPurchased?.(data.trackingNumber);
    },
    onError: () => toast('Could not purchase label', { tone: 'danger' }),
  });

  return (
    <div data-testid="rate-shopping-widget">
      <Button
        variant="secondary"
        data-testid="rate-shop-open"
        loading={shopMutation.isPending}
        disabled={!salesOrderId}
        onClick={() => shopMutation.mutate()}
      >
        <Truck className="mr-2 h-4 w-4" aria-hidden />
        Rate Shop & Compare
      </Button>

      {open && quotes && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="Carrier rate comparison"
          data-testid="rate-shop-modal"
        >
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-surface-raised p-5 shadow-xl">
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-text">Carrier rates</h2>
                <p className="text-sm text-text-muted">
                  {quotes.cartonName} · billable {Number(quotes.billableWeightLb).toFixed(2)} lb
                  (dim {Number(quotes.volumetricWeightLb).toFixed(2)} lb)
                </p>
              </div>
              <Button variant="ghost" onClick={() => setOpen(false)}>
                Close
              </Button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {quotes.rates.map((rate) => (
                <div
                  key={`${rate.carrier}-${rate.service}-${rate.rateId}`}
                  data-testid={`rate-card-${rate.carrier}-${rate.service}`}
                  className={cn(
                    'rounded-lg border p-3',
                    rate.recommended
                      ? 'border-accent bg-accent-muted'
                      : 'border-border bg-surface',
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-semibold text-text">{rate.carrier}</p>
                    {rate.recommended && (
                      <span className="rounded-full bg-accent px-2 py-0.5 text-xs font-medium text-white">
                        Recommended (Cheapest)
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm text-text-muted">{rate.service}</p>
                  <p className="mt-2 text-sm text-text">{rate.transitDays} transit day(s)</p>
                  <p className="mt-1 text-lg font-bold text-text">
                    {rate.currency} {Number(rate.rate).toFixed(2)}
                  </p>
                  {!rate.meetsSla && (
                    <p className="mt-1 text-xs text-warning">May miss requested ship date</p>
                  )}
                </div>
              ))}
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button
                data-testid="rate-shop-buy"
                loading={buyMutation.isPending}
                onClick={() => buyMutation.mutate()}
              >
                Purchase Label
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
