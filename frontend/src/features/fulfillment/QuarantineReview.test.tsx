import { render, screen, fireEvent } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QuarantineReview } from '@/features/fulfillment/QuarantineReview';
import { useOfflineStore } from '@/stores/offlineStore';
import { useSyncConflictStore } from '@/stores/syncConflicts';

describe('QuarantineReview', () => {
  beforeEach(() => {
    useOfflineStore.setState({
      quarantinedMutations: [
        {
          id: 'q1',
          idempotencyKey: 'idem-1',
          method: 'POST',
          url: '/api/v1/fulfillment/scan',
          body: { barcode: '01234567890128', lotNumber: 'L1', quantity: 2, isGs1: true },
          status: 409,
          title: 'ALLOCATION_LOCKED',
          detail: 'Task reassigned to another picker',
          failedAt: Date.now(),
        },
      ],
    });
    useSyncConflictStore.setState({ syncConflicts: [] });
  });

  it('lists rejection reasons and discards scans', () => {
    const onClose = vi.fn();
    render(<QuarantineReview onClose={onClose} />);

    expect(screen.getByText(/Task reassigned to another picker/i)).toBeInTheDocument();
    expect(screen.getByText(/ALLOCATION_LOCKED/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /discard scan/i }));
    expect(useOfflineStore.getState().quarantinedMutations).toHaveLength(0);
  });
});
