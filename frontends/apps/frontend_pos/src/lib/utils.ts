export function formatPosMoney(amount: number, currency = 'USD', locale?: string): string {
  const resolved = locale ?? (currency === 'MXN' ? 'es-MX' : 'en-US');
  return new Intl.NumberFormat(resolved, {
    style: 'currency',
    currency,
  }).format(amount);
}

/** Ceiling to the next whole currency unit (POS “next dollar”). */
export function nextDollarAmount(total: number): number {
  if (!Number.isFinite(total) || total <= 0) return 0;
  return Math.ceil(total);
}
