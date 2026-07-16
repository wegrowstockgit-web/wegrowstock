import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useReverseTransactionMutation } from './useReverseTransactionMutation';
import * as inventoryApi from '@/api/inventory';

vi.mock('@/api/inventory', () => ({
  reverseLedgerTransaction: vi.fn(),
  listLedgerTransactions: vi.fn(),
}));

describe('useReverseTransactionMutation', () => {
  beforeEach(() => {
    vi.mocked(inventoryApi.reverseLedgerTransaction).mockReset();
  });

  it('invalidates ledger, levels, and dashboard query keys on success', async () => {
    vi.mocked(inventoryApi.reverseLedgerTransaction).mockResolvedValue({
      id: 'rev-1',
      variantId: 'v1',
      locationId: 'l1',
      lotId: null,
      movementType: 'ADJUST',
      quantityDelta: -1,
      reasonCode: 'ERROR_CORRECTION',
      referenceType: null,
      referenceId: null,
      reversalOfLedgerId: 'orig-1',
      unitCost: null,
      createdAt: '2026-07-14T00:00:00Z',
    });

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(() => useReverseTransactionMutation(), { wrapper });
    result.current.mutate('orig-1');

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const keys = invalidateSpy.mock.calls.map((c) => (c[0] as { queryKey: string[] }).queryKey);
    expect(keys).toEqual(
      expect.arrayContaining([
        ['inventory_ledger'],
        ['inventory_levels'],
        ['dashboard_stats'],
        ['dashboard'],
      ]),
    );
  });
});
