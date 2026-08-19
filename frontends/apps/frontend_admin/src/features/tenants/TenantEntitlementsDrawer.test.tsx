import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import type { ControlPlaneTenant } from '@invsys/shared-types';
import { TenantEntitlementsDrawer } from './TenantEntitlementsDrawer';
import { createImpersonationSession } from './api';
import { fetchTierDefinitions as fetchTiers } from '@/features/packaging/api';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    createImpersonationSession: vi.fn(),
    cloneSandbox: vi.fn(),
    patchTenantModules: vi.fn(),
    patchTenantTier: vi.fn(),
    patchTenantStatus: vi.fn(),
  };
});

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
  enabledModules: ['CORE', 'SHOPIFY'],
};

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('TenantEntitlementsDrawer impersonation', () => {
  beforeEach(() => {
    vi.mocked(createImpersonationSession).mockReset();
    vi.mocked(fetchTiers).mockReset();
    vi.mocked(fetchTiers).mockResolvedValue([
      {
        tierCode: 'INTERMEDIATE',
        displayName: 'Intermediate',
        defaultModules: ['CORE', 'SHOPIFY'],
        updatedAt: '2026-08-17T00:00:00Z',
      },
    ]);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('hard-redirects into WMS with ?handoff= after Impersonate Owner', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { assign, href: 'http://localhost:3002/' });
    vi.mocked(createImpersonationSession).mockResolvedValue({
      accessToken: 'jwt',
      handoffCode: 'code-1',
      handoffToken: 'code-1',
      expiresInSeconds: 900,
      loginUrl: 'http://localhost:3000/login?handoff=code-1',
      redirectUrl: 'http://localhost:3000/login',
      email: 'owner@demo.test',
    });

    wrap(<TenantEntitlementsDrawer tenant={demo} open onClose={() => undefined} />);

    fireEvent.click(await screen.findByTestId('tenant-impersonate'));
    expect(screen.getByRole('button', { name: /impersonate owner/i })).toBeTruthy();

    await waitFor(() => {
      expect(createImpersonationSession).toHaveBeenCalledWith('t-demo');
      expect(assign).toHaveBeenCalledWith('http://localhost:3000/login?handoff=code-1');
    });
  });
});
