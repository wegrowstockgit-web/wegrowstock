import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { RolePermissionsMatrix } from './RolePermissionsMatrix';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
  },
}));

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('RolePermissionsMatrix', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.patch).mockReset();
    useSessionStore.setState({
      authenticated: true,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('renders role x permission grid and toggles a grant via PATCH', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        roles: [
          { id: 'r-admin', name: 'ADMIN', networkAccessLevel: 'MFA_OUTSIDE_NETWORK' },
          { id: 'r-picker', name: 'PICKER', networkAccessLevel: 'STRICT_INTERNAL' },
        ],
        permissionKeys: ['inventory:cost:view', 'so:discount:override'],
        grants: [
          { roleId: 'r-admin', permissionKey: 'inventory:cost:view', granted: true },
          { roleId: 'r-picker', permissionKey: 'inventory:cost:view', granted: false },
          { roleId: 'r-admin', permissionKey: 'so:discount:override', granted: false },
          { roleId: 'r-picker', permissionKey: 'so:discount:override', granted: false },
        ],
        allowedCidrBlocks: ['10.0.0.0/8'],
      },
    } as never);
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} } as never);

    wrap(<RolePermissionsMatrix />);

    expect(await screen.findByTestId('role-permissions-matrix')).toBeInTheDocument();
    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByText('Picker')).toBeInTheDocument();
    expect(screen.getByText('View Unit Costs')).toBeInTheDocument();

    const toggle = screen.getByTestId('perm-PICKER-inventory:cost:view');
    expect(toggle).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(toggle);

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/settings/permissions', {
        roleId: 'r-picker',
        permissionKey: 'inventory:cost:view',
        granted: true,
      });
    });
  });

  it('patches network access level and CIDR allowlist', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        roles: [{ id: 'r-picker', name: 'PICKER', networkAccessLevel: 'STRICT_INTERNAL' }],
        permissionKeys: ['inventory:cost:view'],
        grants: [{ roleId: 'r-picker', permissionKey: 'inventory:cost:view', granted: false }],
        allowedCidrBlocks: [],
      },
    } as never);
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} } as never);

    wrap(<RolePermissionsMatrix />);
    expect(await screen.findByTestId('corporate-ip-allowlist')).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('network-access-PICKER'), {
      target: { value: 'ROAMING' },
    });
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/settings/permissions/network-access', {
        roleId: 'r-picker',
        networkAccessLevel: 'ROAMING',
      });
    });

    fireEvent.change(screen.getByTestId('cidr-input'), { target: { value: '10.0.0.0/8' } });
    fireEvent.click(screen.getByTestId('cidr-add'));
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/settings/permissions/allowed-cidrs', {
        allowedCidrBlocks: ['10.0.0.0/8'],
      });
    });
  });

  it('hides permission rows for unpurchased commercial modules', async () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@acme.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't2',
      enabledModules: ['CORE'],
    });
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        roles: [{ id: 'r-admin', name: 'ADMIN', networkAccessLevel: 'MFA_OUTSIDE_NETWORK' }],
        permissionKeys: ['inventory:cost:view', 'mrp:run', 'pos.operate'],
        grants: [
          { roleId: 'r-admin', permissionKey: 'inventory:cost:view', granted: true },
          { roleId: 'r-admin', permissionKey: 'mrp:run', granted: false },
          { roleId: 'r-admin', permissionKey: 'pos.operate', granted: false },
        ],
        allowedCidrBlocks: [],
      },
    } as never);

    wrap(<RolePermissionsMatrix />);

    expect(await screen.findByText('View Unit Costs')).toBeInTheDocument();
    expect(screen.queryByText('Run MRP Reorder')).not.toBeInTheDocument();
    expect(screen.queryByTestId('perm-ADMIN-mrp:run')).not.toBeInTheDocument();
    expect(screen.queryByTestId('perm-ADMIN-pos.operate')).not.toBeInTheDocument();
  });
});
