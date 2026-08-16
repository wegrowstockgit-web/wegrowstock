import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { WebRtcCamera } from '@/components/ui/WebRtcCamera';

vi.mock('@/lib/barcodeCameraDecode', () => ({
  decodeBarcodeFromVideo: vi.fn().mockResolvedValue('CAM-SKU-9'),
}));

function mockStream() {
  const track = { stop: vi.fn() };
  return {
    getTracks: () => [track],
  } as unknown as MediaStream;
}

describe('WebRtcCamera', () => {
  beforeEach(() => {
    Object.defineProperty(HTMLMediaElement.prototype, 'play', {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts the camera and emits a decoded barcode', async () => {
    const stream = mockStream();
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const onBarcode = vi.fn();
    render(<WebRtcCamera onBarcode={onBarcode} autoStart />);
    expect(screen.getByTestId('webrtc-camera')).toBeInTheDocument();
    await waitFor(() => {
      expect(onBarcode).toHaveBeenCalledWith('CAM-SKU-9');
    });
  });

  it('closes the preview from the toolbar', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(mockStream()) },
    });
    const onCancel = vi.fn();
    render(<WebRtcCamera onBarcode={vi.fn()} onCancel={onCancel} autoStart />);
    fireEvent.click(screen.getByTestId('webrtc-camera-close'));
    expect(onCancel).toHaveBeenCalled();
  });

  it('keeps the view open and shows an error when the camera API is missing', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: undefined,
    });
    const onCancel = vi.fn();
    render(<WebRtcCamera onBarcode={vi.fn()} onCancel={onCancel} autoStart />);
    expect(await screen.findByTestId('webrtc-camera-error')).toHaveTextContent(/keyboard entry/i);
    expect(onCancel).not.toHaveBeenCalled();
  });
});
