import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToastProvider } from '@/components/ui/Toast';
import { DeviceManagerModal } from './DeviceManagerModal';
import { UNSUPPORTED_BROWSER_HARDWARE_MESSAGE } from '@/lib/hardwareCapabilities';
import { useHardwareStore } from '@/stores/hardwareStore';

const { scaleMock, printMock, caps } = vi.hoisted(() => ({
  caps: { serial: false, bluetooth: false },
  scaleMock: {
    supported: false,
    isSupported: false,
    bluetoothSupported: false,
    serialSupported: false,
    connected: false,
    bluetoothDeviceName: null as string | null,
    transport: null as 'bluetooth' | 'serial' | null,
    reading: null,
    error: null as string | null,
    connecting: false,
    connectBluetooth: vi.fn(async () => true),
    connectSerial: vi.fn(async () => true),
    disconnect: vi.fn(),
  },
  printMock: {
    agentStatus: 'disconnected' as 'disconnected' | 'connected' | 'connecting' | 'error',
    connectAgent: vi.fn(async () => true),
  },
}));

vi.mock('@/lib/hardwareCapabilities', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/hardwareCapabilities')>();
  return {
    ...actual,
    supportsWebSerial: () => caps.serial,
    supportsWebBluetooth: () => caps.bluetooth,
  };
});

vi.mock('@/hooks/useDigitalScale', () => ({
  useDigitalScale: () => scaleMock,
}));

vi.mock('@/stores/usePrintStore', () => ({
  usePrintStore: (sel: (s: typeof printMock) => unknown) => sel(printMock),
}));

function renderModal() {
  return render(
    <ToastProvider>
      <DeviceManagerModal />
    </ToastProvider>,
  );
}

