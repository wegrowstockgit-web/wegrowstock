import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BarcodeScannerInput } from './BarcodeScannerInput';

vi.mock('@/lib/barcodeCameraDecode', () => ({
  decodeBarcodeFromVideo: vi.fn().mockResolvedValue('CAM-SKU-9'),
}));


describe('BarcodeScannerInput', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'serial');
  });

  it('shows a camera CTA and keyboard entry when hardware is unsupported', () => {
    render(<BarcodeScannerInput label="Scan PO or ASN" onScan={vi.fn()} lastScan={null} />);
    expect(screen.getByTestId('barcode-scanner-input')).toHaveAttribute(
      'data-hardware-status',
      'UNSUPPORTED',
    );
    expect(screen.getByTestId('scanner-status-badge')).toHaveTextContent(/no hardware scanner/i);
    expect(screen.getByTestId('scanner-camera-trigger')).toBeInTheDocument();
    expect(screen.getByTestId('scanner-keyboard-entry')).toBeInTheDocument();
  });

  it('submits keyboard entry through the same scan handler', () => {
    const onScan = vi.fn();
    render(<BarcodeScannerInput label="Scan PO or ASN" onScan={onScan} lastScan={null} />);
    fireEvent.click(screen.getByTestId('scanner-keyboard-entry'));
    fireEvent.change(screen.getByTestId('scanner-manual-input'), { target: { value: 'PO-100' } });
    fireEvent.submit(screen.getByTestId('scanner-manual-input').closest('form')!);
    expect(onScan).toHaveBeenCalledWith('PO-100');
  });

  it('shows Scanner Ready when Web Serial is present and a hardware scan arrives', () => {
    Object.defineProperty(navigator, 'serial', { configurable: true, value: {} });
    const onScan = vi.fn();
    render(<BarcodeScannerInput label="Scan item" onScan={onScan} />);
    expect(screen.getByTestId('scanner-status-badge')).toHaveTextContent(/scanner disconnected/i);
    fireEvent(window, new CustomEvent('hardwareScan', { detail: { barcode: 'SKU-HW' } }));
    expect(screen.getByTestId('scanner-status-badge')).toHaveTextContent(/scanner ready/i);
    expect(onScan).toHaveBeenCalledWith('SKU-HW');
  });

  it('opens the device camera, ingests a decode, then closes the preview', async () => {
    Object.defineProperty(HTMLMediaElement.prototype, 'play', {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    });
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }),
      },
    });
    const onScan = vi.fn();
    render(<BarcodeScannerInput label="Scan item" onScan={onScan} />);
    fireEvent.click(screen.getByTestId('scanner-camera-trigger'));
    expect(screen.getByTestId('webrtc-camera')).toBeInTheDocument();
    expect(screen.getByTestId('scanner-keyboard-entry')).toBeInTheDocument();
    await waitFor(() => {
      expect(onScan).toHaveBeenCalledWith('CAM-SKU-9');
    });
    expect(screen.queryByTestId('webrtc-camera')).not.toBeInTheDocument();
  });
});
