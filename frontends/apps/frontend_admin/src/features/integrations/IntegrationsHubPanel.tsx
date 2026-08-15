import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  PageSkeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useToast,
} from '@invsys/shared-ui';
import { fetchTenants } from '@/features/tenants/api';
import { fetchIntegrationTraffic, setIntegrationKillSwitch } from './api';

export function IntegrationsHubPanel() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [selectedTenantId, setSelectedTenantId] = useState('');
  const [reason, setReason] = useState('');

  const trafficQuery = useQuery({
    queryKey: ['control-plane', 'integrations', 'traffic'],
    queryFn: fetchIntegrationTraffic,
  });

  const tenantsQuery = useQuery({
    queryKey: ['control-plane', 'tenants'],
    queryFn: fetchTenants,
  });

  const slugById = useMemo(() => {
    const map = new Map<string, string>();
    for (const t of tenantsQuery.data ?? []) {
      map.set(t.tenantId, t.slug);
    }
    return map;
  }, [tenantsQuery.data]);

  const killSwitchMutation = useMutation({
    mutationFn: ({ paused }: { paused: boolean }) => {
      if (!selectedTenantId) throw new Error('Select a tenant');
      return setIntegrationKillSwitch(selectedTenantId, paused, reason || undefined);
    },
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'integrations', 'traffic'] });
      toast.success(
        res.paused
          ? `Sync paused for tenant ${slugById.get(res.tenantId) ?? res.tenantId}`
          : `Sync resumed for tenant ${slugById.get(res.tenantId) ?? res.tenantId}`,
      );
    },
    onError: () => {
      toast.danger('Could not update integration kill switch.');
    },
  });

  return (
    <div className="space-y-8" data-testid="integrations-hub">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Webhooks & integrations</h2>
        <p className="mt-1 text-sm text-text-muted">
          Outbox traffic over the last 24 hours and per-tenant sync kill switches.
        </p>
      </div>

      <section className="space-y-3 rounded-lg border border-border bg-surface-raised p-4">
        <h3 className="text-sm font-semibold text-text">Kill switch</h3>
        <div className="flex flex-wrap items-end gap-3">
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Tenant</span>
            <select
              className="rounded border border-border bg-surface px-3 py-2 text-sm text-text"
              value={selectedTenantId}
              onChange={(e) => setSelectedTenantId(e.target.value)}
              data-testid="kill-switch-tenant"
            >
              <option value="">Select…</option>
              {(tenantsQuery.data ?? []).map((t) => (
                <option key={t.tenantId} value={t.tenantId}>
                  {t.name} ({t.slug})
                </option>
              ))}
            </select>
          </label>
          <label className="block min-w-[200px] flex-1 text-sm">
            <span className="mb-1 block text-text-muted">Reason (optional)</span>
            <input
              className="w-full rounded border border-border bg-surface px-3 py-2 text-sm text-text"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Incident / maintenance"
              data-testid="kill-switch-reason"
            />
          </label>
          <button
            type="button"
            disabled={!selectedTenantId || killSwitchMutation.isPending}
            className="rounded border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm font-medium text-amber-200 disabled:opacity-50"
            onClick={() => killSwitchMutation.mutate({ paused: true })}
          >
            Pause sync
          </button>
          <button
            type="button"
            disabled={!selectedTenantId || killSwitchMutation.isPending}
            className="rounded border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm font-medium text-emerald-300 disabled:opacity-50"
            onClick={() => killSwitchMutation.mutate({ paused: false })}
          >
            Resume sync
          </button>
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-text">Traffic (24h)</h3>
        {trafficQuery.isLoading ? (
          <PageSkeleton label="Loading traffic…" />
        ) : trafficQuery.isError ? (
          <p className="text-sm text-danger">Failed to load integration traffic.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tenant</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Events</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(trafficQuery.data ?? []).length === 0 ? (
                <TableRow>
                  <TableCell colSpan={3} className="text-text-muted">
                    No outbox traffic in the last 24 hours.
                  </TableCell>
                </TableRow>
              ) : (
                (trafficQuery.data ?? []).map((row) => (
                  <TableRow key={`${row.tenantId}-${row.status}`}>
                    <TableCell className="font-medium">
                      {slugById.get(row.tenantId) ?? row.tenantId}
                    </TableCell>
                    <TableCell className="text-text-muted">{row.status}</TableCell>
                    <TableCell>{row.eventCount.toLocaleString()}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        )}
      </section>
    </div>
  );
}
