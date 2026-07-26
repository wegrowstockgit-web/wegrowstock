import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { TenantSettingsMap } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';

function ToggleRow({
  label,
  description,
  checked,
  onChange,
  testId,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (next: boolean) => void;
  testId: string;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-border/60 py-4 last:border-0">
      <div>
        <p className="font-medium text-text">{label}</p>
        <p className="mt-0.5 text-sm text-text-muted">{description}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        data-testid={testId}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative h-7 w-12 shrink-0 rounded-full transition-colors',
          checked ? 'bg-accent' : 'bg-surface-overlay',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition-transform',
            checked ? 'left-5' : 'left-0.5',
          )}
        />
      </button>
    </div>
  );
}

export function AutomationSettings() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [blindCycleCounts, setBlindCycleCounts] = useState(true);
  const [predictiveReplenishment, setPredictiveReplenishment] = useState(true);
  const [maxAutoAdjust, setMaxAutoAdjust] = useState('100.00');
  const [rmaAutoApprove, setRmaAutoApprove] = useState('100.00');

  const { data, isLoading } = useQuery({
    queryKey: ['settings'],
    queryFn: async () => (await apiClient.get<TenantSettingsMap>('/api/v1/settings')).data,
  });

  useEffect(() => {
    if (!data) return;
    setBlindCycleCounts(Boolean(data.blind_cycle_counts ?? true));
    setPredictiveReplenishment(Boolean(data.predictive_replenishment_enabled ?? true));
    setMaxAutoAdjust(String(data.max_auto_adjust_value ?? '100.00'));
    setRmaAutoApprove(String(data.rma_auto_approve_max_value ?? '100.00'));
  }, [data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch('/api/v1/settings', {
        blind_cycle_counts: blindCycleCounts,
        predictive_replenishment_enabled: predictiveReplenishment,
        max_auto_adjust_value: Number(maxAutoAdjust) || 0,
        rma_auto_approve_max_value: Number(rmaAutoApprove) || 0,
      });
      await apiClient.post('/api/v1/settings/cache/flush');
    },
    onSuccess: () => {
      toast('Automation settings saved', { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: () => toast('Could not save automation settings', { tone: 'danger' }),
  });

  return (
    <Card data-testid="automation-settings">
      <CardHeader
        title="Automations & Thresholds"
        description="Tenant-owned business automations. Platform maintenance jobs are not listed here."
      />
      {isLoading ? (
        <p className="px-4 pb-4 text-sm text-text-muted">Loading…</p>
      ) : (
        <div className="space-y-2 px-4 pb-4">
          <ToggleRow
            label="Blind Cycle Counts"
            description="Forces counters to enter quantities without seeing system targets."
            checked={blindCycleCounts}
            onChange={setBlindCycleCounts}
            testId="automation-blind-cycle-counts"
          />
          <ToggleRow
            label="Predictive Replenishment Generation"
            description="Auto-queues Reserve-to-Pick transfer triggers from demand forecasts."
            checked={predictiveReplenishment}
            onChange={setPredictiveReplenishment}
            testId="automation-predictive-replenishment"
          />
          <div className="grid gap-4 sm:grid-cols-2 pt-2">
            <Input
              label="Cycle Count Auto-Approve Threshold ($)"
              type="number"
              min={0}
              step="0.01"
              value={maxAutoAdjust}
              onChange={(e) => setMaxAutoAdjust(e.target.value)}
              data-testid="automation-max-auto-adjust"
            />
            <Input
              label="RMA Auto-Approve Threshold ($)"
              type="number"
              min={0}
              step="0.01"
              value={rmaAutoApprove}
              onChange={(e) => setRmaAutoApprove(e.target.value)}
              data-testid="automation-rma-auto-approve"
            />
          </div>
          <div className="flex justify-end pt-4">
            <Button
              data-testid="automation-settings-save"
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Save automations
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
