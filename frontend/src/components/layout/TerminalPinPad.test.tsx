import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { TerminalPinPad } from '@/components/layout/TerminalPinPad';
import { useSessionStore } from '@/stores/session';

vi.mock('@/api/client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe('TerminalPinPad', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'owner@demo.test',
        displayName: 'Demo Owner',
        roles: ['OWNER'],
        warehouseIds: [],
        avatarUrl: null,
        tenantId: 't1',
      },
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('portals the pad to document.body so keys 1–3 are in the dialog', () => {
    render(
      <div className="relative overflow-hidden backdrop-blur-md" style={{ height: 56 }}>
        <TerminalPinPad warehouseSized />
      </div>,
    );

    fireEvent.click(screen.getByTestId('terminal-switch-open'));

    const pad = screen.getByTestId('terminal-pin-pad');
    expect(pad.parentElement).toBe(document.body);

    const keys = within(pad).getByTestId('terminal-pin-keys');
    expect(within(keys).getByRole('button', { name: '1' })).toBeVisible();
    expect(within(keys).getByRole('button', { name: '2' })).toBeVisible();
    expect(within(keys).getByRole('button', { name: '3' })).toBeVisible();
    expect(within(pad).getByTestId('terminal-pin-panel')).toBeInTheDocument();
  });

  it('closes on Escape and clears the PIN', () => {
    render(<TerminalPinPad />);
    fireEvent.click(screen.getByTestId('terminal-switch-open'));
    fireEvent.click(screen.getByTestId('terminal-pin-keys').querySelector('[data-pin-key="1"]')!);
    expect(screen.getByTestId('terminal-pin-dots').querySelectorAll('.bg-text')).toHaveLength(1);

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByTestId('terminal-pin-pad')).not.toBeInTheDocument();
  });
});
