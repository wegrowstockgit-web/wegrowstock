import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { demoSession } from '@/lib/posSession';
import { PosSessionProvider } from '@/lib/PosSessionContext';
import { seedDemoManagerPinsIfEmpty } from '@/offline/pinVault';
import { lockShift, ScannerSecurityGate } from './ScannerSecurityGate';

function GateProbe() {
  return (
    <PosSessionProvider initial={demoSession()} disableFetch>
      <ScannerSecurityGate>
        <div data-testid="register-page">register</div>
      </ScannerSecurityGate>
    </PosSessionProvider>
  );
}

describe('ScannerSecurityGate', () => {
  afterEach(() => {
    lockShift();
  });

  it('blocks the register until the cashier PIN is valid', async () => {
    lockShift();
    seedDemoManagerPinsIfEmpty();
    const user = userEvent.setup();
    render(<GateProbe />);
    expect(screen.getByTestId('pos-pin-gate')).toBeTruthy();
    expect(screen.queryByTestId('register-page')).toBeNull();

    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    expect(await screen.findByTestId('pos-pin-error')).toBeTruthy();

    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-2'));
    await user.click(screen.getByTestId('scanner-pin-digit-3'));
    await user.click(screen.getByTestId('scanner-pin-digit-4'));
    expect(await screen.findByTestId('register-page')).toBeTruthy();
    expect(screen.queryByTestId('pos-pin-gate')).toBeNull();
  });
});
