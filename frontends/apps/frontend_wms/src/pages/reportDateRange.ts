export const REPORT_STANDARD_MAX_DAYS = 90;
export const REPORT_HIGH_VOLUME_MAX_DAYS = 31;

export type ReportRangeTab =
  | 'valuation'
  | 'timeTravel'
  | 'turnover'
  | 'cogs'
  | 'profit'
  | 'sales'
  | 'fulfillment'
  | 'purchases'
  | 'returns'
  | 'demand'
  | 'labor'
  | 'audit';

export function reportMaxDays(tab: ReportRangeTab): number | null {
  switch (tab) {
    case 'cogs':
    case 'fulfillment':
      return REPORT_HIGH_VOLUME_MAX_DAYS;
    case 'turnover':
    case 'profit':
    case 'sales':
    case 'purchases':
    case 'returns':
      return REPORT_STANDARD_MAX_DAYS;
    default:
      return null;
  }
}

export function isoDateUtc(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function defaultReportRange(maxDays: number): { startDate: string; endDate: string } {
  const end = new Date();
  const start = new Date(end);
  start.setUTCDate(start.getUTCDate() - maxDays);
  return { startDate: isoDateUtc(start), endDate: isoDateUtc(end) };
}

export function daysBetween(startDate: string, endDate: string): number {
  const start = Date.parse(`${startDate}T00:00:00.000Z`);
  const end = Date.parse(`${endDate}T00:00:00.000Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end)) return Number.NaN;
  return Math.round((end - start) / 86_400_000);
}

export function reportRangeError(
  startDate: string,
  endDate: string,
  maxDays: number,
): string | null {
  const days = daysBetween(startDate, endDate);
  if (!Number.isFinite(days)) {
    return 'Enter a valid start and end date.';
  }
  if (days < 0) {
    return 'Start date must be on or before the end date.';
  }
  if (days > maxDays) {
    return `Date range cannot exceed ${maxDays} days. Narrow the search.`;
  }
  return null;
}

export function clampStartToMax(endDate: string, maxDays: number): string {
  const end = new Date(`${endDate}T00:00:00.000Z`);
  if (!Number.isFinite(end.getTime())) {
    return defaultReportRange(maxDays).startDate;
  }
  end.setUTCDate(end.getUTCDate() - maxDays);
  return isoDateUtc(end);
}

export type ReportDatePreset = 'today' | '7d' | '30d' | 'ytd';

/**
 * Quick windows. Year-to-date is clamped to `maxDays` so the backend
 * guardrail is never bypassed in late-year calendars.
 */
export function applyDatePreset(
  preset: ReportDatePreset,
  maxDays: number,
  now = new Date(),
): { startDate: string; endDate: string } {
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const start = new Date(end);
  if (preset === 'today') {
    return { startDate: isoDateUtc(start), endDate: isoDateUtc(end) };
  }
  if (preset === '7d') {
    start.setUTCDate(start.getUTCDate() - 7);
  } else if (preset === '30d') {
    start.setUTCDate(start.getUTCDate() - 30);
  } else {
    start.setUTCMonth(0, 1);
  }
  const span = daysBetween(isoDateUtc(start), isoDateUtc(end));
  if (Number.isFinite(span) && span > maxDays) {
    return { startDate: clampStartToMax(isoDateUtc(end), maxDays), endDate: isoDateUtc(end) };
  }
  return { startDate: isoDateUtc(start), endDate: isoDateUtc(end) };
}
