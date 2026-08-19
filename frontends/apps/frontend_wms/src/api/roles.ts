import { apiClient } from '@/api/client';
import type { RoleDefinition, RolePermissionGrant } from '@/api/types';

export interface CreateCustomRolePayload {
  name: string;
  cloneFromRoleId?: string | null;
}

export interface RolePermissionUpdate {
  permissionKey: string;
  granted: boolean;
}

export const roleApi = {
  list: async () => (await apiClient.get<RoleDefinition[]>('/api/v1/roles')).data,
  create: async (payload: CreateCustomRolePayload) =>
    (await apiClient.post<RoleDefinition>('/api/v1/roles', {
      name: payload.name,
      cloneFromRoleId: payload.cloneFromRoleId || undefined,
    })).data,
  updatePermissions: async (roleId: string, grants: RolePermissionUpdate[]) =>
    (await apiClient.put<RolePermissionGrant[]>(`/api/v1/roles/${roleId}/permissions`, { grants })).data,
  delete: async (roleId: string) => {
    await apiClient.delete(`/api/v1/roles/${roleId}`);
  },
};
