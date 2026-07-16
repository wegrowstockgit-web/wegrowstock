import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ScannerView, ReceiveQcPhotoSlot, type Gs1FieldState } from '@/features/fulfillment/ScannerView';
import { apiClient } from '@/api/client';
import { uploadViaPresign } from '@/lib/mediaPresign';
import { compressImageForUpload } from '@/utils/imageCompression';

vi.mock('@/api/client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

vi.mock('@/lib/mediaPresign', () => ({
  uploadViaPresign: vi.fn(),
}));

vi.mock('@/utils/imageCompression', () => ({
  compressImageForUpload: vi.fn(async (file: File) => file),
}));

vi.mock('@/components/ui/MediaPicker', () => ({
  MediaPicker: ({
    label,
    onUploaded,
  }: {
    label: string;
    onUploaded?: (result: { id: string; contentUrl: string }) => Promise<void> | void;
  }) => (
    <button
      type="button"
      data-testid="media-picker"
      onClick={() =>
        void onUploaded?.({ id: 'media-qc-1', contentUrl: '/api/v1/media/media-qc-1/content' })
      }
    >
      {label}
    </button>
  ),
}));

vi.mock('@/components/ui/CameraCapture', () => ({
  CameraCapture: ({
    label,
    onCapture,
    disabled,
  }: {
    label: string;
    onCapture: (file: File) => void;
    disabled?: boolean;
  }) => (
    <button
      type="button"
      data-testid="camera-capture-mock"
      disabled={disabled}
      onClick={() => onCapture(new File(['cam'], 'cam.jpg', { type: 'image/jpeg' }))}
    >
      {label}
    </button>
  ),
}));

describe('ScannerView GS1 fields', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders and edits Lot / Expiry / Qty when GS1 is active', () => {
    const onChange = vi.fn();
    const fields: Gs1FieldState = {
      lotNumber: 'LOT-1',
      expiryDate: '2025-12-31',
      quantity: '4',
    };

    render(
      <ScannerView
        lastScan="01234567890128"
        lastThumbUrl={null}
        history={[]}
        scanning={false}
        mode="receive"
        onThumbCaptured={vi.fn()}
        gs1Active
        gs1Fields={fields}
        onGs1FieldsChange={onChange}
      />,
    );

    expect(screen.getByTestId('gs1-fields-card')).toBeInTheDocument();
    expect(screen.getByTestId('gs1-lot')).toHaveDisplayValue('LOT-1');
    expect(screen.getByTestId('gs1-expiry')).toHaveDisplayValue('2025-12-31');
    expect(screen.getByTestId('gs1-qty')).toHaveDisplayValue('4');

    fireEvent.change(screen.getByTestId('gs1-lot'), { target: { value: 'LOT-2' } });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ lotNumber: 'LOT-2', expiryDate: '2025-12-31', quantity: '4' }),
    );
  });

  it('hides GS1 card when inactive', () => {
    render(
      <ScannerView
        lastScan="SKU-1"
        lastThumbUrl={null}
        history={[]}
        scanning={false}
        mode="pick"
        onThumbCaptured={vi.fn()}
        gs1Active={false}
      />,
    );
    expect(screen.queryByTestId('gs1-fields-card')).not.toBeInTheDocument();
  });

  it('shows history rows with GS1 metadata', () => {
    render(
      <ScannerView
        lastScan="01234567890128"
        lastThumbUrl={null}
        history={[
          {
            barcode: '01234567890128',
            sku: 'WIDGET-S',
            name: 'Widget',
            success: true,
            message: 'Received 4 unit(s)',
            lotNumber: 'BATCH-E2E',
            expiryDate: '2025-12-31',
            quantity: 4,
            putawayTarget: '/WH/Z/A/B1',
            timestamp: Date.now(),
          },
        ]}
        scanning={false}
        mode="receive"
        onThumbCaptured={vi.fn()}
      />,
    );
    expect(screen.getByText(/Lot BATCH-E2E/)).toBeInTheDocument();
    expect(screen.getByText(/Putaway target/i)).toBeInTheDocument();
  });

  it('shows glove-ready Skip & Flag button when lot extraction failed', () => {
    const onSkip = vi.fn();
    render(
      <ScannerView
        lastScan="01234567890128"
        lastThumbUrl={null}
        history={[]}
        scanning={false}
        mode="pick"
        onThumbCaptured={vi.fn()}
        showSkipFlag
        onSkipFlag={onSkip}
      />,
    );
    const btn = screen.getByTestId('skip-flag-barcode');
    expect(btn).toHaveTextContent(/Skip/);
    expect(btn.className).toMatch(/min-h-14/);
    fireEvent.click(btn);
    expect(onSkip).toHaveBeenCalled();
  });

  it('applies success enter and error shake classes from feedbackFlash', () => {
    const { rerender } = render(
      <ScannerView
        lastScan="SKU-OK"
        lastThumbUrl={null}
        history={[
          {
            barcode: 'SKU-OK',
            sku: 'SKU-OK',
            success: true,
            message: 'Picked',
            timestamp: 1,
          },
        ]}
        scanning={false}
        mode="pick"
        onThumbCaptured={vi.fn()}
        feedbackFlash="success"
      />,
    );
    expect(screen.getByTestId('scan-verification-deck').className).toMatch(/scan-success-enter/);

    rerender(
      <ScannerView
        lastScan="BAD"
        lastThumbUrl={null}
        history={[
          {
            barcode: 'BAD',
            success: false,
            message: 'Not found',
            timestamp: 2,
          },
        ]}
        scanning={false}
        mode="pick"
        onThumbCaptured={vi.fn()}
        feedbackFlash="error"
      />,
    );
    expect(screen.getByTestId('scan-verification-deck').className).toMatch(/scan-error-shake/);
  });

  it('captures product image via compress + presign path', async () => {
    vi.mocked(uploadViaPresign).mockResolvedValue({
      id: 'm1',
      contentUrl: '/api/v1/media/m1/content',
      contentType: 'image/webp',
      byteSize: 12,
    } as never);
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);
    const onThumb = vi.fn();

    render(
      <ScannerView
        lastScan="SKU-1"
        lastThumbUrl={null}
        history={[
          {
            barcode: 'SKU-1',
            variantId: 'v-1',
            sku: 'SKU-1',
            success: true,
            message: 'Picked',
            timestamp: Date.now(),
          },
        ]}
        scanning={false}
        mode="pick"
        onThumbCaptured={onThumb}
      />,
    );

    fireEvent.click(screen.getByTestId('camera-capture-mock'));

    await waitFor(() => {
      expect(compressImageForUpload).toHaveBeenCalled();
      expect(uploadViaPresign).toHaveBeenCalled();
      expect(onThumb).toHaveBeenCalledWith('/api/v1/media/m1/content', 'v-1');
    });
  });

  it('opens gallery file input from Upload from gallery', async () => {
    vi.mocked(uploadViaPresign).mockResolvedValue({
      id: 'm2',
      contentUrl: '/api/v1/media/m2/content',
      contentType: 'image/webp',
      byteSize: 12,
    } as never);
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);

    render(
      <ScannerView
        lastScan="SKU-1"
        lastThumbUrl={null}
        history={[
          {
            barcode: 'SKU-1',
            variantId: 'v-1',
            sku: 'SKU-1',
            success: true,
            message: 'Picked',
            timestamp: Date.now(),
          },
        ]}
        scanning={false}
        mode="pick"
        onThumbCaptured={vi.fn()}
      />,
    );

    const input = screen.getByTestId('capture-product-image').querySelector('input[type="file"]')!;
    const clickSpy = vi.spyOn(input, 'click');
    fireEvent.click(screen.getByRole('button', { name: /upload from gallery/i }));
    expect(clickSpy).toHaveBeenCalled();

    const file = new File(['img'], 'shot.jpg', { type: 'image/jpeg' });
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);
    await waitFor(() => {
      expect(uploadViaPresign).toHaveBeenCalled();
    });
  });

  it('triggers error feedback when capture upload fails', async () => {
    vi.mocked(uploadViaPresign).mockRejectedValue(new Error('network'));
    render(
      <ScannerView
        lastScan="SKU-1"
        lastThumbUrl={null}
        history={[
          {
            barcode: 'SKU-1',
            variantId: 'v-1',
            sku: 'SKU-1',
            success: true,
            message: 'Picked',
            timestamp: Date.now(),
          },
        ]}
        scanning={false}
        mode="pick"
        onThumbCaptured={vi.fn()}
      />,
    );
    const file = new File(['img'], 'shot.jpg', { type: 'image/jpeg' });
    const input = screen.getByTestId('capture-product-image').querySelector('input[type="file"]')!;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);
    await waitFor(() => {
      expect(uploadViaPresign).toHaveBeenCalled();
    });
  });

  it('renders receive QC photo slot', () => {
    render(<ReceiveQcPhotoSlot variantId="v-9" />);
    expect(screen.getByTestId('receive-qc-photo')).toBeInTheDocument();
    expect(screen.getByTestId('media-picker')).toHaveTextContent(/QC/);
  });

  it('posts QC media attachments when ReceiveQcPhotoSlot uploads', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);
    const onDone = vi.fn();
    render(<ReceiveQcPhotoSlot variantId="v-9" onDone={onDone} />);
    fireEvent.click(screen.getByTestId('media-picker'));
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/media/transactions',
        expect.objectContaining({ entityType: 'RECEIPT', entityId: 'v-9' }),
      );
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/media/attachments',
        expect.objectContaining({
          mediaObjectId: 'media-qc-1',
          entityType: 'PRODUCT_VARIANT',
          purpose: 'QC_DAMAGE',
        }),
      );
      expect(onDone).toHaveBeenCalled();
    });
  });

  it('shows history lot-logged badge when tracking is disabled', () => {
    render(
      <ScannerView
        lastScan="SKU-1"
        lastThumbUrl={null}
        history={[
          {
            barcode: 'SKU-1',
            sku: 'SKU-1',
            success: true,
            message: 'Received',
            lotLoggedNotTracked: true,
            timestamp: Date.now(),
          },
        ]}
        scanning={false}
        mode="receive"
        onThumbCaptured={vi.fn()}
      />,
    );
    expect(screen.getByTestId('history-lot-logged-badge')).toBeInTheDocument();
  });

  it('shows non-blocking lot-logged badge when tracking is disabled', () => {
    const fields: Gs1FieldState = {
      lotNumber: 'LOT123',
      expiryDate: '',
      quantity: '1',
    };
    render(
      <ScannerView
        lastScan="01234567890128"
        lastThumbUrl={null}
        history={[]}
        scanning={false}
        mode="receive"
        onThumbCaptured={vi.fn()}
        gs1Active
        gs1Fields={fields}
        onGs1FieldsChange={vi.fn()}
        lotLoggedNotTracked
      />,
    );
    expect(screen.getByTestId('lot-logged-badge')).toHaveTextContent('Lot Data Logged (Not Tracked)');
  });
});


