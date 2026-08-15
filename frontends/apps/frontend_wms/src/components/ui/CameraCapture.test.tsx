import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CameraCapture } from '@/components/ui/CameraCapture';

function mockStream() {
  const track = { stop: vi.fn() };
  return {
    getTracks: () => [track],
  } as unknown as MediaStream;
}

describe('CameraCapture', () => {
  beforeEach(() => {
    Object.defineProperty(HTMLMediaElement.prototype, 'play', {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    });
    Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
      configurable: true,
      value: vi.fn(() => ({
        drawImage: vi.fn(),
      })),
    });
    Object.defineProperty(HTMLCanvasElement.prototype, 'toBlob', {
      configurable: true,
      value: function toBlob(cb: (b: Blob | null) => void) {
        cb(new Blob(['jpeg'], { type: 'image/jpeg' }));
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('opens live preview via getUserMedia and snaps a JPEG file', async () => {
    const stream = mockStream();
    const getUserMedia = vi.fn().mockResolvedValue(stream);
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia },
    });

    const onCapture = vi.fn();
    render(<CameraCapture onCapture={onCapture} label="Take photo" facingMode="user" />);

    fireEvent.click(screen.getByTestId('camera-capture-open'));
    await waitFor(() => {
      expect(screen.getByTestId('camera-capture-live')).toBeInTheDocument();
    });
    expect(getUserMedia).toHaveBeenCalled();

    const video = screen.getByTestId('camera-capture-preview') as HTMLVideoElement;
    Object.defineProperty(video, 'videoWidth', { configurable: true, value: 640 });
    Object.defineProperty(video, 'videoHeight', { configurable: true, value: 480 });

    fireEvent.click(screen.getByTestId('camera-capture-snap'));
    await waitFor(() => {
      expect(onCapture).toHaveBeenCalled();
    });
    const file = onCapture.mock.calls[0][0] as File;
    expect(file.type).toBe('image/jpeg');
    expect(file.name).toMatch(/^capture-/);
  });

  it('surfaces an error when camera permission is denied', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn().mockRejectedValue(new Error('denied')),
      },
    });

    render(<CameraCapture onCapture={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: /take photo/i }));
    await waitFor(() => {
      expect(screen.getByTestId('camera-capture-error')).toBeInTheDocument();
    });
  });
});
