import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { RolePermissionsMatrix } from './RolePermissionsMatrix';
import { apiClient } from '@/api/client';
import { roleApi } from '@/api/roles';
import { useSessionStore } from '@/stores/session';
import { useCurrentNetwork } from '@/hooks/useCurrentNetwork';
import { ToastProvider } from '@/components/ui/Toast';
import type { CurrentNetworkInfo } from '@/api/settings';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/api/roles', () => ({
  roleApi: {
    list: vi.fn(),
    create: vi.fn(),
    updatePermissions: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/hooks/useCurrentNetwork', () => ({
  useCurrentNetwork: vi.fn(),
}));

const COVERED_NETWORK: CurrentNetworkInfo = {
  clientIp: '10.1.2.3',
  suggestedCidr: '10.1.2.3/32',
  isPrivateNetwork: true,
  networkHint: 'Internal VPN / LAN',
};

const UNCOVERED_NETWORK: CurrentNetworkInfo = {
  clientIp: '198.51.100.45',
  suggestedCidr: '198.51.100.45/32',
  isPrivateNetwork: false,
  networkHint: 'Public Corporate Gateway',
};

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>,
  );
}

const MATRIX = {
  roles: [
    { id: 'r-admin', name: 'ADMIN', networkAccessLevel: 'MFA_OUTSIDE_NETWORK', isSystemRole: true },
    { id: 'r-picker', name: 'PICKER', networkAccessLevel: 'STRICT_INTERNAL', isSystemRole: true },
    { id: 'r-custom', name: 'JUNIOR_BUYER', networkAccessLevel: 'STRICT_INTERNAL', isSystemRole: false },
  ],
  permissionKeys: ['inventory:cost:view', 'so:discount:override', 'mrp:run', 'pos.operate'],
  grants: [
    { roleId: 'r-admin', permissionKey: 'inventory:cost:view', granted: true },
    { roleId: 'r-picker', permissionKey: 'inventory:cost:view', granted: false },
    { roleId: 'r-custom', permissionKey: 'inventory:cost:view', granted: false },
    { roleId: 'r-admin', permissionKey: 'so:discount:override', granted: false },
    { roleId: 'r-picker', permissionKey: 'so:discount:override', granted: false },
    { roleId: 'r-custom', permissionKey: 'so:discount:override', granted: false },
  ],
  allowedCidrBlocks: ['10.0.0.0/8'],
};

