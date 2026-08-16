import { describe, expect, it } from 'vitest';
import { cartTotals, formatMoney, lineTotal, TAX_RATES } from './tax';

describe('tax', () => {
  it('computes USA and Mexico totals', () => {
    const us = cartTotals([{ unitPrice: 100, qty: 1 }], 'US');
    expect(us.tax).toBe(8.25);
    expect(us.grandTotal).toBe(108.25);
    const mx = cartTotals([{ unitPrice: 50, qty: 2 }], 'MX');
    expect(mx.subtotal).toBe(100);
    expect(mx.tax).toBe(16);
    expect(TAX_RATES.MX).toBe(0.16);
  });

  it('formats money and line totals', () => {
    expect(lineTotal(12.5, 2)).toBe(25);
    expect(formatMoney(12.5, 'USD')).toContain('12.50');
    expect(formatMoney(12.5, 'MXN')).toBeTruthy();
    expect(formatMoney(12.5, 'EUR', 'fr-FR')).toMatch(/12/);
  });
});
