import { describe, expect, it } from 'vitest';
import { parseSerialWeightLine } from './useDigitalScale';

describe('usePackingScale serial parsing', () => {
  it('extracts clean float lb from shipping-bay ASCII ticks', () => {
    expect(parseSerialWeightLine('ST,GS,+0012.340lb')?.weightLb).toBeCloseTo(12.34, 2);
    expect(parseSerialWeightLine('  3.50 lb')?.weightLb).toBe(3.5);
  });
});
