import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import type { ControlPlaneTenant } from '@invsys/shared-types';
import { TenantManager } from './TenantManager';
import { cloneSandbox, fetchTenants } from './api';
import { fetchTierDefinitions } from '@/features/packaging/api';

vi.mock('./api', () => ({
  fetchTenants: vi.fn(),
  cloneSandbox: vi.fn(),
  patchTenantModules: vi.fn(),
  patchTenantTier: vi.fn(),
  patchTenantStatus: vi.fn(),
  impersonateTenant: vi.fn(),
}));

vi.mock('@/features/packaging/api', () => ({
  fetchTierDefinitions: vi.fn(),
}));

vi.mock('@invsys/shared-ui', async () => {
  const actual = await vi.importActual<typeof import('@invsys/shared-ui')>('@invsys/shared-ui');
  return {
    ...actual,
    useToast: () => ({
      success: vi.fn(),
      danger: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
    }),
  };
});

const demo: ControlPlaneTenant = {
  tenantId: 't-demo',
  name: 'Demo Corp',
  slug: 'demo-corp',
  status: 'ACTIVE',
  tier: 'INTERMEDIATE',
  enabledModules: ['CORE', 'SHOPIFY', 'B2B_SHOWROOM', 'FINTECH'],
};

function wrap(ui: ReactNode, client?: QueryClient) {
  const queryClient =
    client ??
    new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
  return {
    client: queryClient,
    ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>),
  };
}

describe('TenantManager entitlements drawer', () => {
  beforeEach(() => {
    vi.mocked(fetchTenants).mockReset();
    vi.mocked(cloneSandbox).mockReset();
    vi.mocked(fetchTierDefinitions).mockReset();
    vi.mocked(fetchTenants).mockResolvedValue([demo]);
    vi.mocked(fetchTierDefinitions).mockResolvedValue([
      {
        tierCode: 'INTERMEDIATE',
        displayName: 'Intermediate',
        defaultModules: ['CORE', 'SHOPIFY', 'MANUFACTURING'],
        updatedAt: '2026-08-17T00:00:00Z',
      },
      {
        tierCode: 'ENTERPRISE',
        displayName: 'Enterprise',
        defaultModules: ['CORE', 'SHOPIFY', 'MANUFACTURING', 'FINTECH'],
        updatedAt: '2026-08-17T00:00:00Z',
      },
    ]);
  });

  it('rebinds the open drawer to the live tenant row after cache updates', async () => {
    const { client } = wrap(<TenantManager />);
    fireEvent.click(await screen.findByTestId('tenant-row-demo-corp'));

    expect(await screen.findByTestId('tenant-entitlements-drawer')).toBeTruthy();
    expect(screen.getByText(/demo-corp · INTERMEDIATE · ACTIVE/)).toBeTruthy();

    client.setQueryData<ControlPlaneTenant[]>(['control-plane', 'tenants'], [
      {
        ...demo,
        tier: 'ENTERPRISE',
        enabledModules: ['CORE', 'SHOPIFY', 'MANUFACTURING', 'FINTECH'],
      },
    ]);

    await waitFor(() => {
      expect(screen.getByText(/demo-corp · ENTERPRISE · ACTIVE/)).toBeTruthy();
    });
    expect((screen.getByTestId('module-toggle-MANUFACTURING') as HTMLInputElement).checked).toBe(
      true,
    );
  });
});
