import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { apiClient } from '@/api/client';
import type { FintechDashboard } from '@/api/types';
import { postIdempotent } from '@/lib/apiIdempotent';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { TableSkeleton } from '@/components/ui/Skeleton';

export function FinancingCockpit() {
  const queryClient = useQueryClient();
  const [drawAmount, setDrawAmount] = useState('5000');

  const { data: dashboard, refetch, isLoading } = useQuery({
    queryKey: ['fintech', 'dashboard'],
    queryFn: async () => (await apiClient.get<FintechDashboard>('/api/v1/fintech/dashboard')).data,
    retry: false,
  });

  const drawMutation = useMutation({
    mutationFn: async () => {
      await postIdempotent('/api/v1/fintech/drawdown', { amount: Number(drawAmount) });
    },
    onSuccess: () => {
      void refetch();
      void queryClient.invalidateQueries({ queryKey: ['fintech'] });
    },
  });

  const factorMutation = useMutation({
    mutationFn: async (invoiceId: string) => {
      await postIdempotent('/api/v1/fintech/factor', { invoiceId });
    },
    onSuccess: () => void refetch(),
  });

  if (isLoading || !dashboard) {
    return <TableSkeleton rows={4} />;
  }

  const available = dashboard.creditLine.creditLimit - dashboard.creditLine.outstandingBalance;
  const metrics = dashboard.underwriting;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Financing Cockpit"
          description="Credit utilization, underwriting signals, and instant factoring"
        />
        {metrics && (
          <div className="grid gap-3 border-b border-border p-4 sm:grid-cols-3 lg:grid-cols-5">
            <Metric label="GMV (30d)" value={`$${metrics.gmv30d.toLocaleString()}`} />
            <Metric label="DSO" value={`${metrics.dsoDays.toFixed(0)}d`} />
            <Metric label="Avg invoice age" value={`${metrics.avgInvoiceAgeDays.toFixed(0)}d`} />
            <Metric
              label="Payment velocity"
              value={`${metrics.paymentVelocityScore.toFixed(0)}/100`}
            />
            <Metric
              label="Factoring limit"
              value={`$${metrics.eligibleFactoringLimit.toLocaleString()}`}
            />
          </div>
        )}
        <div className="grid gap-4 p-4 sm:grid-cols-3">
          <Metric label="Credit limit" value={`$${dashboard.creditLine.creditLimit.toLocaleString()}`} />
          <Metric
            label="Outstanding"
            value={`$${dashboard.creditLine.outstandingBalance.toLocaleString()}`}
          />
          <Metric label="Utilization" value={`${dashboard.utilizationPercent.toFixed(1)}%`} />
        </div>
        <div className="border-t border-border p-4">
          <p className="mb-2 text-sm font-medium text-text">Capital drawdown</p>
          <div className="flex flex-wrap items-end gap-3">
            <Input
              label="Amount"
              type="number"
              value={drawAmount}
              onChange={(e) => setDrawAmount(e.target.value)}
              className="max-w-xs"
            />
            <p className="text-sm text-text-muted">Available: ${available.toLocaleString()}</p>
            <Button
              onClick={() => drawMutation.mutate()}
              loading={drawMutation.isPending}
              disabled={Number(drawAmount) <= 0 || Number(drawAmount) > available}
            >
              Draw capital
            </Button>
          </div>
          <input
            type="range"
            min={0}
            max={Math.max(available, 1)}
            value={Math.min(Number(drawAmount) || 0, available)}
            onChange={(e) => setDrawAmount(e.target.value)}
            className="mt-4 w-full max-w-md accent-accent"
            aria-label="Drawdown amount slider"
          />
        </div>
      </Card>

      <Card>
        <CardHeader title="Eligible factoring invoices" description="Advance up to 85% of open AR" />
        <div className="divide-y divide-border">
          {dashboard.eligibleInvoices.length === 0 ? (
            <p className="p-4 text-sm text-text-muted">No open invoices eligible for factoring.</p>
          ) : (
            dashboard.eligibleInvoices.map((inv) => (
              <div key={inv.invoiceId} className="flex items-center justify-between p-4">
                <div>
                  <p className="font-medium text-text">{inv.number}</p>
                  <p className="text-sm text-text-muted">
                    Total ${inv.total.toLocaleString()} · Advance ${inv.advanceAmount.toLocaleString()}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="secondary"
                  loading={factorMutation.isPending}
                  onClick={() => factorMutation.mutate(inv.invoiceId)}
                >
                  Factor now
                </Button>
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs text-text-muted">{label}</p>
      <p className="mt-1 text-lg font-bold text-text">{value}</p>
    </div>
  );
}
