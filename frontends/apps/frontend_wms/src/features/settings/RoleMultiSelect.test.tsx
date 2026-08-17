import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleMultiSelect } from '@/features/settings/RoleMultiSelect';

describe('RoleMultiSelect', () => {
  it('checks and unchecks additive roles', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { rerender } = render(<RoleMultiSelect value={['VIEWER']} onChange={onChange} />);

    await user.click(screen.getByTestId('role-option-PICKER').querySelector('input')!);
    expect(onChange).toHaveBeenCalledWith(['VIEWER', 'PICKER']);

    rerender(<RoleMultiSelect value={['VIEWER', 'PICKER']} onChange={onChange} />);
    await user.click(screen.getByTestId('role-option-VIEWER').querySelector('input')!);
    expect(onChange).toHaveBeenCalledWith(['PICKER']);
  });

  it('keeps OWNER locked when included', () => {
    render(<RoleMultiSelect value={['OWNER']} onChange={vi.fn()} includeCodes={['OWNER']} />);
    expect(screen.getByTestId('role-option-OWNER').querySelector('input')).toBeDisabled();
  });
});
