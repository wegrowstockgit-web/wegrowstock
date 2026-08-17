import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { cacheManagerPins, hashManagerPin } from '@/offline/pinVault';
import { VoidConfirmModal } from './VoidConfirmModal';

describe('VoidConfirmModal', () => {
  it('keeps confirm disabled until a valid manager PIN is entered', async () => {
    cacheManagerPins('t1', [{ managerId: 'mgr-1', pinHash: hashManagerPin('t1', '1234') }]);
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <VoidConfirmModal
        open
        cartValueLabel="Cart $10.00"
        title="Void?"
        body="Need PIN"
        pinTitle="Manager PIN"
        pinHint="4 digits"
        invalidPin="Invalid manager PIN"
        confirmLabel="Yes, Void Transaction"
        cancelLabel="Keep sale"
        onCancel={vi.fn()}
        onConfirm={onConfirm}
      />,
    );

    const confirm = screen.getByTestId('void-confirm-yes');
    expect(confirm).toBeDisabled();
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-2'));
    await user.click(screen.getByTestId('scanner-pin-digit-3'));
    await user.click(screen.getByTestId('scanner-pin-digit-4'));
    expect(confirm).toBeEnabled();
    await user.click(confirm);
    expect(onConfirm).toHaveBeenCalledWith('mgr-1');
  });

  it('cancels without voiding', async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();
    render(
      <VoidConfirmModal
        open
        cartValueLabel="Cart $10.00"
        title="Void?"
        body="Need PIN"
        pinTitle="Manager PIN"
        pinHint="4 digits"
        invalidPin="Invalid manager PIN"
        confirmLabel="Yes, Void Transaction"
        cancelLabel="Keep sale"
        onCancel={onCancel}
        onConfirm={vi.fn()}
      />,
    );
    await user.click(screen.getByTestId('void-confirm-cancel'));
    expect(onCancel).toHaveBeenCalled();
  });
});
