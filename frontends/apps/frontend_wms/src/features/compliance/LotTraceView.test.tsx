import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LotTraceView } from './LotTraceView';
import { apiClient } from '@/api/client';
import type { ComplianceLotTraceResponse } from '@/api/types';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

vi.mock('@/hooks/useHardwareScanner', () => ({
  useHardwareScanner: () => undefined,
}));

vi.mock('@/hooks/useScanFeedback', () => ({
  useScanFeedback: () => ({
    triggerSuccess: vi.fn(),
    triggerError: vi.fn(),
  }),
}));

const sample: ComplianceLotTraceResponse = {
  lotId: 'lot-1',
  lotNumber: 'LOT-99',
  variantId: 'v-1',
  sku: 'WIDGET-1',
  origin: {
    ledgerId: 'led-1',
    receivedAt: '2026-01-01T00:00:00Z',
    quantity: 10,
    locationCode: 'RCV',
    locationPath: 'WH/RCV',
    purchaseOrderNumber: 'PO-1',
    supplierName: 'Acme',
  },
  currentExposure: [
    {
      inventoryLevelId: 'il-1',
      locationId: 'loc-1',
      locationCode: 'PF-1',
      locationPath: 'WH/PF-1',
      zoneBehavior: 'PICK_FACE',
      onHand: 4,
      allocated: 0,
      available: 4,
    },
  ],
  downstream: [
    {
      ledgerId: 'ship-1',
      shippedAt: '2026-01-02T00:00:00Z',
      quantity: 2,
      salesOrderNumber: 'SO-1',
      customerName: 'Buyer Co',
      trackingNumber: '1Z',
    },
  ],
};

function renderView() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <LotTraceView />
    </QueryClientProvider>,
  );
}

describe('LotTraceView', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('renders three-part genealogy and enables CSV export', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: sample } as never);
    const createObjectURL = vi.fn(() => 'blob:csv');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const click = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(click);

    renderView();
    fireEvent.change(screen.getByLabelText(/Lot number/i), { target: { value: 'LOT-99' } });
    fireEvent.click(screen.getByRole('button', { name: /Trace/i }));

    await waitFor(() => {
      expect(screen.getByTestId('lot-trace-origin')).toHaveTextContent(/PO-1/);
      expect(screen.getByTestId('lot-trace-exposure')).toHaveTextContent(/PF-1/);
      expect(screen.getByTestId('lot-trace-downstream')).toHaveTextContent(/Buyer Co/);
    });

    fireEvent.click(screen.getByTestId('export-recall-csv'));
    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
  });
});
