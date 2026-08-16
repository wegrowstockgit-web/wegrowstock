import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { PlatformPackagingPanel } from './PlatformPackagingPanel';
import { fetchTierDefinitions, putTierDefinition } from './api';

vi.mock('./api', () => ({
  fetchTierDefinitions: vi.fn(),
  putTierDefinition: vi.fn(),
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

describe('PlatformPackagingPanel', () => {
  beforeEach(() => {
    vi.mocked(fetchTierDefinitions).mockReset();
    vi.mocked(putTierDefinition).mockReset();
    vi.mocked(fetchTierDefinitions).mockResolvedValue([
      {
        tierCode: 'BASIC',
        displayName: 'Basic',
        defaultModules: ['CORE'],
        updatedAt: '2026-08-15T00:00:00Z',
      },
      {
        tierCode: 'INTERMEDIATE',
        displayName: 'Intermediate',
        defaultModules: ['CORE', 'SHOPIFY'],
        updatedAt: '2026-08-15T00:00:00Z',
      },
      {
        tierCode: 'ENTERPRISE',
        displayName: 'Enterprise',
        defaultModules: ['CORE', 'FINTECH'],
        updatedAt: '2026-08-15T00:00:00Z',
      },
    ]);
  });

  it('renders a card per tier with live module toggles', async () => {
    wrap(<PlatformPackagingPanel />);

    expect(await screen.findByTestId('packaging-card-BASIC')).toBeTruthy();
    expect(screen.getByTestId('platform-packaging')).toBeTruthy();
    expect(screen.getByTestId('packaging-card-INTERMEDIATE')).toBeTruthy();
    expect(screen.getByTestId('packaging-card-ENTERPRISE')).toBeTruthy();
    expect((screen.getByTestId('packaging-toggle-BASIC-CORE') as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId('packaging-toggle-BASIC-SHOPIFY') as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByTestId('packaging-toggle-INTERMEDIATE-SHOPIFY') as HTMLInputElement).checked).toBe(true);
  });
});
