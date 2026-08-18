import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { RolePermissionsMatrixResponse } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';
import { useEntitlement } from '@/hooks/useEntitlement';
import {
  NETWORK_ACCESS_LABELS,
  NETWORK_ACCESS_LEVELS,
  parseNetworkAccessLevel,
  type NetworkAccessLevel,
} from '@/features/settings/networkAccess';

type SystemPermission = {
  key: string;
  label: string;
  requiredModule?: string;
};

/** Hardcoded permission catalog with commercial-module gating. */
const SYSTEM_PERMISSIONS: SystemPermission[] = [
  { key: 'inventory:cost:view', label: 'View Unit Costs' },
  { key: 'inventory:adjust', label: 'Adjust Inventory' },
  { key: 'purchasing:po:approve', label: 'Approve Purchase Orders' },
  { key: 'sales:invoice:void', label: 'Void Invoices' },
  { key: 'settings:users:manage', label: 'Manage Users' },
  { key: 'fulfillment:override', label: 'Fulfillment Override', requiredModule: 'ADVANCED_FULFILLMENT' },
  { key: 'returns:qc:process', label: 'Process RMA QC' },
  { key: 'mrp:run', label: 'Run MRP Reorder', requiredModule: 'MRP' },
  { key: 'printing:thermal', label: 'Thermal Printing' },
  { key: 'edi:outbound', label: 'EDI Outbound', requiredModule: 'DOCUMENTS' },
  { key: 'so:discount:override', label: 'Override Pricing' },
  { key: 'customers:manage', label: 'Manage Customers' },
  { key: 'pos.operate', label: 'Operate POS', requiredModule: 'RETAIL_POS' },
  { key: 'pos.supervise', label: 'Supervise POS', requiredModule: 'RETAIL_POS' },
];

const PERMISSION_LABELS: Record<string, string> = Object.fromEntries(
  SYSTEM_PERMISSIONS.map((permission) => [permission.key, permission.label]),
);

const PERMISSION_REQUIRED_MODULE: Record<string, string> = Object.fromEntries(
  SYSTEM_PERMISSIONS.filter((permission) => permission.requiredModule).map((permission) => [
    permission.key,
    permission.requiredModule as string,
  ]),
);

function formatPermissionLabel(key: string): string {
  return (
    PERMISSION_LABELS[key] ??
    key
      .split(/[._-]/)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
      .join(' ')
  );
}

/** System roles shown as matrix columns (excludes portal-only roles). */
const MATRIX_ROLE_ORDER = ['ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER', 'OWNER'];

