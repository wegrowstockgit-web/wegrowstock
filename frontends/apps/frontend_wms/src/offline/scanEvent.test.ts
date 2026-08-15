import { describe, expect, it, vi } from 'vitest';
import { createScanEventPayload, highResTimestamp } from './scanEvent';

describe('scanEvent', () => {
  it('builds ScanEventPayload with UUIDv4 idempotencyKey and high-res scannedAt', () => {
    const uuidSpy = vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-2222-4333-8444-555555555555');
    const before = highResTimestamp();
    const event = createScanEventPayload('SKU-99');
    const after = highResTimestamp();

    expect(event.barcode).toBe('SKU-99');
    expect(event.idempotencyKey).toBe('11111111-2222-4333-8444-555555555555');
    expect(event.scannedAt).toBeGreaterThanOrEqual(before);
    expect(event.scannedAt).toBeLessThanOrEqual(after);
    expect(event.rawBarcode).toBe('SKU-99');

    uuidSpy.mockRestore();
  });

  it('preserves GS1 raw barcode on the payload', () => {
    const event = createScanEventPayload('01234567890128', {
      sku: '01234567890128',
      isGs1: true,
      raw: '(01)01234567890128(10)LOT1',
      lotNumber: 'LOT1',
    });
    expect(event.barcode).toBe('01234567890128');
    expect(event.rawBarcode).toBe('(01)01234567890128(10)LOT1');
    expect(event.parsed?.lotNumber).toBe('LOT1');
  });
});
