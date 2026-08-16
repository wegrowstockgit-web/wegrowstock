import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FlaskConical, Search } from 'lucide-react';
import { TIER_LABELS, type ControlPlaneTenant } from '@invsys/shared-types';
import {
  Button,
  PageSkeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useToast,
} from '@invsys/shared-ui';
import { PageHeader } from '@/features/layout/PageHeader';
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
      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ${tone}`}
      data-testid={`tier-badge-${tier}`}
    >
      {TIER_LABELS[tier]}
    </span>
  );
}

function StatusBadge({ status }: { status: string }) {
  const active = status === 'ACTIVE';
  return (
    <span
      className={
        active
          ? 'inline-flex items-center gap-1.5 text-xs font-medium text-success'
          : 'inline-flex items-center gap-1.5 text-xs font-medium text-warning'
      }
    >
      <span className={`h-1.5 w-1.5 rounded-full ${active ? 'bg-success' : 'bg-warning'}`} />
      {status}
    </span>
  );
}

export function TenantManager() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [selected, setSelected] = useState<ControlPlaneTenant | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [query, setQuery] = useState('');

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

  const sorted = useMemo(() => {
    const q = query.trim().toLowerCase();
    return [...tenants]
      .filter((tenant) => {
        if (!q) return true;
        return (
          tenant.name.toLowerCase().includes(q) ||
          tenant.slug.toLowerCase().includes(q) ||
          tenant.status.toLowerCase().includes(q) ||
          tenant.tier.toLowerCase().includes(q)
        );
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [tenants, query]);

  const openTenant = (tenant: ControlPlaneTenant) => {
    setSelected(tenant);
    setDrawerOpen(true);
  };

  return (
    <div className="space-y-6" data-testid="tenant-manager">
      <PageHeader
        title="Registered tenants"
        description="Select a tenant to manage entitlements, impersonation, and sandbox provisioning."
        actions={
          <Button variant="secondary" size="sm" type="button" onClick={() => void refetch()}>
            Refresh
          </Button>
        }
      />

      <label className="relative block max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search name, slug, tier, or status"
          className="admin-field pl-9"
          aria-label="Filter tenants"
        />
      </label>

      {isLoading ? (
        <PageSkeleton label="Loading tenants…" />
      ) : isError ? (
        <p className="text-sm text-danger">Failed to load tenants.</p>
      ) : sorted.length === 0 ? (
        <div className="admin-card px-5 py-10 text-center">
          <p className="text-sm font-medium text-text">No tenants match that filter.</p>
          <p className="mt-1 text-sm text-text-muted">Clear the search to see every registered tenant.</p>
        </div>
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
                <TableCell>
                  <StatusBadge status={tenant.status} />
                </TableCell>
                <TableCell>
                  <TierBadge tier={tenant.tier} />
                </TableCell>
                <TableCell className="text-text-muted">{tenant.enabledModules.length}</TableCell>
                <TableCell className="text-right">
                  <button
                    type="button"
                    data-testid={`sandbox-btn-${tenant.slug}`}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs font-medium text-text hover:bg-surface"
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
