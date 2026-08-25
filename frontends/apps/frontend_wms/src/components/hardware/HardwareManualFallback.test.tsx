import { describe, expect, it, beforeEach, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HardwareManualFallback } from './HardwareManualFallback';
import { useHardwareStore } from '@/stores/hardwareStore';

describe('HardwareManualFallback', () => {
  beforeEach(() => {
    useHardwareStore.getState().resetHardwareStore();
  });

  it('opens keyboard entry then submits a typed scan', async () => {
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
    await user.click(screen.getByTestId('scanner-keyboard-entry'));
    const input = screen.getByTestId('scanner-manual-input');
    expect(input).toHaveFocus();
    await user.type(input, 'SKU-99{Enter}');
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

  it('reopens the weight field when the hardware store reports a scale drop', () => {
    useHardwareStore.getState().setBluetoothScale({ connected: true, deviceName: 'Scale' });
    render(
      <HardwareManualFallback isSupported mode="weight" bluetoothSupported serialSupported />,
    );
    expect(screen.getByTestId('hardware-manual-fallback')).toHaveAttribute('data-scale-linked', 'true');

    act(() => {
      useHardwareStore.getState().setBluetoothScale({ connected: false, deviceName: null });
    });
    expect(screen.getByTestId('hardware-manual-fallback')).toHaveAttribute('data-scale-linked', 'false');
    expect(screen.getByLabelText(/manual weight/i)).toBeTruthy();
  });
});