describe('DeviceManagerModal', () => {
  beforeEach(() => {
    useHardwareStore.getState().resetHardwareStore();
    useHardwareStore.getState().openManager();
    caps.serial = false;
    caps.bluetooth = false;
    scaleMock.connected = false;
    scaleMock.transport = null;
    scaleMock.bluetoothDeviceName = null;
    scaleMock.connectSerial.mockReset().mockImplementation(async () => {
      scaleMock.connected = true;
      scaleMock.transport = 'serial';
      return true;
    });
    scaleMock.connectBluetooth.mockReset().mockImplementation(async () => {
      scaleMock.connected = true;
      scaleMock.transport = 'bluetooth';
      return true;
    });
    scaleMock.disconnect.mockReset();
    printMock.agentStatus = 'disconnected';
    printMock.connectAgent.mockReset().mockResolvedValue(true);
  });

  it('hides scale Connect buttons and shows the Chrome/Edge warning when APIs are missing', () => {
    renderModal();

    expect(screen.getByTestId('device-manager-modal')).toBeTruthy();
    expect(screen.getByTestId('device-manager-unsupported-alert').textContent).toBe(
      UNSUPPORTED_BROWSER_HARDWARE_MESSAGE,
    );
    expect(screen.queryByTestId('device-connect-bluetooth-scale')).toBeNull();
    expect(screen.queryByTestId('device-connect-serial-scale')).toBeNull();
    expect(screen.getByTestId('device-connect-qz-tray')).toBeTruthy();
    expect(screen.getByTestId('device-row-bluetooth-scale')).toHaveAttribute('data-connected', 'false');
    expect(screen.getByTestId('device-row-serial-scale')).toHaveAttribute('data-connected', 'false');
    expect(screen.getByTestId('device-row-qz-tray')).toHaveAttribute('data-connected', 'false');
    expect(screen.queryByText(/barcode scanners? do not/i)).toBeNull();
  });

  it('shows Connect Device for USB and Bluetooth when the browser supports the APIs', () => {
    caps.serial = true;
    caps.bluetooth = true;
    renderModal();

    expect(screen.queryByTestId('device-manager-unsupported-alert')).toBeNull();
    expect(screen.getByTestId('device-connect-bluetooth-scale')).toHaveTextContent('Connect Device');
    expect(screen.getByTestId('device-connect-serial-scale')).toHaveTextContent('Connect Device');
    expect(screen.getByTestId('device-connect-qz-tray')).toHaveTextContent('Connect Device');
  });

  it('keeps Bluetooth Connect when only Web Serial is missing', () => {
    caps.serial = false;
    caps.bluetooth = true;
    renderModal();
    expect(screen.getByTestId('device-manager-unsupported-alert')).toBeTruthy();
    expect(screen.getByTestId('device-connect-bluetooth-scale')).toBeTruthy();
    expect(screen.queryByTestId('device-connect-serial-scale')).toBeNull();
  });

  it('does not toast when the USB chooser is cancelled', async () => {
    const user = userEvent.setup();
    caps.serial = true;
    caps.bluetooth = true;
    scaleMock.connectSerial.mockImplementation(async () => false);
    renderModal();

    await user.click(screen.getByTestId('device-connect-serial-scale'));

    expect(scaleMock.connectSerial).toHaveBeenCalled();
    expect(screen.queryByTestId('app-toast')).toBeNull();
  });

  it('toasts a Bluetooth pairing failure', async () => {
    const user = userEvent.setup();
    caps.serial = true;
    caps.bluetooth = true;
    scaleMock.connectBluetooth.mockRejectedValue(new Error('GATT failed'));
    renderModal();

    await user.click(screen.getByTestId('device-connect-bluetooth-scale'));

    await waitFor(() =>
      expect(screen.getByTestId('app-toast').textContent).toMatch(/GATT failed/i),
    );
  });

  it('pairs a USB scale through Web Serial and toasts success', async () => {
    const user = userEvent.setup();
    caps.serial = true;
    caps.bluetooth = true;
    renderModal();

    await user.click(screen.getByTestId('device-connect-serial-scale'));

    expect(scaleMock.connectSerial).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(useHardwareStore.getState().serialScale.connected).toBe(true));
    expect(screen.getByTestId('app-toast').textContent).toMatch(/USB scale connected/i);
  });

  it('pairs a Bluetooth scale through Web Bluetooth and toasts success', async () => {
    const user = userEvent.setup();
    caps.serial = true;
    caps.bluetooth = true;
    scaleMock.bluetoothDeviceName = 'Acme Deck Scale';
    renderModal();

    await user.click(screen.getByTestId('device-connect-bluetooth-scale'));

    expect(scaleMock.connectBluetooth).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(useHardwareStore.getState().bluetoothScale.connected).toBe(true));
    expect(screen.getByTestId('app-toast').textContent).toMatch(/Bluetooth scale connected/i);
  });

  it('connects QZ Tray without showing the scale browser warning', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByTestId('device-connect-qz-tray'));

    expect(printMock.connectAgent).toHaveBeenCalledTimes(1);
    expect(useHardwareStore.getState().qzTray).toEqual({ connected: true, version: 'QZ Tray' });
    expect(screen.getByTestId('app-toast').textContent).toMatch(/QZ Tray connected/i);
  });

  it('reports QZ Tray failure without treating it as a scale warning', async () => {
    const user = userEvent.setup();
    printMock.connectAgent.mockResolvedValue(false);
    renderModal();

    await user.click(screen.getByTestId('device-connect-qz-tray'));

    expect(screen.getByTestId('app-toast').textContent).toMatch(/Could not reach QZ Tray/i);
    expect(screen.queryByTestId('device-manager-unsupported-alert')).toBeTruthy();
  });

  it('drops the live scale session when the store requests disconnect', async () => {
    caps.serial = true;
    caps.bluetooth = true;
    renderModal();
    await act(async () => {
      useHardwareStore.getState().requestScaleDisconnect();
    });
    await waitFor(() => expect(scaleMock.disconnect).toHaveBeenCalled());
  });
});