export function RolePermissionsMatrix() {
  const queryClient = useQueryClient();
  const [cidrDraft, setCidrDraft] = useState('');
  const { hasModule } = useEntitlement();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['role-permissions'],
    queryFn: async () =>
      (await apiClient.get<RolePermissionsMatrixResponse>('/api/v1/settings/permissions')).data,
    retry: false,
  });

  const roles = useMemo(() => {
    const list = data?.roles ?? [];
    return [...list].sort((a, b) => {
      const ai = MATRIX_ROLE_ORDER.indexOf(a.name);
      const bi = MATRIX_ROLE_ORDER.indexOf(b.name);
      return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
    });
  }, [data?.roles]);

  const grantMap = useMemo(() => {
    const map = new Map<string, boolean>();
    for (const grant of data?.grants ?? []) {
      map.set(`${grant.roleId}:${grant.permissionKey}`, grant.granted);
    }
    return map;
  }, [data?.grants]);

  const cidrs = data?.allowedCidrBlocks ?? [];

  const toggleMutation = useMutation({
    mutationFn: async ({
      roleId,
      permissionKey,
      granted,
    }: {
      roleId: string;
      permissionKey: string;
      granted: boolean;
    }) => {
      await apiClient.patch('/api/v1/settings/permissions', {
        roleId,
        permissionKey,
        granted,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['role-permissions'] });
    },
  });

  const networkMutation = useMutation({
    mutationFn: async ({
      roleId,
      networkAccessLevel,
    }: {
      roleId: string;
      networkAccessLevel: NetworkAccessLevel;
    }) => {
      await apiClient.patch('/api/v1/settings/permissions/network-access', {
        roleId,
        networkAccessLevel,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['role-permissions'] });
    },
  });

  const cidrMutation = useMutation({
    mutationFn: async (allowedCidrBlocks: string[]) => {
      await apiClient.patch('/api/v1/settings/permissions/allowed-cidrs', { allowedCidrBlocks });
    },
    onSuccess: () => {
      setCidrDraft('');
      void queryClient.invalidateQueries({ queryKey: ['role-permissions'] });
    },
  });

  if (isLoading) {
    return <TableSkeleton rows={4} cols={6} />;
  }

  if (isError || !data) {
    return (
      <Card data-testid="role-permissions-matrix">
        <CardHeader
          title="Role permissions"
          description="Could not load permission matrix. Ensure the settings API is available."
        />
      </Card>
    );
  }

  const { permissionKeys: catalogKeys } = data;
  const permissionKeys = catalogKeys.filter((key) => {
    const requiredModule = PERMISSION_REQUIRED_MODULE[key];
    return !requiredModule || hasModule(requiredModule);
  });

  const addCidr = () => {
    const next = cidrDraft.trim();
    if (!next || cidrs.includes(next)) return;
    cidrMutation.mutate([...cidrs, next]);
  };

  return (
    <Card data-testid="role-permissions-matrix">
      <CardHeader
        title="Role permissions"
        description="Granular toggles per role. Users with multiple roles receive the union of granted permissions. Network access is the highest assigned level."
      />
      <div className="mb-4 space-y-2 rounded-lg border border-border bg-surface-overlay/40 p-3" data-testid="corporate-ip-allowlist">
        <p className="text-sm font-semibold text-text">Corporate IP Allowlist</p>
        <p className="text-xs text-text-muted">
          CIDRs that count as the internal warehouse / office network. Leave empty to disable fencing.
        </p>
        <div className="flex flex-wrap gap-2">
          {cidrs.map((cidr) => (
            <button
              key={cidr}
              type="button"
              className="rounded-full border border-border bg-surface-raised px-2 py-0.5 text-xs"
              data-testid={`cidr-chip-${cidr}`}
              onClick={() => cidrMutation.mutate(cidrs.filter((item) => item !== cidr))}
            >
              {cidr} ×
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <Input
            value={cidrDraft}
            onChange={(e) => setCidrDraft(e.target.value)}
            placeholder="10.0.0.0/8"
            data-testid="cidr-input"
            className="max-w-xs"
          />
          <Button type="button" size="sm" data-testid="cidr-add" onClick={addCidr} loading={cidrMutation.isPending}>
            Add CIDR
          </Button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[32rem] border-collapse text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="sticky left-0 z-10 bg-surface-raised px-3 py-2 text-left font-semibold text-text">
                Permission
              </th>
              {roles.map((role) => (
                <th
                  key={role.id}
                  className="px-3 py-2 text-center font-semibold text-text whitespace-nowrap"
                >
                  {role.name === 'WAREHOUSE_MANAGER' ? 'Manager' : role.name.charAt(0) + role.name.slice(1).toLowerCase()}
                </th>
              ))}
            </tr>
            <tr className="border-b border-border bg-surface-overlay/30">
              <th className="sticky left-0 z-10 bg-surface-raised px-3 py-2 text-left text-xs font-medium text-text-muted">
                Network access
              </th>
              {roles.map((role) => {
                const level = parseNetworkAccessLevel(role.networkAccessLevel);
                return (
                  <th key={role.id} className="px-2 py-2">
                    <select
                      aria-label={`${role.name} network access`}
                      data-testid={`network-access-${role.name}`}
                      className="w-full rounded border border-border bg-surface-raised px-1 py-1 text-xs"
                      value={level}
                      onChange={(e) =>
                        networkMutation.mutate({
                          roleId: role.id,
                          networkAccessLevel: parseNetworkAccessLevel(e.target.value),
                        })
                      }
                    >
                      {NETWORK_ACCESS_LEVELS.map((option) => (
                        <option key={option} value={option}>
                          {NETWORK_ACCESS_LABELS[option]}
                        </option>
                      ))}
                    </select>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {permissionKeys.map((permissionKey) => (
              <tr key={permissionKey} className="border-b border-border/60 hover:bg-surface-overlay/50">
                <td className="sticky left-0 z-10 bg-surface-raised px-3 py-2 font-medium text-text">
                  {formatPermissionLabel(permissionKey)}
                  <span className="mt-0.5 block font-mono text-xs text-text-muted">{permissionKey}</span>
                </td>
                {roles.map((role) => {
                  const granted = grantMap.get(`${role.id}:${permissionKey}`) ?? false;
                  const pending =
                    toggleMutation.isPending &&
                    toggleMutation.variables?.roleId === role.id &&
                    toggleMutation.variables?.permissionKey === permissionKey;
                  return (
                    <td key={role.id} className="px-3 py-2 text-center">
                      <button
                        type="button"
                        role="switch"
                        aria-checked={granted}
                        aria-label={`${role.name} — ${permissionKey}`}
                        data-testid={`perm-${role.name}-${permissionKey}`}
                        disabled={pending}
                        onClick={() =>
                          toggleMutation.mutate({
                            roleId: role.id,
                            permissionKey,
                            granted: !granted,
                          })
                        }
                        className={cn(
                          'inline-flex h-8 w-14 items-center rounded-full border-2 transition-colors',
                          granted
                            ? 'border-accent bg-accent justify-end'
                            : 'border-border bg-surface-overlay justify-start',
                          pending && 'opacity-60',
                        )}
                      >
                        <span className="mx-1 h-5 w-5 rounded-full bg-surface-raised shadow-sm" />
                      </button>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
