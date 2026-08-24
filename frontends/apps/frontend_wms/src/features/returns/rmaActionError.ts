import axios from 'axios';

const FALLBACK = 'An unexpected error occurred while processing the RMA.';

/** RFC 7807 `detail` from an Axios/fetch error, else a generic RMA message. */
export function rmaActionErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: unknown } | undefined)?.detail;
    if (typeof detail === 'string' && detail.trim()) return detail;
  }
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const detail = (error as { response?: { data?: { detail?: unknown } } }).response?.data?.detail;
    if (typeof detail === 'string' && detail.trim()) return detail;
  }
  return FALLBACK;
}

export function restockLinesMissingBin(
  lines: Array<{ disposition?: string | null; restockLocationId?: string | null }>,
): boolean {
  return lines.some(
    (line) => line.disposition === 'RESTOCK' && !String(line.restockLocationId ?? '').trim(),
  );
}
