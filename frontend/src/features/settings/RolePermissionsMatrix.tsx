import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { RolePermissionsMatrixResponse } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';

function formatPermissionLabel(key: string): string {
  return key
    .split(/[._-]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}

export function RolePermissionsMatrix() {
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['role-permissions'],
    queryFn: async () =>
      (await apiClient.get<RolePermissionsMatrixResponse>('/api/v1/settings/role-permissions'))
        .data,
    retry: false,
  });

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
      await apiClient.put('/api/v1/settings/role-permissions', {
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

  const { roles, permissionKeys } = data;

  return (
    <Card data-testid="role-permissions-matrix">
      <CardHeader
        title="Role permissions"
        description="Toggle granular permissions per role. Changes apply on next session refresh."
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
                  {role.name}
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
