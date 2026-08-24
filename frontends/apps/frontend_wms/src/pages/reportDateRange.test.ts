import { describe, expect, it } from 'vitest';
import {
  applyDatePreset,
  clampStartToMax,
  daysBetween,
  reportMaxDays,
  reportRangeError,
} from './reportDateRange';

describe('reportDateRange', () => {
  it('allows a 90-day standard window and rejects 91 days', () => {
    expect(reportRangeError('2026-01-01', '2026-04-01', 90)).toBeNull();
    expect(reportRangeError('2026-01-01', '2026-04-02', 90)).toBe(
      'Date range cannot exceed 90 days. Narrow the search.',
    );
  });

  it('caps high-volume reports at 31 days', () => {
    expect(reportMaxDays('cogs')).toBe(31);
    expect(reportMaxDays('fulfillment')).toBe(31);
    expect(reportRangeError('2026-01-01', '2026-02-01', 31)).toBeNull();
    expect(reportRangeError('2026-01-01', '2026-02-02', 31)).toContain('31 days');
  });

  it('rejects inverted ranges', () => {
    expect(reportRangeError('2026-03-01', '2026-02-01', 90)).toBe(
      'Start date must be on or before the end date.',
    );
  });

  it('clamps start to the max window', () => {
    expect(clampStartToMax('2026-04-01', 90)).toBe('2026-01-01');
    expect(daysBetween('2026-01-01', '2026-04-01')).toBe(90);
  });

  it('applies presets and clamps year-to-date to the report cap', () => {
    const now = new Date('2026-08-23T12:00:00.000Z');
    expect(applyDatePreset('today', 90, now)).toEqual({
      startDate: '2026-08-23',
      endDate: '2026-08-23',
    });
    expect(applyDatePreset('7d', 90, now)).toEqual({
      startDate: '2026-08-16',
      endDate: '2026-08-23',
    });
    expect(applyDatePreset('ytd', 90, now)).toEqual({
      startDate: '2026-05-25',
      endDate: '2026-08-23',
    });
  });
});
