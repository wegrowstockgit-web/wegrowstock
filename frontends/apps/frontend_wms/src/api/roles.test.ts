import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put, del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));

vi.mock('@/api/client', () => ({
  apiClient: { get, post, put, delete: del },
}));

import { roleApi } from '@/api/roles';

describe('roleApi', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
    del.mockReset();
  });

  it('lists tenant roles', async () => {
    get.mockResolvedValue({
      data: [{ id: 'r1', name: 'ADMIN', isSystemRole: true }],
    });
    await expect(roleApi.list()).resolves.toEqual([
      { id: 'r1', name: 'ADMIN', isSystemRole: true },
    ]);
    expect(get).toHaveBeenCalledWith('/api/v1/roles');
  });

  it('creates a custom role with optional clone source', async () => {
    post.mockResolvedValue({ data: { id: 'r2', name: 'JUNIOR_BUYER', isSystemRole: false } });
    await roleApi.create({ name: 'Junior Buyer', cloneFromRoleId: 'r-picker' });
    expect(post).toHaveBeenCalledWith('/api/v1/roles', {
      name: 'Junior Buyer',
      cloneFromRoleId: 'r-picker',
    });
  });

  it('updates permissions and deletes a custom role', async () => {
    put.mockResolvedValue({ data: [] });
    del.mockResolvedValue({ data: undefined });
    await roleApi.updatePermissions('r2', [{ permissionKey: 'inventory:cost:view', granted: true }]);
    await roleApi.delete('r2');
    expect(put).toHaveBeenCalledWith('/api/v1/roles/r2/permissions', {
      grants: [{ permissionKey: 'inventory:cost:view', granted: true }],
    });
    expect(del).toHaveBeenCalledWith('/api/v1/roles/r2');
  });
});
