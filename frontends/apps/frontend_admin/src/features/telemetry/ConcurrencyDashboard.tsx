import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PageSkeleton, useToast } from '@invsys/shared-ui';
import { PageHeader } from '@/features/layout/PageHeader';
import {
  fetchTenantTelemetry,
  patchTenantThrottle,
  putTenantRateLimit,
  type TenantTelemetry,
} from './api';

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
      className="admin-card space-y-4 p-5"
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

function TrafficControllerRow({
  row,
  saving,
  onSave,
}: {
  row: TenantTelemetry;
  saving: boolean;
  onSave: (payload: { customRateLimit: number; isThrottled: boolean }) => void;
}) {
  const currentRps = row.customRateLimit && row.customRateLimit > 0 ? row.customRateLimit : 100;
  const [rps, setRps] = useState(currentRps);
  const [paused, setPaused] = useState(Boolean(row.isThrottled));

  useEffect(() => {
    setRps(row.customRateLimit && row.customRateLimit > 0 ? row.customRateLimit : 100);
    setPaused(Boolean(row.isThrottled));
  }, [row.customRateLimit, row.isThrottled]);

  const dirty = rps !== currentRps || paused !== Boolean(row.isThrottled);

  return (
    <li
      className="flex flex-col gap-3 border-b border-border py-4 last:border-b-0 sm:flex-row sm:items-center sm:justify-between"
      data-testid={`traffic-row-${row.slug}`}
    >
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-text">{row.slug}</p>
        <p className="text-xs text-text-muted">{row.status}</p>
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-3 sm:max-w-xl">
        <label className="block space-y-1 text-sm">
          <span className="flex justify-between text-text-muted">
            <span>API rate limit (RPS)</span>
            <span className="font-mono text-text">{rps}</span>
          </span>
          <input
            type="range"
            min={1}
            max={500}
            step={1}
            value={rps}
            onChange={(e) => setRps(Number(e.target.value))}
            className="w-full accent-accent"
            aria-label={`API rate limit for ${row.slug}`}
            data-testid={`rps-slider-${row.slug}`}
          />
        </label>
        <label className="inline-flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="admin-switch"
            checked={paused}
            onChange={(e) => setPaused(e.target.checked)}
            data-testid={`kill-switch-${row.slug}`}
          />
          <span className={paused ? 'font-medium text-danger' : 'text-text-muted'}>
            Kill Switch / Pause Traffic
          </span>
        </label>
        <button
          type="button"
          disabled={saving || !dirty}
          className="rounded border border-border px-3 py-1.5 text-sm font-medium hover:bg-surface disabled:opacity-50"
          onClick={() => onSave({ customRateLimit: rps, isThrottled: paused })}
        >
          Apply traffic policy
        </button>
      </div>
    </li>
  );
}

export function ConcurrencyDashboard() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');

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

  const throttleMutation = useMutation({
    mutationFn: ({
      tenantId,
      customRateLimit,
      isThrottled,
    }: {
      tenantId: string;
      customRateLimit: number;
      isThrottled: boolean;
    }) =>
      patchTenantThrottle(tenantId, {
        tenantId,
        customRateLimit,
        isThrottled,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData<TenantTelemetry[]>(
        ['control-plane', 'telemetry', 'tenants'],
        (prev) =>
          (prev ?? []).map((t) => (t.tenantId === updated.tenantId ? updated : t)),
      );
      toast.success(
        updated.isThrottled
          ? `Traffic paused for ${updated.slug}`
          : `Traffic policy updated for ${updated.slug}`,
      );
    },
    onError: () => {
      toast.danger('Could not update tenant throttle.');
    },
  });

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return data;
    return data.filter(
      (row) =>
        row.slug.toLowerCase().includes(q) ||
        row.tenantId.toLowerCase().includes(q) ||
        row.status.toLowerCase().includes(q),
    );
  }, [data, query]);

  if (isLoading) {
    return <PageSkeleton label="Loading concurrency telemetry…" />;
  }

  if (isError) {
    return <p className="text-sm text-danger">Failed to load tenant telemetry.</p>;
  }

  return (
    <div className="space-y-8" data-testid="concurrency-dashboard">
      <PageHeader
        title="Concurrency"
        description="Per-tenant latency snapshots, distributed rate-limit capacity, and live traffic control."
      />

      <section className="admin-card space-y-4 p-5" data-testid="live-traffic-controller">
        <div>
          <h3 className="text-sm font-semibold text-text">Live Tenant Traffic Controller</h3>
          <p className="mt-1 text-sm text-text-muted">
            Search active tenants, set API RPS, or pause traffic with the emergency kill switch.
          </p>
        </div>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search tenants"
          className="admin-field"
          aria-label="Search tenants"
          data-testid="traffic-search"
        />
        {filtered.length === 0 ? (
          <p className="text-sm text-text-muted">No matching tenants.</p>
        ) : (
          <ul>
            {filtered.map((row) => (
              <TrafficControllerRow
                key={row.tenantId}
                row={row}
                saving={throttleMutation.isPending}
                onSave={({ customRateLimit, isThrottled }) =>
                  throttleMutation.mutate({
                    tenantId: row.tenantId,
                    customRateLimit,
                    isThrottled,
                  })
                }
              />
            ))}
          </ul>
        )}
      </section>

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
