import { describe, expect, it } from 'vitest';
import { gs1LookupKey, parseGs1Barcode } from '@/lib/gs1Barcode';

describe('parseGs1Barcode', () => {
  it('parses AI 01 / 17 / 10 composite payload', () => {
    const parsed = parseGs1Barcode('01012345678901281725010110LOT42');
    expect(parsed).not.toBeNull();
    expect(parsed?.gtin).toBe('01234567890128');
    expect(parsed?.expiry).toBe('2025-01-01');
    expect(parsed?.lot).toBe('LOT42');
  });

  it('parses parenthetical form', () => {
    const parsed = parseGs1Barcode('(01)01234567890128(10)BATCH9(17)251231');
    expect(parsed?.gtin).toBe('01234567890128');
    expect(parsed?.lot).toBe('BATCH9');
    expect(parsed?.expiry).toBe('2025-12-31');
  });

  it('lookup key prefers GTIN', () => {
    expect(gs1LookupKey('01012345678901281725010110ABC')).toBe('01234567890128');
    expect(gs1LookupKey('PLAIN')).toBe('PLAIN');
  });

  it('parses AI 30 variable quantity with FNC1', () => {
    const parsed = parseGs1Barcode('01012345678901283012\u001d10LOT99');
    expect(parsed?.gtin).toBe('01234567890128');
    expect(parsed?.variableQuantity).toBe(12);
    expect(parsed?.lot).toBe('LOT99');
  });

  it('returns null for non-GS1', () => {
    expect(parseGs1Barcode('SKU-1')).toBeNull();
    expect(parseGs1Barcode('')).toBeNull();
  });
});
