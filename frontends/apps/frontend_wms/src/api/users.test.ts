import { beforeEach, describe, expect, it, vi } from 'vitest';

const { post, patch, get } = vi.hoisted(() => ({
  post: vi.fn(),
  patch: vi.fn(),
  get: vi.fn(),
}));

vi.mock('@/api/client', () => ({
  apiClient: { post, patch, get },
}));

import { toInviteBody, toOrgScopeBody, userApi } from '@/api/users';

describe('userApi payloads', () => {
  beforeEach(() => {
    post.mockReset();
    patch.mockReset();
    get.mockReset();
  });

  it('sends roleIds array on invite', async () => {
    post.mockResolvedValue({ data: { email: 'a@b.c' } });
    await userApi.create({ email: 'a@b.c', roleIds: ['PICKER', 'RETAIL_CASHIER'] });
    expect(post).toHaveBeenCalledWith('/api/v1/users/invitations', {
      email: 'a@b.c',
      role: 'PICKER',
      roles: ['PICKER', 'RETAIL_CASHIER'],
      roleIds: ['PICKER', 'RETAIL_CASHIER'],
      customerId: undefined,
      supplierId: undefined,
    });
  });

  it('sends roleIds array on org-scope update', async () => {
    patch.mockResolvedValue({ data: { id: 'u1', roles: ['ADMIN', 'PICKER'] } });
    await userApi.update('u1', { roleIds: ['ADMIN', 'PICKER'] });
    expect(patch).toHaveBeenCalledWith('/api/v1/users/u1/org-scope', {
      roleIds: ['ADMIN', 'PICKER'],
      role: 'ADMIN',
      roles: ['ADMIN', 'PICKER'],
    });
  });

  it('builds invite and org-scope bodies from legacy role', () => {
    expect(toInviteBody({ email: 'x@y.z', roleIds: [], role: 'VIEWER' }).roleIds).toEqual(['VIEWER']);
    expect(toOrgScopeBody({ roleIds: [], roles: ['PICKER', 'ADMIN'] }).roleIds).toEqual(['PICKER', 'ADMIN']);
  });
});
