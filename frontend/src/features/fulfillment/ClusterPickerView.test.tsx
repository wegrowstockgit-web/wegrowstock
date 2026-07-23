import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ClusterPickerView } from './ClusterPickerView';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@/hooks/useHardwareScanner', () => ({
  useHardwareScanner: vi.fn(),
}));

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('ClusterPickerView', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it('renders 12-slot grid when sequence loads', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [
        {
          sequenceOrder: 1,
          sku: 'SKU-A',
          qty: 2,
          slotIndex: 1,
          toteBarcode: 'TOTE-A',
          locationPath: '/A/01',
          instruction: 'Scan SKU SKU-A -> Place Qty 2 into Tote slot 1',
        },
        {
          sequenceOrder: 2,
          sku: 'SKU-B',
          qty: 1,
          slotIndex: 3,
          toteBarcode: 'TOTE-B',
          locationPath: '/A/02',
          instruction: 'Scan SKU SKU-B -> Place Qty 1 into Tote slot 3',
        },
      ],
    });

    wrap(<ClusterPickerView />);

    expect(await screen.findByTestId('cluster-picker-view')).toBeInTheDocument();
    await user.type(screen.getByTestId('cluster-pick-batch-input'), 'batch-1');
    await user.click(screen.getByRole('button', { name: /load sequence/i }));

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/fulfillment/cluster/batches/batch-1/pick-sequence',
      );
    });
    expect(await screen.findByText('TOTE-A')).toBeInTheDocument();
    expect(screen.getByText('TOTE-B')).toBeInTheDocument();
    for (let i = 1; i <= 12; i += 1) {
      expect(screen.getByTestId(`cluster-slot-${i}`)).toBeInTheDocument();
    }
  });
});
