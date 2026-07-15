import { beforeEach, describe, expect, it } from 'vitest';
import { useOfflineStore } from '@/stores/offlineStore';

describe('offlineStore quarantine queue', () => {
  beforeEach(() => {
    useOfflineStore.setState({ quarantinedMutations: [] });
  });

  it('quarantines and discards failed mutations', () => {
    useOfflineStore.getState().quarantineMutation({
      id: 'q1',
      idempotencyKey: 'idem-1',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: '01234567890128', quantity: 4, isGs1: true },
      status: 409,
      title: 'INSUFFICIENT_STOCK',
      detail: 'Insufficient stock',
      failedAt: Date.now(),
    });

    expect(useOfflineStore.getState().quarantinedMutations).toHaveLength(1);
    expect(useOfflineStore.getState().quarantinedMutations[0].detail).toBe('Insufficient stock');

    useOfflineStore.getState().discardQuarantined('q1');
    expect(useOfflineStore.getState().quarantinedMutations).toHaveLength(0);
  });

  it('dedupes by id and caps at 50', () => {
    for (let i = 0; i < 55; i++) {
      useOfflineStore.getState().quarantineMutation({
        id: `q-${i}`,
        idempotencyKey: `idem-${i}`,
        method: 'POST',
        url: '/api/v1/fulfillment/scan',
        status: 409,
        title: 'X',
        detail: 'y',
        failedAt: i,
      });
    }
    expect(useOfflineStore.getState().quarantinedMutations).toHaveLength(50);

    useOfflineStore.getState().quarantineMutation({
      id: 'q-54',
      idempotencyKey: 'idem-54',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      status: 409,
      title: 'UPDATED',
      detail: 'replaced',
      failedAt: 999,
    });
    const entry = useOfflineStore.getState().quarantinedMutations.find((m) => m.id === 'q-54');
    expect(entry?.title).toBe('UPDATED');
  });
});
