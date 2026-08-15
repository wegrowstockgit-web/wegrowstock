import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { RolePermissionsMatrixResponse } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';

const PERMISSION_LABELS: Record<string, string> = {
  'inventory:cost:view': 'View Unit Costs',
  'inventory:adjust': 'Adjust Inventory',
  'purchasing:po:approve': 'Approve Purchase Orders',
  'sales:invoice:void': 'Void Invoices',
  'settings:users:manage': 'Manage Users',
  'fulfillment:override': 'Fulfillment Override',
  'returns:qc:process': 'Process RMA QC',
  'mrp:run': 'Run MRP Reorder',
  'printing:thermal': 'Thermal Printing',
  'edi:outbound': 'EDI Outbound',
  'so:discount:override': 'Override Pricing',
};

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

  const { permissionKeys } = data;

  return (
    <Card data-testid="role-permissions-matrix">
      <CardHeader
        title="Role permissions"
        description="Granular toggles per role. Users with multiple roles receive the union of granted permissions."
      />
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
