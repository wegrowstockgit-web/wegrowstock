import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ActionDraftCard } from './ActionDraftCard';

describe('ActionDraftCard', () => {
  it('renders editable fields and approves with merged payload', async () => {
    const onApprove = vi.fn(async () => undefined);
    const user = userEvent.setup();

    render(
      <ActionDraftCard
        draft={{
          title: 'Generate cycle count for Aisle-4',
          description: 'Creates a worksheet',
          targetEndpoint: '/api/v1/cycle-counts',
          httpMethod: 'POST',
          payload: {
            supportAction: 'generateCycleCount',
            zoneId: 'Aisle-4',
            quantity: 2,
          },
        }}
        onApprove={onApprove}
        onCancel={() => undefined}
      />,
    );

    expect(screen.getByTestId('support-action-draft')).toHaveTextContent(/Aisle-4/i);
    expect(screen.queryByTestId('support-draft-field-supportAction')).not.toBeInTheDocument();

    const zone = screen.getByTestId('support-draft-field-zoneId');
    await user.clear(zone);
    await user.type(zone, 'Aisle-9');
    await user.click(screen.getByTestId('support-draft-approve'));

    await waitFor(() => {
      expect(onApprove).toHaveBeenCalled();
    });
    const arg = onApprove.mock.calls[0]?.[0];
    expect(arg?.payload?.zoneId).toBe('Aisle-9');
    expect(arg?.payload?.supportAction).toBe('generateCycleCount');
    expect(arg?.payload?.quantity).toBe(2);
  });
});
