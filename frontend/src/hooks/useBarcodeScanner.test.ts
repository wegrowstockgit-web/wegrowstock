import { describe, expect, it } from 'vitest';
import { extractIntentBarcode } from '@/hooks/useBarcodeScanner';

describe('extractIntentBarcode', () => {
  it('reads DataWedge data_string extras', () => {
    expect(
      extractIntentBarcode({
        extras: { 'com.symbol.datawedge.data_string': 'SKU-12345' },
      })
    ).toBe('SKU-12345');
  });

  it('reads Honeywell / Capacitor barcode fields', () => {
    expect(extractIntentBarcode({ barcode: 'LOT-99' })).toBe('LOT-99');
    expect(extractIntentBarcode({ data: '  PACK-1  ' })).toBe('PACK-1');
    expect(extractIntentBarcode('RAW-CODE')).toBe('RAW-CODE');
  });

  it('returns null for empty payloads', () => {
    expect(extractIntentBarcode(null)).toBeNull();
    expect(extractIntentBarcode({})).toBeNull();
    expect(extractIntentBarcode('   ')).toBeNull();
  });
});
