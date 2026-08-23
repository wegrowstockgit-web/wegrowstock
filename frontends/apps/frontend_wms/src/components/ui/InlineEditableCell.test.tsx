import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InlineEditableCell } from './InlineEditableCell';

describe('InlineEditableCell', () => {
  it('shows a dashed affordance when editable and opens on click', async () => {
    const onSave = vi.fn();
    render(<InlineEditableCell testId="cell" value={4} onSave={onSave} />);
    const button = screen.getByTestId('cell');
    expect(button.className).toMatch(/border-dashed/);
    await userEvent.click(button);
    expect(screen.getByTestId('cell-input')).toBeInTheDocument();
  });

  it('does not show the dashed affordance when disabled', () => {
    render(<InlineEditableCell testId="cell" value={4} onSave={vi.fn()} disabled />);
    expect(screen.getByTestId('cell').className).not.toMatch(/border-dashed/);
  });
});
