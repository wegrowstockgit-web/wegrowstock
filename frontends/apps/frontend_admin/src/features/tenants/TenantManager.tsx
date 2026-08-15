import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FlaskConical } from 'lucide-react';
import { TIER_LABELS, type ControlPlaneTenant } from '@invsys/shared-types';
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
import { cloneSandbox, fetchTenants } from './api';
import { TenantEntitlementsDrawer } from './TenantEntitlementsDrawer';

function TierBadge({ tier }: { tier: ControlPlaneTenant['tier'] }) {
  const tone =
    tier === 'ENTERPRISE'
      ? 'bg-emerald-500/15 text-emerald-300'
      : tier === 'INTERMEDIATE'
        ? 'bg-sky-500/15 text-sky-300'
        : 'bg-amber-500/15 text-amber-200';
  return (
    <span
      className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ${tone}`}
      data-testid={`tier-badge-${tier}`}
    >
      {TIER_LABELS[tier]}
    </span>
  );
}

export function TenantManager() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [selected, setSelected] = useState<ControlPlaneTenant | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const { data: tenants = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['control-plane', 'tenants'],
    queryFn: fetchTenants,
  });

  const sandboxMutation = useMutation({
    mutationFn: (tenantId: string) => cloneSandbox(tenantId),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'tenants'] });
      toast.success(`Sandbox ${res.sandboxSlug} ready · key …${res.apiKeyHint}`);
      window.prompt('Sandbox API key (copy now — shown once):', res.apiKey);
    },
    onError: () => {
      toast.danger('Could not provision sandbox environment.');
    },
  });

  const sorted = useMemo(
    () => [...tenants].sort((a, b) => a.name.localeCompare(b.name)),
    [tenants],
  );

  const openTenant = (tenant: ControlPlaneTenant) => {
    setSelected(tenant);
    setDrawerOpen(true);
  };

  return (
    <div className="space-y-6" data-testid="tenant-manager">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">Registered tenants</h2>
          <p className="mt-1 text-sm text-text-muted">
            Select a tenant to manage entitlements, impersonation, and sandbox provisioning.
          </p>
        </div>
        <button
          type="button"
          className="text-sm text-accent underline-offset-4 hover:underline"
          onClick={() => void refetch()}
        >
          Refresh
        </button>
      </div>

      {isLoading ? (
        <PageSkeleton label="Loading tenants…" />
      ) : isError ? (
        <p className="text-sm text-danger">Failed to load tenants.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Tier</TableHead>
              <TableHead>Modules</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((tenant) => (
              <TableRow
                key={tenant.tenantId}
                className="cursor-pointer hover:bg-surface/80"
                onClick={() => openTenant(tenant)}
                data-testid={`tenant-row-${tenant.slug}`}
              >
                <TableCell className="font-medium">{tenant.name}</TableCell>
                <TableCell className="text-text-muted">{tenant.slug}</TableCell>
                <TableCell>{tenant.status}</TableCell>
                <TableCell>
                  <TierBadge tier={tenant.tier} />
                </TableCell>
                <TableCell className="text-text-muted">{tenant.enabledModules.length}</TableCell>
                <TableCell className="text-right">
                  <button
                    type="button"
                    data-testid={`sandbox-btn-${tenant.slug}`}
                    className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs font-medium text-text hover:bg-surface"
                    disabled={sandboxMutation.isPending}
                    onClick={(e) => {
                      e.stopPropagation();
                      sandboxMutation.mutate(tenant.tenantId);
                    }}
                  >
                    <FlaskConical className="h-3.5 w-3.5" aria-hidden />
                    Sandbox
                  </button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <TenantEntitlementsDrawer
        tenant={selected}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      />
    </div>
  );
}
