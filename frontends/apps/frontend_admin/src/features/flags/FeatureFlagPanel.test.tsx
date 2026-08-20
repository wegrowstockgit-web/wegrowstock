import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { FeatureFlagPanel } from './FeatureFlagPanel';
import { createFeatureFlag, fetchFeatureFlags, putFeatureFlagTenants } from './api';

vi.mock('./api', () => ({
  fetchFeatureFlags: vi.fn(),
  createFeatureFlag: vi.fn(),
  putFeatureFlagTenants: vi.fn(),
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

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('FeatureFlagPanel', () => {
  beforeEach(() => {
    vi.mocked(fetchFeatureFlags).mockReset();
    vi.mocked(createFeatureFlag).mockReset();
    vi.mocked(putFeatureFlagTenants).mockReset();
    vi.mocked(fetchFeatureFlags).mockResolvedValue([
      {
        id: 'f1',
        flagKey: 'beta-dock',
        description: 'Dock beta',
        isGlobal: false,
        createdAt: '2026-08-19T00:00:00Z',
        tenants: [],
      },
    ]);
    vi.mocked(createFeatureFlag).mockResolvedValue({
      id: 'f2',
      flagKey: 'new-wave',
      description: 'Wave',
      isGlobal: true,
      createdAt: '2026-08-19T00:00:00Z',
      tenants: [],
    });
    vi.mocked(putFeatureFlagTenants).mockResolvedValue({
      id: 'f1',
      flagKey: 'beta-dock',
      description: 'Dock beta',
      isGlobal: true,
      createdAt: '2026-08-19T00:00:00Z',
      tenants: [{ tenantId: 't-1', enabled: true }],
    });
  });

  it('creates a flag and targets a tenant', async () => {
    wrap(<FeatureFlagPanel />);
    expect(await screen.findByTestId('flag-row-beta-dock')).toBeTruthy();

    fireEvent.change(screen.getByTestId('flag-key-input'), { target: { value: 'new-wave' } });
    fireEvent.click(screen.getByRole('button', { name: /create flag/i }));
    await waitFor(() => {
      expect(createFeatureFlag).toHaveBeenCalledWith({
        flagKey: 'new-wave',
        description: undefined,
        isGlobal: false,
      });
    });

    fireEvent.click(screen.getByTestId('flag-global-beta-dock'));
    await waitFor(() => {
      expect(putFeatureFlagTenants).toHaveBeenCalledWith('f1', {
        isGlobal: true,
        overrides: [],
      });
    });

    fireEvent.change(screen.getByTestId('flag-tenants-beta-dock'), {
      target: { value: 't-1' },
    });
    fireEvent.click(screen.getByRole('button', { name: /save targeting/i }));
    await waitFor(() => {
      expect(putFeatureFlagTenants).toHaveBeenCalledWith('f1', {
        isGlobal: true,
        overrides: [{ tenantId: 't-1', enabled: true }],
      });
    });
  });
});
