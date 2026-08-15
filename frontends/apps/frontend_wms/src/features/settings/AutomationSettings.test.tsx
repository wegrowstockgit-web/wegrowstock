import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AutomationSettings } from './AutomationSettings';

const patch = vi.fn();
const get = vi.fn();
const post = vi.fn();

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => get(...args),
    patch: (...args: unknown[]) => patch(...args),
    post: (...args: unknown[]) => post(...args),
  },
}));

vi.mock('@/components/ui/Toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

function renderWidget() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <AutomationSettings />
    </QueryClientProvider>,
  );
}

describe('AutomationSettings', () => {
  beforeEach(() => {
    get.mockReset();
    patch.mockReset();
    post.mockReset();
    get.mockResolvedValue({
      data: {
        blind_cycle_counts: true,
        predictive_replenishment_enabled: true,
        max_auto_adjust_value: 100,
        rma_auto_approve_max_value: 100,
      },
    });
    patch.mockResolvedValue({ data: {} });
    post.mockResolvedValue({ data: {} });
  });

  it('loads and saves automation fields via PATCH /settings', async () => {
    const user = userEvent.setup();
    renderWidget();
    await waitFor(() =>
      expect(screen.getByTestId('automation-blind-cycle-counts')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('automation-blind-cycle-counts'));
    await user.click(screen.getByTestId('automation-settings-save'));
    await waitFor(() => expect(patch).toHaveBeenCalled());
    expect(patch.mock.calls[0][0]).toBe('/api/v1/settings');
    expect(patch.mock.calls[0][1]).toMatchObject({
      blind_cycle_counts: false,
      predictive_replenishment_enabled: true,
    });
  });
});

