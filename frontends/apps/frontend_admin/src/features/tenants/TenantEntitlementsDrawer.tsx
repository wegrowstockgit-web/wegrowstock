import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FlaskConical, UserRoundSearch } from 'lucide-react';
import {
  APP_MODULES,
  COMMERCIAL_TIERS,
  MODULE_LABELS,
  TIER_LABELS,
  type AppModule,
  type CommercialTier,
  type ControlPlaneTenant,
} from '@invsys/shared-types';
import { SlideOutDrawer, useToast } from '@invsys/shared-ui';
import { fetchTierDefinitions } from '@/features/packaging/api';
import {
  buildWmsImpersonationUrl,
  cloneSandbox,
  impersonateTenant,
  patchTenantModules,
  patchTenantStatus,
  patchTenantTier,
} from './api';

type Props = {
  tenant: ControlPlaneTenant | null;
  open: boolean;
  onClose: () => void;
};

function applyTenantCache(
  queryClient: ReturnType<typeof useQueryClient>,
  updated: ControlPlaneTenant,
) {
  void queryClient.invalidateQueries({ queryKey: ['control-plane', 'tenants'] });
  queryClient.setQueryData<ControlPlaneTenant[]>(['control-plane', 'tenants'], (prev) =>
    (prev ?? []).map((t) => (t.tenantId === updated.tenantId ? updated : t)),
  );
}

