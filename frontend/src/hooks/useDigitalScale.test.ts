import { describe, expect, it } from 'vitest';
import { parseSerialWeightLine } from './useDigitalScale';

describe('parseSerialWeightLine', () => {
  it('parses lb and kg ASCII streams from shipping-bay scales', () => {
    expect(parseSerialWeightLine('  12.34 lb')).toMatchObject({
      weightLb: 12.34,
      weightOz: 12.34 * 16,
    });
    const kg = parseSerialWeightLine('1.000 kg');
    expect(kg?.weightLb).toBeCloseTo(2.20462, 4);
    expect(parseSerialWeightLine('ST,GS,+0012.340lb')?.weightLb).toBeCloseTo(12.34, 2);
  });

  it('rejects empty or non-weight lines', () => {
    expect(parseSerialWeightLine('')).toBeNull();
    expect(parseSerialWeightLine('READY')).toBeNull();
  });
});
