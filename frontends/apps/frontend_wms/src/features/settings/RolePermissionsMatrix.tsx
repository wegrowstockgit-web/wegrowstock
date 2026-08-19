import { useMemo, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { roleApi } from '@/api/roles';
import type { RoleDefinition, RolePermissionsMatrixResponse } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';
import { useEntitlement } from '@/hooks/useEntitlement';
import { useCurrentNetwork } from '@/hooks/useCurrentNetwork';
import { useToast } from '@/components/ui/Toast';
import {
  NETWORK_ACCESS_LABELS,
  NETWORK_ACCESS_LEVELS,
  clientIpCovered,
  formatCidrEntry,
  parseCidrEntry,
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

function formatRoleColumnName(name: string): string {
  if (name === 'WAREHOUSE_MANAGER') return 'Manager';
  return name
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}

function isLockedSystemRole(role: RoleDefinition): boolean {
  return role.isSystemRole === true;
}

/** Baseline columns first; custom roles append in fetch order. */
const MATRIX_ROLE_ORDER = ['ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER', 'OWNER'];

export function RolePermissionsMatrix() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { networkInfo, isLoading: networkLoading } = useCurrentNetwork();
  const [cidrDraft, setCidrDraft] = useState('');
  const [cidrLabelDraft, setCidrLabelDraft] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [roleName, setRoleName] = useState('');
  const [roleDescription, setRoleDescription] = useState('');
  const [cloneFromRoleId, setCloneFromRoleId] = useState('');
  const { hasModule } = useEntitlement();

  const matrixQuery = useQuery({
    queryKey: ['role-permissions'],
    queryFn: async () =>
      (await apiClient.get<RolePermissionsMatrixResponse>('/api/v1/settings/permissions')).data,
    retry: false,
  });

  const rolesQuery = useQuery({
    queryKey: ['roles'],
    queryFn: roleApi.list,
    retry: false,
  });

  const data = matrixQuery.data;
  const fetchedRoles = rolesQuery.data;

  const roles = useMemo(() => {
    const list = fetchedRoles ?? data?.roles ?? [];
    return [...list].sort((a, b) => {
      const ai = MATRIX_ROLE_ORDER.indexOf(a.name);
      const bi = MATRIX_ROLE_ORDER.indexOf(b.name);
      const aRank = ai === -1 ? 99 : ai;
      const bRank = bi === -1 ? 99 : bi;
      if (aRank !== bRank) return aRank - bRank;
      return a.name.localeCompare(b.name);
    });
  }, [data?.roles, fetchedRoles]);

  const grantMap = useMemo(() => {
    const map = new Map<string, boolean>();
    for (const grant of data?.grants ?? []) {
      map.set(`${grant.roleId}:${grant.permissionKey}`, grant.granted);
    }
    return map;
  }, [data?.grants]);

  const cidrs = data?.allowedCidrBlocks ?? [];
  const catalogKeys = data?.permissionKeys ?? [];

  const invalidateRoles = () => {
    void queryClient.invalidateQueries({ queryKey: ['role-permissions'] });
    void queryClient.invalidateQueries({ queryKey: ['roles'] });
  };

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
      const grants = catalogKeys.map((key) => ({
        permissionKey: key,
        granted: key === permissionKey ? granted : (grantMap.get(`${roleId}:${key}`) ?? false),
      }));
      await roleApi.updatePermissions(roleId, grants);
    },
    onSuccess: invalidateRoles,
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
    onSuccess: invalidateRoles,
  });

  const cidrMutation = useMutation({
    mutationFn: async (allowedCidrBlocks: string[]) => {
      await apiClient.patch('/api/v1/settings/permissions/allowed-cidrs', { allowedCidrBlocks });
    },
    onSuccess: () => {
      setCidrDraft('');
      setCidrLabelDraft('');
      invalidateRoles();
    },
  });

  const createMutation = useMutation({
    mutationFn: async () =>
      roleApi.create({
        name: roleName.trim(),
        cloneFromRoleId: cloneFromRoleId || null,
        description: roleDescription.trim() || null,
      }),
    onSuccess: () => {
      setCreateOpen(false);
      setRoleName('');
      setRoleDescription('');
      setCloneFromRoleId('');
      invalidateRoles();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (roleId: string) => roleApi.delete(roleId),
    onSuccess: invalidateRoles,
  });

  if (matrixQuery.isLoading || rolesQuery.isLoading) {
    return <TableSkeleton rows={4} cols={6} />;
  }

  if (matrixQuery.isError || !data) {
    return (
      <Card data-testid="role-permissions-matrix">
        <CardHeader
          title="Role permissions"
          description="Could not load permission matrix. Ensure the settings API is available."
        />
      </Card>
    );
  }

  const permissionKeys = catalogKeys.filter((key) => {
    const requiredModule = PERMISSION_REQUIRED_MODULE[key];
    return !requiredModule || hasModule(requiredModule);
  });

  const cidrAlreadyListed = (cidr: string) =>
    cidrs.some((entry) => parseCidrEntry(entry).cidr === parseCidrEntry(cidr).cidr);

  const addCidr = () => {
    const next = formatCidrEntry(cidrDraft, cidrLabelDraft);
    if (!parseCidrEntry(next).cidr || cidrAlreadyListed(next)) return;
    cidrMutation.mutate([...cidrs, next]);
  };

  const addCurrentNetwork = () => {
    const suggested = networkInfo?.suggestedCidr?.trim();
    if (!suggested || cidrAlreadyListed(suggested)) return;
    cidrMutation.mutate([...cidrs, formatCidrEntry(suggested, cidrLabelDraft)], {
      onSuccess: () => {
        toast(`Added your current network (${networkInfo?.clientIp}) to the allowlist.`, {
          tone: 'success',
        });
      },
    });
  };

  const fencingEnabled = cidrs.length > 0;
  const currentIpUncovered =
    fencingEnabled &&
    !!networkInfo?.clientIp &&
    networkInfo.clientIp !== 'unknown' &&
    !clientIpCovered(networkInfo.clientIp, cidrs);

  return (
    <Card className="min-w-0" data-testid="role-permissions-matrix">
      <CardHeader
        title="Role permissions"
        description="Granular toggles per custom role. System roles are locked to platform defaults. Users with multiple roles receive the union of granted permissions. Network access is the highest assigned level."
        action={
          <Button
            type="button"
            size="sm"
            className="whitespace-nowrap"
            data-testid="create-custom-role"
            onClick={() => setCreateOpen(true)}
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />
            Create custom role
          </Button>
        }
      />
      <div className="mb-4 space-y-2 rounded-lg border border-border bg-surface-overlay/40 p-3" data-testid="corporate-ip-allowlist">
        <p className="text-sm font-semibold text-text">Corporate IP Allowlist</p>
        <p className="text-xs text-text-muted">
          CIDRs that count as the internal warehouse / office network. Leave empty to disable fencing.
        </p>
        <div
          className="rounded-md border border-border bg-surface-raised px-3 py-2 text-sm"
          data-testid="current-network-banner"
        >
          {networkLoading || !networkInfo ? (
            <p className="text-text-muted">Detecting your current connection…</p>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-text">
                📍 Your Current Connection:{' '}
                <span className="font-mono" data-testid="current-network-ip">
                  {networkInfo.clientIp}
                </span>{' '}
                <span className="text-text-muted" data-testid="current-network-hint">
                  ({networkInfo.networkHint})
                </span>
              </p>
              <Button
                type="button"
                size="sm"
                data-testid="add-current-network"
                onClick={addCurrentNetwork}
                disabled={!networkInfo.suggestedCidr || cidrAlreadyListed(networkInfo.suggestedCidr)}
                loading={cidrMutation.isPending}
              >
                + Add My Current Network
              </Button>
            </div>
          )}
        </div>
        {currentIpUncovered && (
          <div
            className="rounded-md border border-amber-400/60 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-100"
            data-testid="cidr-lockout-warning"
            role="alert"
          >
            ⚠️ Warning: Your current network is not in the allowlist. Saving these settings may restrict your access.
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          {cidrs.map((entry) => {
            const parsed = parseCidrEntry(entry);
            return (
              <button
                key={entry}
                type="button"
                className="rounded-full border border-border bg-surface-raised px-2 py-0.5 text-xs"
                data-testid={`cidr-chip-${parsed.cidr}`}
                onClick={() => cidrMutation.mutate(cidrs.filter((item) => item !== entry))}
              >
                {parsed.label ? `${parsed.label} - ${parsed.cidr}` : parsed.cidr} ×
              </button>
            );
          })}
        </div>
        <div className="flex flex-wrap gap-2">
          <Input
            value={cidrLabelDraft}
            onChange={(e) => setCidrLabelDraft(e.target.value)}
            placeholder="Dallas Warehouse"
            data-testid="cidr-label-input"
            className="max-w-[12rem]"
          />
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
      <div className="min-w-0 max-w-full overflow-x-auto">
        <table className="w-full min-w-[32rem] border-collapse text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="sticky left-0 z-10 bg-surface-raised px-3 py-2 text-left font-semibold text-text">
                Permission
              </th>
              {roles.map((role) => (
                <th
                  key={role.id}
                  className="min-w-[11rem] px-2 py-2 text-center font-semibold text-text"
                >
                  <span className="inline-flex items-center justify-center gap-1">
                    {formatRoleColumnName(role.name)}
                    {!isLockedSystemRole(role) && (
                      <button
                        type="button"
                        aria-label={`Delete ${role.name}`}
                        data-testid={`delete-role-${role.name}`}
                        className="rounded p-0.5 text-text-muted hover:text-danger"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(role.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </span>
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
                  <th key={role.id} className="min-w-[11rem] px-2 py-2 align-middle">
                    <Select
                      aria-label={`${role.name} network access`}
                      data-testid={`network-access-${role.name}`}
                      title={NETWORK_ACCESS_LABELS[level]}
                      size="sm"
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
                    </Select>
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
                  const locked = isLockedSystemRole(role);
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
                        disabled={locked || pending}
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
                          (pending || locked) && 'opacity-45',
                          locked && 'cursor-not-allowed',
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

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Create custom role"
        description="Custom roles start blank or cloned from an existing role. System roles stay locked."
      >
        <form
          className="space-y-4"
          data-testid="create-role-dialog"
          onSubmit={(event) => {
            event.preventDefault();
            if (!roleName.trim()) return;
            createMutation.mutate();
          }}
        >
          <Input
            label="Role Name"
            value={roleName}
            onChange={(e) => setRoleName(e.target.value)}
            placeholder="Quality Control Temp"
            data-testid="create-role-name"
            required
          />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="create-role-description" className="text-sm font-medium text-text">
              Description
            </label>
            <textarea
              id="create-role-description"
              data-testid="create-role-description"
              value={roleDescription}
              onChange={(e) => setRoleDescription(e.target.value)}
              maxLength={255}
              rows={3}
              placeholder="What this role can do in the warehouse"
              className="min-h-[4.5rem] rounded-md border border-border bg-surface-raised px-3 py-2 text-sm text-text"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="clone-role" className="text-sm font-medium text-text">
              Clone permissions from
            </label>
            <select
              id="clone-role"
              data-testid="create-role-clone"
              className="h-10 rounded-md border border-border bg-surface-raised px-3 text-sm"
              value={cloneFromRoleId}
              onChange={(e) => setCloneFromRoleId(e.target.value)}
            >
              <option value="">None (start blank)</option>
              {roles.map((role) => (
                <option key={role.id} value={role.id}>
                  {formatRoleColumnName(role.name)}
                </option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" data-testid="create-role-submit" loading={createMutation.isPending}>
              Create role
            </Button>
          </div>
        </form>
      </Modal>
    </Card>
  );
}
