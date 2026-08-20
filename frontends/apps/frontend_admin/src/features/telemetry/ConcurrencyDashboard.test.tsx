import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ConcurrencyDashboard } from './ConcurrencyDashboard';
import { fetchTenantTelemetry, patchTenantThrottle, putTenantRateLimit } from './api';

vi.mock('./api', () => ({
  fetchTenantTelemetry: vi.fn(),
  patchTenantThrottle: vi.fn(),
  putTenantRateLimit: vi.fn(),
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

const demo = {
  tenantId: 't-demo',
  slug: 'demo-corp',
  status: 'ACTIVE',
  p50LatencyMs: 12,
  p95LatencyMs: 40,
  capacityMultiplier: 1,
  customRateLimit: 80,
  isThrottled: false,
};

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('ConcurrencyDashboard traffic controller', () => {
  beforeEach(() => {
    vi.mocked(fetchTenantTelemetry).mockReset();
    vi.mocked(patchTenantThrottle).mockReset();
    vi.mocked(putTenantRateLimit).mockReset();
    vi.mocked(fetchTenantTelemetry).mockResolvedValue([demo]);
    vi.mocked(patchTenantThrottle).mockResolvedValue({ ...demo, isThrottled: true, customRateLimit: 80 });
  });

  it('filters tenants and posts the kill switch', async () => {
    wrap(<ConcurrencyDashboard />);
    expect(await screen.findByTestId('live-traffic-controller')).toBeTruthy();
    expect(screen.getByTestId('traffic-row-demo-corp')).toBeTruthy();

    fireEvent.change(screen.getByTestId('traffic-search'), { target: { value: 'nope' } });
    expect(screen.queryByTestId('traffic-row-demo-corp')).toBeNull();
    fireEvent.change(screen.getByTestId('traffic-search'), { target: { value: 'demo' } });
    expect(screen.getByTestId('traffic-row-demo-corp')).toBeTruthy();

    fireEvent.click(screen.getByTestId('kill-switch-demo-corp'));
    fireEvent.click(screen.getByRole('button', { name: /apply traffic policy/i }));
    await waitFor(() => {
      expect(patchTenantThrottle).toHaveBeenCalledWith('t-demo', {
        tenantId: 't-demo',
        customRateLimit: 80,
        isThrottled: true,
      });
    });
  });
});
