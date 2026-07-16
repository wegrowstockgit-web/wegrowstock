import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import { listLedgerTransactions, reverseLedgerTransaction } from '@/api/inventory';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('inventory API', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it('lists ledger transactions', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [{ id: 'a' }] } as never);
    await expect(listLedgerTransactions(25)).resolves.toEqual([{ id: 'a' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/inventory/ledger', { params: { limit: 25 } });
  });

  it('posts a ledger reversal', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'rev' } } as never);
    await expect(reverseLedgerTransaction('led-1')).resolves.toEqual({ id: 'rev' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/inventory/ledger/led-1/reverse');
  });
});
