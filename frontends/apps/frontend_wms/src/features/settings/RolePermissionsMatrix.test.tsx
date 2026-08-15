import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { RolePermissionsMatrix } from './RolePermissionsMatrix';
import { apiClient } from '@/api/client';

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
  });

  it('renders role x permission grid and toggles a grant via PATCH', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        roles: [
          { id: 'r-admin', name: 'ADMIN' },
          { id: 'r-picker', name: 'PICKER' },
        ],
        permissionKeys: ['inventory:cost:view', 'so:discount:override'],
        grants: [
          { roleId: 'r-admin', permissionKey: 'inventory:cost:view', granted: true },
          { roleId: 'r-picker', permissionKey: 'inventory:cost:view', granted: false },
          { roleId: 'r-admin', permissionKey: 'so:discount:override', granted: false },
          { roleId: 'r-picker', permissionKey: 'so:discount:override', granted: false },
        ],
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
});
