import { formatPosMoney } from './utils';

export type TaxRegion = 'US' | 'MX';

export const TAX_RATES: Record<TaxRegion, number> = {
  US: 0.0825,
  MX: 0.16,
};

export const TAX_LABELS: Record<TaxRegion, string> = {
  US: 'USA sales tax (8.25%)',
  MX: 'Mexico IVA (16%)',
};

export function lineTotal(unitPrice: number, qty: number): number {
  return roundMoney(unitPrice * qty);
}

export function cartTotals(lines: Array<{ unitPrice: number; qty: number }>, region: TaxRegion) {
  const subtotal = roundMoney(lines.reduce((sum, line) => sum + line.unitPrice * line.qty, 0));
  const tax = roundMoney(subtotal * TAX_RATES[region]);
  return { subtotal, tax, grandTotal: roundMoney(subtotal + tax) };
}

export function formatMoney(amount: number, currency = 'USD', locale?: string): string {
  return formatPosMoney(amount, currency, locale);
}

export function roundMoney(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}
