import axios from 'axios';

/**
 * RFC 7807 Problem Details from an Axios/mutation error, else the caller fallback.
 */
export function extractApiError(error: unknown, fallbackMessage: string): string {
  if (axios.isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: unknown } | undefined)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail.trim();
    }
  }
  return fallbackMessage;
}
