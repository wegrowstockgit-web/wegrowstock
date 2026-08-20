import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IdleWarningModal } from './IdleWarningModal';

describe('IdleWarningModal', () => {
  it('renders the lock warning and keep-signed-in action', async () => {
    const onStaySignedIn = vi.fn();
    const user = userEvent.setup();
    render(<IdleWarningModal open onStaySignedIn={onStaySignedIn} />);

    expect(screen.getByTestId('idle-warning-modal')).toBeTruthy();
    expect(screen.getByText(/will lock in 2 minutes due to inactivity/i)).toBeTruthy();
    await user.click(screen.getByTestId('idle-keep-signed-in'));
    expect(onStaySignedIn).toHaveBeenCalledTimes(1);
  });

  it('renders nothing when closed', () => {
    const { container } = render(<IdleWarningModal open={false} onStaySignedIn={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });
});
