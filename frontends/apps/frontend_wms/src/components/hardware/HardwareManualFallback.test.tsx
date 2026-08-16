import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HardwareManualFallback } from './HardwareManualFallback';

describe('HardwareManualFallback', () => {
  it('hides connect buttons and shows a typed fallback when APIs are missing', async () => {
    const onManualSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <HardwareManualFallback
        isSupported={false}
        mode="scan"
        onConnectBluetooth={() => undefined}
        onConnectSerial={() => undefined}
        onManualSubmit={onManualSubmit}
      />,
    );

    expect(screen.queryByRole('button', { name: /connect bluetooth scale/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /connect usb scanner/i })).toBeNull();
    await user.type(screen.getByLabelText('Manual scan'), 'SKU-99{Enter}');
    expect(onManualSubmit).toHaveBeenCalledWith('SKU-99');
  });

  it('shows connect actions only when the browser supports the APIs', () => {
    render(
      <HardwareManualFallback
        isSupported
        mode="weight"
        bluetoothSupported
        serialSupported
        onConnectBluetooth={() => undefined}
        onConnectSerial={() => undefined}
      />,
    );

    expect(screen.getByRole('button', { name: /connect bluetooth scale/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /connect usb scanner/i })).toBeTruthy();
    expect(screen.getByTestId('hardware-manual-fallback')).toBeTruthy();
  });
});
