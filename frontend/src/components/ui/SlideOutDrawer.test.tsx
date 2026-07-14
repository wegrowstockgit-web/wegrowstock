import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SlideOutDrawer } from '@/components/ui/SlideOutDrawer';

describe('SlideOutDrawer', () => {
  it('renders content when open and calls onClose', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(
      <SlideOutDrawer open title="Order SO-100" description="Acme Corp" onClose={onClose}>
        <p>Line items</p>
      </SlideOutDrawer>
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Order SO-100')).toBeInTheDocument();
    expect(screen.getByText('Line items')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Close drawer'));
    expect(onClose).toHaveBeenCalled();
  });

  it('is hidden when closed', () => {
    render(
      <SlideOutDrawer open={false} title="Hidden" onClose={() => {}}>
        <p>Hidden content</p>
      </SlideOutDrawer>
    );

    expect(screen.queryByText('Hidden content')).not.toBeInTheDocument();
  });
});
