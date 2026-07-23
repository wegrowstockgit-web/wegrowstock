import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { RolePermissionsMatrix } from './RolePermissionsMatrix';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
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
    vi.mocked(apiClient.put).mockReset();
  });

  it('renders role x permission grid and toggles a grant', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        roles: [
          { id: 'r-admin', name: 'ADMIN' },
          { id: 'r-picker', name: 'PICKER' },
        ],
        permissionKeys: ['inventory.adjust', 'sales.refund'],
        grants: [
          { roleId: 'r-admin', permissionKey: 'inventory.adjust', granted: true },
          { roleId: 'r-picker', permissionKey: 'inventory.adjust', granted: false },
          { roleId: 'r-admin', permissionKey: 'sales.refund', granted: false },
          { roleId: 'r-picker', permissionKey: 'sales.refund', granted: false },
        ],
      },
    } as never);
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} } as never);

    wrap(<RolePermissionsMatrix />);

    expect(await screen.findByTestId('role-permissions-matrix')).toBeInTheDocument();
    expect(screen.getByText('ADMIN')).toBeInTheDocument();
    expect(screen.getByText('PICKER')).toBeInTheDocument();

    const toggle = screen.getByTestId('perm-PICKER-inventory.adjust');
    expect(toggle).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(toggle);

    await waitFor(() => {
      expect(apiClient.put).toHaveBeenCalledWith('/api/v1/settings/role-permissions', {
        roleId: 'r-picker',
        permissionKey: 'inventory.adjust',
        granted: true,
      });
    });
  });
});
