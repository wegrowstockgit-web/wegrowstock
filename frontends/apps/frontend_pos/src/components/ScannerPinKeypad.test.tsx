import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ScannerPinKeypad } from './ScannerPinKeypad';

describe('ScannerPinKeypad', () => {
  it('enters digits, backspaces, and ignores extra presses', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(
      <ScannerPinKeypad value="" onChange={onChange} title="Manager PIN" subtitle="4 digits" />,
    );
    await user.click(screen.getByTestId('scanner-pin-digit-9'));
    expect(onChange).toHaveBeenCalledWith('9');
    rerender(<ScannerPinKeypad value="1234" onChange={onChange} title="Manager PIN" />);
    await user.click(screen.getByTestId('scanner-pin-digit-5'));
    expect(onChange).toHaveBeenLastCalledWith('9');
    await user.click(screen.getByTestId('scanner-pin-back'));
    expect(onChange).toHaveBeenCalledWith('123');
  });
});
