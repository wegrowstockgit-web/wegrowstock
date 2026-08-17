import { apiClient } from '@/api/client';
import type { CreateUserPayload, TenantUser, UpdateUserPayload } from '@/api/types';

function resolveRoleIds(payload: { roleIds?: string[]; roles?: string[]; role?: string }) {
  const fromIds = payload.roleIds?.filter(Boolean) ?? [];
  if (fromIds.length > 0) return fromIds;
  const fromRoles = payload.roles?.filter(Boolean) ?? [];
  if (fromRoles.length > 0) return fromRoles;
  return payload.role ? [payload.role] : [];
}

export function toInviteBody(payload: CreateUserPayload) {
  const roleIds = resolveRoleIds(payload);
  return {
    email: payload.email,
    role: roleIds[0],
    roles: roleIds,
    roleIds,
    customerId: payload.customerId,
    supplierId: payload.supplierId,
  };
}

export function toOrgScopeBody(payload: UpdateUserPayload) {
  const roleIds = resolveRoleIds(payload);
  return {
    ...payload,
    role: roleIds[0],
    roles: roleIds,
    roleIds,
  };
}

export const userApi = {
  list: async () => (await apiClient.get<TenantUser[]>('/api/v1/users')).data,
  create: async (payload: CreateUserPayload) =>
    (await apiClient.post('/api/v1/users/invitations', toInviteBody(payload))).data,
  update: async (userId: string, payload: UpdateUserPayload) =>
    (await apiClient.patch<TenantUser>(`/api/v1/users/${userId}/org-scope`, toOrgScopeBody(payload))).data,
};
