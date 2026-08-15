import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { BarcodeScannerInput } from './BarcodeScannerInput';

vi.mock('@/hooks/useBarcodeScanner', () => ({
  useBarcodeScanner: () => undefined,
}));

describe('BarcodeScannerInput', () => {
  it('submits manual barcode entry', () => {
    const onScan = vi.fn();
    render(<BarcodeScannerInput label="Scan PO or ASN" onScan={onScan} lastScan={null} />);
    expect(screen.getByTestId('barcode-scanner-input')).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('scanner-manual-input'), { target: { value: 'PO-100' } });
    fireEvent.submit(screen.getByTestId('scanner-manual-input').closest('form')!);
    expect(onScan).toHaveBeenCalledWith('PO-100');
  });
});
