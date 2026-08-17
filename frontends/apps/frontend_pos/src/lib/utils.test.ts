import { describe, expect, it } from 'vitest';
import { formatPosMoney, nextDollarAmount } from './utils';

describe('pos money utils', () => {
  it('formats with the active locale', () => {
    expect(formatPosMoney(12.5, 'USD', 'en-US')).toMatch(/\$12\.50/);
    expect(formatPosMoney(12.5, 'MXN', 'es-MX')).toMatch(/12/);
  });

  it('ceils to the next dollar', () => {
    expect(nextDollarAmount(12.01)).toBe(13);
    expect(nextDollarAmount(12)).toBe(12);
    expect(nextDollarAmount(0)).toBe(0);
  });
});
