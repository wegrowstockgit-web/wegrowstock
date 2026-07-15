import { describe, expect, it } from 'vitest';
import { evaluateLotGrace, gs1LookupSku, parseGs1 } from '@/utils/gs1Parser';

describe('parseGs1', () => {
  it('parses concatenated AI 01 / 17 / 10', () => {
    const parsed = parseGs1('01012345678901281725010110LOT42');
    expect(parsed.isGs1).toBe(true);
    expect(parsed.sku).toBe('01234567890128');
    expect(parsed.expiryDate).toBe('2025-01-01');
    expect(parsed.lotNumber).toBe('LOT42');
  });

  it('parses parenthetical human-readable form', () => {
    const parsed = parseGs1('(01)01234567890128(10)BATCH9(17)251231(30)4');
    expect(parsed.isGs1).toBe(true);
    expect(parsed.sku).toBe('01234567890128');
    expect(parsed.lotNumber).toBe('BATCH9');
    expect(parsed.expiryDate).toBe('2025-12-31');
    expect(parsed.quantity).toBe(4);
  });

  it('keeps lot from bleeding into AI 30 when FNC1 is present', () => {
    const parsed = parseGs1('010123456789012810LOT99\u001d3012');
    expect(parsed.isGs1).toBe(true);
    expect(parsed.lotNumber).toBe('LOT99');
    expect(parsed.quantity).toBe(12);
  });

  it('returns plain sku for non-GS1 barcodes', () => {
    const parsed = parseGs1('SKU-ONLY');
    expect(parsed.isGs1).toBe(false);
    expect(parsed.sku).toBe('SKU-ONLY');
    expect(parsed.lotNumber).toBeUndefined();
  });

  it('gs1LookupSku prefers GTIN', () => {
    expect(gs1LookupSku('01012345678901281725010110ABC')).toBe('01234567890128');
    expect(gs1LookupSku('PLAIN')).toBe('PLAIN');
  });

  it('handles empty and whitespace input', () => {
    expect(parseGs1('')).toEqual({ sku: '', isGs1: false });
    expect(parseGs1('   ')).toEqual({ sku: '', isGs1: false });
    expect(parseGs1(null)).toEqual({ sku: '', isGs1: false });
  });

  it('parses ]C1 symbology prefix', () => {
    const parsed = parseGs1(']C101012345678901281700010110LOTZ');
    expect(parsed.isGs1).toBe(true);
    expect(parsed.sku).toBe('01234567890128');
    expect(parsed.lotNumber).toBe('LOTZ');
  });
});

describe('evaluateLotGrace', () => {
  it('sinks lot into metadata when tracking is disabled', () => {
    const parsed = parseGs1('(01)01234567890128(10)LOT123');
    const grace = evaluateLotGrace(parsed, false);
    expect(grace.lotLoggedNotTracked).toBe(true);
    expect(grace.metadata).toEqual({ vendor_lot_captured: 'LOT123' });
  });

  it('does not warn when lot tracking is enabled', () => {
    const parsed = parseGs1('(01)01234567890128(10)LOT123');
    expect(evaluateLotGrace(parsed, true).lotLoggedNotTracked).toBe(false);
  });

  it('does not warn on cache miss', () => {
    const parsed = parseGs1('(01)01234567890128(10)LOT123');
    expect(evaluateLotGrace(parsed, undefined).lotLoggedNotTracked).toBe(false);
  });
});

