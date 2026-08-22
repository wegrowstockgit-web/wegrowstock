import { describe, expect, it } from 'vitest';
import { formatMediumDate } from './utils';

describe('formatMediumDate', () => {
  it('formats dates as MMM DD, YYYY', () => {
    expect(formatMediumDate(new Date(2026, 7, 22))).toBe('Aug 22, 2026');
  });

  it('returns an em dash for empty values', () => {
    expect(formatMediumDate(null)).toBe('—');
    expect(formatMediumDate(undefined)).toBe('—');
    expect(formatMediumDate('')).toBe('—');
  });
});
