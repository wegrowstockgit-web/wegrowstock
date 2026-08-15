import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Card, CardHeader } from '@/components/ui/Card';
import { LaborVelocityLeaderboard } from '@/features/dashboard/LaborVelocityLeaderboard';

interface LaborAnalytics {
  unitsPerHour: number;
  directHours: number;
  indirectHours: number;
  directPercent: number;
  indirectPercent: number;
}

export function LaborAnalyticsDashboard({ mode = 'full' }: { mode?: 'full' | 'summary' }) {
  const { data, isLoading } = useQuery({
    queryKey: ['labor', 'analytics'],
    queryFn: async () => (await apiClient.get<LaborAnalytics>('/api/v1/labor/analytics')).data,
    retry: false,
  });

  return (
    <div className="space-y-4" data-testid="labor-analytics-dashboard">
      <Card>
        <CardHeader
          title="Punch-clock efficiency"
          description="UPH from direct activities · Direct vs Indirect hours"
        />
        {isLoading || !data ? (
          <p className="px-4 pb-4 text-sm text-text-muted">No punch-clock data yet.</p>
        ) : (
          <div className="grid grid-cols-2 gap-3 px-4 pb-4 sm:grid-cols-4">
            <Metric label="UPH" value={Number(data.unitsPerHour).toFixed(1)} testId="labor-uph" />
            <Metric
              label="Direct %"
              value={`${Number(data.directPercent).toFixed(0)}%`}
              testId="labor-direct-pct"
            />
            <Metric
              label="Indirect %"
              value={`${Number(data.indirectPercent).toFixed(0)}%`}
              testId="labor-indirect-pct"
            />
            <Metric
              label="Payroll hrs"
              value={(Number(data.directHours) + Number(data.indirectHours)).toFixed(2)}
              testId="labor-payroll-hours"
            />
          </div>
        )}
      </Card>
      {mode === 'full' && <LaborVelocityLeaderboard mode="summary" />}
    </div>
  );
}

function Metric({ label, value, testId }: { label: string; value: string; testId: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface p-3">
      <p className="text-xs text-text-muted">{label}</p>
      <p className="mt-1 text-xl font-bold text-text" data-testid={testId}>
        {value}
      </p>
    </div>
  );
}
