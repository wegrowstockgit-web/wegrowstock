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

  it('shows empty state and falls back to URL when body has no barcode', () => {
    useOfflineStore.setState({
      quarantinedMutations: [
        {
          id: 'q2',
          idempotencyKey: 'idem-2',
          method: 'POST',
          url: '/api/v1/fulfillment/scan',
          body: { quantity: 1 },
          status: 409,
          title: 'CONFLICT',
          detail: 'Conflict',
          failedAt: Date.now(),
        },
        {
          id: 'q3',
          idempotencyKey: 'idem-3',
          method: 'POST',
          url: '/api/v1/fulfillment/scan',
          body: null,
          status: 409,
          title: 'CONFLICT',
          detail: 'Conflict 2',
          failedAt: Date.now(),
        },
      ],
    });
    render(<QuarantineReview />);
    expect(screen.getAllByText('/api/v1/fulfillment/scan').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: /Discard all/i }));
    expect(useOfflineStore.getState().quarantinedMutations).toHaveLength(0);
  });

  it('renders empty card when nothing is quarantined', () => {
    useOfflineStore.setState({ quarantinedMutations: [] });
    render(<QuarantineReview />);
    expect(screen.getByText(/No quarantined scans/i)).toBeInTheDocument();
  });
});
