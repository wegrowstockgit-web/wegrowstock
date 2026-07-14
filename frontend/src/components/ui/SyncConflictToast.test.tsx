import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SyncConflictToast } from './SyncConflictToast';
import { useSyncConflictStore } from '@/stores/syncConflicts';

describe('SyncConflictToast', () => {
  beforeEach(() => {
    useSyncConflictStore.setState({ syncConflicts: [] });
  });

  it('renders nothing when there are no conflicts', () => {
    const { container } = render(<SyncConflictToast />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows conflict details and dismisses', () => {
    useSyncConflictStore.getState().addConflict({
      id: 'c1',
      idempotencyKey: 'k1',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      status: 409,
      message: 'Stock no longer available',
      failedAt: Date.now(),
    });

    render(<SyncConflictToast />);
    expect(screen.getByTestId('sync-conflict-toast')).toBeInTheDocument();
    expect(screen.getByText('Stock no longer available')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(useSyncConflictStore.getState().syncConflicts).toHaveLength(0);
  });
});
