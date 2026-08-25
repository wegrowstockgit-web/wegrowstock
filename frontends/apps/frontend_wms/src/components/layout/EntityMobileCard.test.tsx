import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EntityMobileCard } from './EntityMobileCard';

describe('EntityMobileCard', () => {
  it('renders identity, title, amount, and date', () => {
    render(
      <EntityMobileCard
        testId="order-card"
        identity="SO-100"
        title="Metro Distributors"
        amount="$120.00"
        date="Aug 25, 2026"
        status={<span>Open</span>}
      />,
    );
    expect(screen.getByTestId('order-card')).toHaveTextContent('SO-100');
    expect(screen.getByText('Metro Distributors')).toBeInTheDocument();
    expect(screen.getByText('$120.00')).toBeInTheDocument();
    expect(screen.getByText('Aug 25, 2026')).toBeInTheDocument();
    expect(screen.getByText('Open')).toBeInTheDocument();
  });

  it('activates on click when interactive', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <EntityMobileCard testId="order-card" identity="SO-100" title="Buyer" onClick={onClick} />,
    );
    await user.click(screen.getByTestId('order-card'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
