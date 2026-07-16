import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { uploadViaPresign } from '@/lib/mediaPresign';

vi.mock('@/lib/mediaPresign', () => ({
  uploadViaPresign: vi.fn(async () => ({
    id: 'media-1',
    contentUrl: '/api/v1/media/media-1/content',
    contentType: 'image/webp',
    byteSize: 42,
  })),
}));

vi.mock('@/api/client', () => ({
  apiClient: {
    put: vi.fn(async () => ({ data: {} })),
    post: vi.fn(async () => ({ data: {} })),
  },
}));

vi.mock('@/components/ui/CameraCapture', () => ({
  CameraCapture: ({
    onCapture,
    label,
  }: {
    onCapture: (file: File) => void;
    label?: string;
  }) => (
    <button
      type="button"
      data-testid="camera-capture-open"
      onClick={() =>
        onCapture(new File(['x'], 'cam.jpg', { type: 'image/jpeg' }))
      }
    >
      {label ?? 'Take photo'}
    </button>
  ),
}));

describe('MediaPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn(),
    });
  });

  it('shows Take photo when capture is enabled and uploads via S3 presign', async () => {
    const onUploaded = vi.fn();

    render(
      <MediaPicker kind="AVATAR" label="Upload photo" capture onUploaded={onUploaded} />,
    );

    expect(screen.getByRole('button', { name: /upload photo/i })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('camera-capture-open'));

    await waitFor(() => {
      expect(uploadViaPresign).toHaveBeenCalled();
      expect(onUploaded).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 'media-1',
          contentUrl: '/api/v1/media/media-1/content',
        }),
      );
    });
  });
});