describe('RolePermissionsMatrix', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.patch).mockReset();
    vi.mocked(roleApi.list).mockReset();
    vi.mocked(roleApi.create).mockReset();
    vi.mocked(roleApi.updatePermissions).mockReset();
    vi.mocked(roleApi.delete).mockReset();
    vi.mocked(useCurrentNetwork).mockReturnValue({
      networkInfo: COVERED_NETWORK,
      isLoading: false,
      error: null,
      refresh: vi.fn(),
    });
    useSessionStore.setState({
      authenticated: true,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
    vi.mocked(apiClient.get).mockResolvedValue({ data: MATRIX } as never);
    vi.mocked(roleApi.list).mockResolvedValue(MATRIX.roles);
    vi.mocked(roleApi.updatePermissions).mockResolvedValue([]);
    vi.mocked(roleApi.create).mockResolvedValue({
      id: 'r-new',
      name: 'QUALITY_CONTROL_TEMP',
      networkAccessLevel: 'STRICT_INTERNAL',
      isSystemRole: false,
    });
    vi.mocked(roleApi.delete).mockResolvedValue(undefined);
  });

  it('renders dynamic role columns, locks system roles, and PUTs custom grants', async () => {
    wrap(<RolePermissionsMatrix />);

    expect(await screen.findByTestId('role-permissions-matrix')).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('Admin')).toBeInTheDocument();
    expect(within(table).getByText('Picker')).toBeInTheDocument();
    expect(within(table).getByText('Junior Buyer')).toBeInTheDocument();
    expect(screen.getByText('View Unit Costs')).toBeInTheDocument();

    const systemToggle = screen.getByTestId('perm-PICKER-inventory:cost:view');
    expect(systemToggle).toBeDisabled();
    fireEvent.click(systemToggle);
    expect(roleApi.updatePermissions).not.toHaveBeenCalled();
    expect(apiClient.patch).not.toHaveBeenCalled();

    const customToggle = screen.getByTestId('perm-JUNIOR_BUYER-inventory:cost:view');
    expect(customToggle).toBeEnabled();
    expect(customToggle).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(customToggle);

    await waitFor(() => {
      expect(roleApi.updatePermissions).toHaveBeenCalledWith(
        'r-custom',
        expect.arrayContaining([
          { permissionKey: 'inventory:cost:view', granted: true },
        ]),
      );
    });
  });

  it('patches network access level and CIDR allowlist', async () => {
    wrap(<RolePermissionsMatrix />);
    expect(await screen.findByTestId('corporate-ip-allowlist')).toBeInTheDocument();
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} } as never);

    const pickerAccess = screen.getByTestId('network-access-PICKER');
    expect(pickerAccess).toHaveDisplayValue('Internal Only');
    expect(within(pickerAccess).getByRole('option', { name: 'MFA Remote' })).toBeInTheDocument();
    fireEvent.change(pickerAccess, {
      target: { value: 'ROAMING' },
    });
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/settings/permissions/network-access', {
        roleId: 'r-picker',
        networkAccessLevel: 'ROAMING',
      });
    });

    fireEvent.change(screen.getByTestId('cidr-label-input'), { target: { value: 'Austin HQ' } });
    fireEvent.change(screen.getByTestId('cidr-input'), { target: { value: '10.1.0.0/16' } });
    fireEvent.click(screen.getByTestId('cidr-add'));
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/settings/permissions/allowed-cidrs', {
        allowedCidrBlocks: ['10.0.0.0/8', '10.1.0.0/16#Austin HQ'],
      });
    });
  });

  it('adds the detected current network and warns when fencing would lock the admin out', async () => {
    vi.mocked(useCurrentNetwork).mockReturnValue({
      networkInfo: UNCOVERED_NETWORK,
      isLoading: false,
      error: null,
      refresh: vi.fn(),
    });
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} } as never);

    wrap(<RolePermissionsMatrix />);
    expect(await screen.findByTestId('current-network-banner')).toBeInTheDocument();
    expect(screen.getByTestId('current-network-ip')).toHaveTextContent('198.51.100.45');
    expect(screen.getByTestId('current-network-hint')).toHaveTextContent('Public Corporate Gateway');
    expect(screen.getByTestId('cidr-lockout-warning')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('add-current-network'));
    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/settings/permissions/allowed-cidrs', {
        allowedCidrBlocks: ['10.0.0.0/8', '198.51.100.45/32'],
      });
    });
    expect(await screen.findByTestId('app-toast')).toHaveTextContent(
      'Added your current network (198.51.100.45) to the allowlist.',
    );
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

    wrap(<RolePermissionsMatrix />);

    expect(await screen.findByText('View Unit Costs')).toBeInTheDocument();
    expect(screen.queryByText('Run MRP Reorder')).not.toBeInTheDocument();
    expect(screen.queryByTestId('perm-ADMIN-mrp:run')).not.toBeInTheDocument();
    expect(screen.queryByTestId('perm-ADMIN-pos.operate')).not.toBeInTheDocument();
  });

  it('creates a custom role from the dialog, optionally cloning a baseline', async () => {
    wrap(<RolePermissionsMatrix />);
    expect(await screen.findByTestId('create-custom-role')).toHaveTextContent(/create custom role/i);
    fireEvent.click(screen.getByTestId('create-custom-role'));

    expect(await screen.findByTestId('create-role-dialog')).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('create-role-name'), {
      target: { value: 'Quality Control Temp' },
    });
    fireEvent.change(screen.getByTestId('create-role-description'), {
      target: { value: 'Inspect inbound lots before put-away' },
    });
    fireEvent.change(screen.getByTestId('create-role-clone'), {
      target: { value: 'r-picker' },
    });
    fireEvent.click(screen.getByTestId('create-role-submit'));

    await waitFor(() => {
      expect(roleApi.create).toHaveBeenCalledWith({
        name: 'Quality Control Temp',
        cloneFromRoleId: 'r-picker',
        description: 'Inspect inbound lots before put-away',
      });
    });
  });

  it('deletes a custom role from the column header and hides trash on system roles', async () => {
    wrap(<RolePermissionsMatrix />);
    expect(await screen.findByTestId('delete-role-JUNIOR_BUYER')).toBeInTheDocument();
    expect(screen.queryByTestId('delete-role-PICKER')).not.toBeInTheDocument();
    expect(screen.queryByTestId('delete-role-ADMIN')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('delete-role-JUNIOR_BUYER'));
    await waitFor(() => {
      expect(roleApi.delete).toHaveBeenCalledWith('r-custom');
    });
  });
});
