import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PageSkeleton, useToast } from '@invsys/shared-ui';
import { fetchTenantTelemetry, putTenantRateLimit, type TenantTelemetry } from './api';

function LatencyBar({ label, value, max }: { label: string; value: number; max: number }) {
  const pct = Math.min(100, Math.round((value / Math.max(max, 1)) * 100));
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs text-text-muted">
        <span>{label}</span>
        <span>{value.toFixed(1)} ms</span>
      </div>
      <div className="h-2 overflow-hidden rounded bg-surface">
        <div className="h-full rounded bg-accent" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

function TenantCard({
  row,
  onSave,
  saving,
}: {
  row: TenantTelemetry;
  onSave: (multiplier: number) => void;
  saving: boolean;
}) {
  const [multiplier, setMultiplier] = useState(row.capacityMultiplier);

  useEffect(() => {
    setMultiplier(row.capacityMultiplier);
  }, [row.capacityMultiplier]);

  return (
    <article
      className="space-y-4 rounded-lg border border-border bg-surface-raised p-4"
      data-testid={`telemetry-card-${row.slug}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-text">{row.slug}</h3>
          <p className="text-xs text-text-muted">{row.status}</p>
        </div>
        <span className="rounded bg-surface px-2 py-0.5 text-xs text-text-muted">
          ×{row.capacityMultiplier.toFixed(2)}
        </span>
      </div>

      <div className="space-y-3">
        <LatencyBar label="p50" value={row.p50LatencyMs} max={100} />
        <LatencyBar label="p95" value={row.p95LatencyMs} max={200} />
      </div>

      <label className="block space-y-2 text-sm">
        <span className="flex justify-between text-text-muted">
          <span>Rate capacity multiplier</span>
          <span className="font-mono text-text">{multiplier.toFixed(2)}</span>
        </span>
        <input
          type="range"
          min={0.25}
          max={4}
          step={0.05}
          value={multiplier}
          onChange={(e) => setMultiplier(Number(e.target.value))}
          className="w-full accent-accent"
          data-testid={`rate-slider-${row.slug}`}
        />
      </label>

      <button
        type="button"
        disabled={saving || multiplier === row.capacityMultiplier}
        className="w-full rounded border border-border px-3 py-1.5 text-sm font-medium hover:bg-surface disabled:opacity-50"
        onClick={() => onSave(multiplier)}
      >
        Apply multiplier
      </button>
    </article>
  );
}

export function ConcurrencyDashboard() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'telemetry', 'tenants'],
    queryFn: fetchTenantTelemetry,
  });

  const rateMutation = useMutation({
    mutationFn: ({ tenantId, capacityMultiplier }: { tenantId: string; capacityMultiplier: number }) =>
      putTenantRateLimit(tenantId, capacityMultiplier),
    onSuccess: (updated) => {
      queryClient.setQueryData<TenantTelemetry[]>(
        ['control-plane', 'telemetry', 'tenants'],
        (prev) =>
          (prev ?? []).map((t) => (t.tenantId === updated.tenantId ? updated : t)),
      );
      toast.success(`Rate multiplier updated for ${updated.slug}`);
    },
    onError: () => {
      toast.danger('Could not update rate limit multiplier.');
    },
  });

  if (isLoading) {
    return <PageSkeleton label="Loading concurrency telemetry…" />;
  }

  if (isError) {
    return <p className="text-sm text-danger">Failed to load tenant telemetry.</p>;
  }

  return (
    <div className="space-y-6" data-testid="concurrency-dashboard">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Concurrency</h2>
        <p className="mt-1 text-sm text-text-muted">
          Per-tenant latency snapshots and distributed rate-limit capacity multipliers.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data.map((row) => (
          <TenantCard
            key={row.tenantId}
            row={row}
            saving={rateMutation.isPending}
            onSave={(capacityMultiplier) =>
              rateMutation.mutate({ tenantId: row.tenantId, capacityMultiplier })
            }
          />
        ))}
      </div>
    </div>
  );
}
