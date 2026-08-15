import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReplenishmentBadge, ReplenishmentQueue } from './ReplenishmentQueue';
import { apiClient } from '@/api/client';
import type { ReplenishmentTask } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const task: ReplenishmentTask = {
  ruleId: 'rule-1',
  variantId: 'v-1',
  sku: 'RPL-1',
  variantName: 'Replenish item',
  fromLocationId: 'res-1',
  fromLocationCode: 'RSV-1',
  fromLocationPath: 'WH/RSV-1',
  toLocationId: 'pf-1',
  toLocationCode: 'PF-1',
  toLocationPath: 'WH/PF-1',
  pickFaceOnHand: 2,
  minQuantity: 10,
  maxQuantity: 40,
  suggestedQuantity: 38,
  instruction: 'Move 38 of RPL-1 from RSV-1 to PF-1',
};

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('ReplenishmentQueue', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it('shows badge count and confirms transfer', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [task] } as never);
    vi.mocked(apiClient.post).mockResolvedValue({ data: { transferGroupId: 'g1' } } as never);

    const onOpen = vi.fn();
    wrap(<ReplenishmentBadge onOpen={onOpen} />);
    expect(await screen.findByTestId('replenishments-count')).toHaveTextContent('1');
    fireEvent.click(screen.getByTestId('replenishments-needed'));
    expect(onOpen).toHaveBeenCalled();

    wrap(<ReplenishmentQueue onClose={vi.fn()} />);
    expect(await screen.findByText(/Move 38 of RPL-1/)).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('confirm-replenishment'));
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/warehouse/replenishments/confirm',
        expect.objectContaining({
          variantId: 'v-1',
          quantity: 38,
        }),
      );
    });
  });
});
