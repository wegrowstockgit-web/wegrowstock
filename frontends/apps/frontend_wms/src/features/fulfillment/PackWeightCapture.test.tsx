import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PackWeightCapture } from './PackWeightCapture';

describe('PackWeightCapture', () => {
  it('locks the input while the scale is connected and fills a stable weight', () => {
    const onChange = vi.fn();
    render(
      <PackWeightCapture
        scaleConnected
        stableWeightLb={12.5}
        value=""
        onChange={onChange}
      />,
    );
    const input = screen.getByTestId('pack-weight-input');
    expect(input).toHaveAttribute('readOnly');
    expect(onChange).toHaveBeenCalledWith('12.50');
  });

  it('unlocks typing after the manual override toggle', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <PackWeightCapture
        scaleConnected
        stableWeightLb={8}
        value="8.00"
        onChange={onChange}
      />,
    );
    await user.click(screen.getByTestId('pack-weight-manual-override'));
    expect(screen.getByTestId('pack-weight-input')).not.toHaveAttribute('readOnly');
    expect(screen.getByTestId('pack-weight-manual-override')).toHaveAttribute('aria-pressed', 'true');
  });

  it('unlocks typing when the scale disconnects', () => {
    const { rerender } = render(
      <PackWeightCapture scaleConnected stableWeightLb={4} value="4.00" onChange={() => undefined} />,
    );
    expect(screen.getByTestId('pack-weight-input')).toHaveAttribute('readOnly');
    rerender(
      <PackWeightCapture
        scaleConnected={false}
        stableWeightLb={null}
        value="4.00"
        onChange={() => undefined}
      />,
    );
    expect(screen.getByTestId('pack-weight-input')).not.toHaveAttribute('readOnly');
  });
});