export function TenantEntitlementsDrawer({ tenant, open, onClose }: Props) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [pending, setPending] = useState<AppModule | null>(null);
  const [sandboxKey, setSandboxKey] = useState<string | null>(null);

  const { data: tierDefinitions = [] } = useQuery({
    queryKey: ['control-plane', 'packaging', 'tiers'],
    queryFn: fetchTierDefinitions,
    enabled: open && !!tenant,
  });

  const modules = useMemo(() => new Set(tenant?.enabledModules ?? []), [tenant]);
  const includedInSelectedTier = useMemo(() => {
    const row = tierDefinitions.find((t) => t.tierCode === tenant?.tier);
    return new Set(row?.defaultModules ?? []);
  }, [tierDefinitions, tenant?.tier]);

  const modulesMutation = useMutation({
    mutationFn: (next: AppModule[]) => {
      if (!tenant) throw new Error('No tenant selected');
      return patchTenantModules(tenant.tenantId, next);
    },
    onSuccess: (updated) => {
      applyTenantCache(queryClient, updated);
      toast.success(`Modules updated for ${updated.name}`);
    },
    onError: () => {
      toast.danger('Could not save module entitlements.');
    },
    onSettled: () => setPending(null),
  });

  const tierMutation = useMutation({
    mutationFn: (tier: CommercialTier) => {
      if (!tenant) throw new Error('No tenant selected');
      return patchTenantTier(tenant.tenantId, tier);
    },
    onSuccess: (updated) => {
      applyTenantCache(queryClient, updated);
      toast.success(`Tier set to ${TIER_LABELS[updated.tier]} for ${updated.name}`);
    },
    onError: () => {
      toast.danger('Could not update commercial tier.');
    },
  });

  const statusMutation = useMutation({
    mutationFn: (status: 'ACTIVE' | 'SUSPENDED') => {
      if (!tenant) throw new Error('No tenant selected');
      return patchTenantStatus(tenant.tenantId, status);
    },
    onSuccess: (updated) => {
      if (!tenant) return;
      applyTenantCache(queryClient, { ...tenant, status: updated.status });
      toast.success(
        updated.status === 'SUSPENDED'
          ? `${updated.name} suspended`
          : `${updated.name} reactivated`,
      );
    },
    onError: () => {
      toast.danger('Could not update tenant status.');
    },
  });

  const impersonateMutation = useMutation({
    mutationFn: () => {
      if (!tenant) throw new Error('No tenant selected');
      return impersonateTenant(tenant.tenantId);
    },
    onSuccess: (res) => {
      const url = buildWmsImpersonationUrl(res.accessToken, res.loginUrl);
      window.open(url, '_blank', 'noopener,noreferrer');
      toast.success(`Impersonating as ${res.email}`);
    },
    onError: () => {
      toast.danger('Could not start impersonation session.');
    },
  });

  const sandboxMutation = useMutation({
    mutationFn: () => {
      if (!tenant) throw new Error('No tenant selected');
      return cloneSandbox(tenant.tenantId);
    },
    onSuccess: (res) => {
      setSandboxKey(res.apiKey);
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'tenants'] });
      toast.success(`Sandbox ready: ${res.sandboxSlug}`);
    },
    onError: () => {
      toast.danger('Could not provision sandbox environment.');
    },
  });

  const toggle = (module: AppModule, checked: boolean) => {
    if (!tenant || module === 'CORE') return;
    const next = new Set(modules);
    if (checked) next.add(module);
    else next.delete(module);
    next.add('CORE');
    setPending(module);
    modulesMutation.mutate([...APP_MODULES].filter((m) => next.has(m)));
  };

  const busy =
    modulesMutation.isPending ||
    tierMutation.isPending ||
    statusMutation.isPending ||
    impersonateMutation.isPending ||
    sandboxMutation.isPending;

  const suspended = tenant?.status?.toUpperCase() === 'SUSPENDED';

  return (
    <SlideOutDrawer
      open={open && !!tenant}
      onClose={onClose}
      title={tenant?.name ?? 'Tenant'}
      description={tenant ? `${tenant.slug} · ${tenant.tier} · ${tenant.status}` : undefined}
      width="lg"
    >
      {tenant ? (
        <div className="space-y-6" data-testid="tenant-entitlements-drawer">
          <section className="space-y-3">
            <h3 className="text-sm font-semibold text-text">Support actions</h3>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                data-testid="tenant-impersonate"
                disabled={busy || suspended}
                onClick={() => impersonateMutation.mutate()}
                className="inline-flex items-center gap-2 rounded border border-border px-3 py-1.5 text-sm font-medium text-text hover:bg-surface disabled:opacity-50"
              >
                <UserRoundSearch className="h-4 w-4" aria-hidden />
                Impersonate
              </button>
              <button
                type="button"
                data-testid="tenant-suspend-toggle"
                disabled={busy}
                onClick={() => statusMutation.mutate(suspended ? 'ACTIVE' : 'SUSPENDED')}
                className={
                  suspended
                    ? 'inline-flex items-center gap-2 rounded border border-emerald-500/40 bg-emerald-500/10 px-3 py-1.5 text-sm font-medium text-emerald-300 hover:bg-emerald-500/15 disabled:opacity-50'
                    : 'inline-flex items-center gap-2 rounded border border-amber-500/40 bg-amber-500/10 px-3 py-1.5 text-sm font-medium text-amber-200 hover:bg-amber-500/15 disabled:opacity-50'
                }
              >
                {suspended ? 'Reactivate' : 'Suspend'}
              </button>
              <button
                type="button"
                data-testid="tenant-clone-sandbox"
                disabled={busy}
                onClick={() => {
                  setSandboxKey(null);
                  sandboxMutation.mutate();
                }}
                className="inline-flex items-center gap-2 rounded border border-border px-3 py-1.5 text-sm font-medium text-text hover:bg-surface disabled:opacity-50"
              >
                <FlaskConical className="h-4 w-4" aria-hidden />
                Provision Sandbox
              </button>
            </div>
            {sandboxKey ? (
              <p
                className="break-all rounded border border-border bg-surface px-3 py-2 font-mono text-xs text-text"
                data-testid="sandbox-api-key"
              >
                Sandbox API key (copy now): {sandboxKey}
              </p>
            ) : null}
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold text-text">Commercial tier</h3>
            <p className="text-sm text-text-muted">
              Applying a tier loads its module bundle and keeps any custom add-ons already enabled
              outside that bundle.
            </p>
            <div className="flex flex-wrap gap-2" role="group" aria-label="Commercial tier">
              {COMMERCIAL_TIERS.map((tier) => {
                const active = tenant.tier === tier;
                return (
                  <button
                    key={tier}
                    type="button"
                    data-testid={`tier-select-${tier}`}
                    disabled={busy || active}
                    onClick={() => tierMutation.mutate(tier)}
                    className={
                      active
                        ? 'rounded border border-accent bg-accent/10 px-3 py-1.5 text-sm font-medium text-accent'
                        : 'rounded border border-border px-3 py-1.5 text-sm text-text hover:bg-surface'
                    }
                  >
                    {TIER_LABELS[tier]}
                  </button>
                );
              })}
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold text-text">Enabled modules</h3>
            <p className="text-sm text-text-muted">
              Toggle commercial modules for this tenant. Changes take effect immediately for API
              gatekeepers and session hydration.
            </p>
            <ul className="divide-y divide-border rounded-lg border border-border">
              {APP_MODULES.map((module) => {
                const enabled = modules.has(module);
                const locked = module === 'CORE';
                return (
                  <li key={module} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div>
                      <p className="text-sm font-medium text-text">{MODULE_LABELS[module]}</p>
                      <p className="text-xs text-text-muted">
                        {module}
                        {includedInSelectedTier.has(module)
                          ? ' · Included in Tier'
                          : enabled
                            ? ' · Custom Add-on'
                            : ' · Not in tier'}
                      </p>
                    </div>
                      <input
                      type="checkbox"
                      className="admin-switch"
                      checked={enabled}
                      disabled={locked || busy}
                      aria-label={`Enable ${MODULE_LABELS[module]}`}
                      data-testid={`module-toggle-${module}`}
                      onChange={(e) => toggle(module, e.target.checked)}
                    />
                    {pending === module && modulesMutation.isPending ? (
                      <span className="sr-only">Saving…</span>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          </section>
        </div>
      ) : null}
    </SlideOutDrawer>
  );
}
