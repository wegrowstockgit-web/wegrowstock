import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ScannerView, type Gs1FieldState } from '@/features/fulfillment/ScannerView';

describe('ScannerView GS1 fields', () => {
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
});

