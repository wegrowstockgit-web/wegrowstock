import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  APP_MODULES,
  MODULE_LABELS,
  TIER_LABELS,
  type AppModule,
  type CommercialTier,
  type PlatformTierDefinition,
} from '@invsys/shared-types';
import { PageSkeleton, useToast } from '@invsys/shared-ui';
import { PageHeader } from '@/features/layout/PageHeader';
import { fetchTierDefinitions, putTierDefinition } from './api';

const TIER_ORDER: CommercialTier[] = ['BASIC', 'INTERMEDIATE', 'ENTERPRISE'];

function sortTiers(rows: PlatformTierDefinition[]): PlatformTierDefinition[] {
  return [...rows].sort(
    (a, b) => TIER_ORDER.indexOf(a.tierCode) - TIER_ORDER.indexOf(b.tierCode),
  );
}

export function PlatformPackagingPanel() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data: tiers = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'packaging', 'tiers'],
    queryFn: fetchTierDefinitions,
  });

  const saveMutation = useMutation({
    mutationFn: ({ tierCode, defaultModules }: { tierCode: string; defaultModules: AppModule[] }) =>
      putTierDefinition(tierCode, defaultModules),
    onSuccess: (updated) => {
      queryClient.setQueryData<PlatformTierDefinition[]>(
        ['control-plane', 'packaging', 'tiers'],
        (prev) =>
          (prev ?? []).map((row) => (row.tierCode === updated.tierCode ? updated : row)),
      );
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'packaging', 'tiers'] });
      toast.success(`${updated.displayName} bundle saved`);
    },
    onError: () => {
      toast.danger('Could not save tier packaging.');
    },
  });

  if (isLoading) {
    return (
      <div data-testid="platform-packaging">
        <PageSkeleton label="Loading pricing & packaging…" />
      </div>
    );
  }

  if (isError) {
    return (
      <div data-testid="platform-packaging">
        <p className="text-sm text-danger" data-testid="packaging-load-error">
          Failed to load tier definitions.
        </p>
      </div>
    );
  }

  const toggle = (tier: PlatformTierDefinition, module: AppModule, checked: boolean) => {
    if (module === 'CORE') return;
    const next = new Set(tier.defaultModules);
    if (checked) next.add(module);
    else next.delete(module);
    next.add('CORE');
    saveMutation.mutate({
      tierCode: tier.tierCode,
      defaultModules: APP_MODULES.filter((m) => next.has(m)),
    });
  };

  return (
    <div className="space-y-6" data-testid="platform-packaging">
      <PageHeader
        title="Pricing & Packaging"
        description="Toggle which commercial modules are included in each tier's base price. Changes apply the next time a tenant is assigned that tier."
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {sortTiers(tiers).map((tier) => {
          const enabled = new Set(tier.defaultModules);
          return (
            <section
              key={tier.tierCode}
              className="admin-card p-5"
              data-testid={`packaging-card-${tier.tierCode}`}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-base font-semibold text-text">
                    {tier.displayName || TIER_LABELS[tier.tierCode]}
                  </h3>
                  <p className="mt-1 text-xs font-medium uppercase tracking-[0.12em] text-text-muted">
                    {tier.tierCode}
                  </p>
                </div>
                <p className="text-xs text-text-muted">{enabled.size} modules</p>
              </div>
              <ul className="mt-4 divide-y divide-border overflow-hidden rounded-md border border-border">
                {APP_MODULES.map((module) => {
                  const on = enabled.has(module);
                  const locked = module === 'CORE';
                  return (
                    <li
                      key={module}
                      className="flex items-center justify-between gap-3 bg-surface/40 px-3 py-2.5"
                    >
                      <label
                        htmlFor={`pkg-${tier.tierCode}-${module}`}
                        className="text-sm text-text"
                      >
                        {MODULE_LABELS[module]}
                      </label>
                      <input
                        id={`pkg-${tier.tierCode}-${module}`}
                        type="checkbox"
                        className="admin-switch"
                        checked={on}
                        disabled={locked || saveMutation.isPending}
                        aria-label={`${tier.tierCode} ${MODULE_LABELS[module]}`}
                        data-testid={`packaging-toggle-${tier.tierCode}-${module}`}
                        onChange={(e) => toggle(tier, module, e.target.checked)}
                      />
                    </li>
                  );
                })}
              </ul>
            </section>
          );
        })}
      </div>
    </div>
  );
}
