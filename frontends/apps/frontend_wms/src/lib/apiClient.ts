import axios from 'axios';
import { toast } from '@/components/ui/Toast';

export const PROBLEM_TOAST_STATUSES = new Set([400, 409, 422]);

const DEDUPE_MS = 1_500;
let lastToastKey = '';
let lastToastAt = 0;

/**
 * RFC 7807 Problem Details from an Axios/mutation error, else the caller fallback.
 */
export function extractApiError(error: unknown, fallbackMessage: string): string {
  return extractProblemDetail(error) ?? fallbackMessage;
}

/** RFC 7807 `detail` when present on an Axios response body. */
export function extractProblemDetail(error: unknown): string | null {
  if (!axios.isAxiosError(error)) return null;
  const detail = (error.response?.data as { detail?: unknown } | undefined)?.detail;
  if (typeof detail === 'string' && detail.trim()) {
    return detail.trim();
  }
  return null;
}

/**
 * Global silent-failure guard: toast RFC 7807 detail on 400 / 409 / 422.
 * Still rejects so forms can set inline errors. Dedupes with local onError toasts.
 */
export function notifyApiProblem(error: unknown): void {
  if (!axios.isAxiosError(error)) return;
  if (error.config?.skipErrorToast) return;
  const status = error.response?.status;
  if (!status || !PROBLEM_TOAST_STATUSES.has(status)) return;
  const detail = extractProblemDetail(error);
  if (!detail) return;
  const key = `${status}:${detail}`;
  const now = Date.now();
  if (key === lastToastKey && now - lastToastAt < DEDUPE_MS) return;
  lastToastKey = key;
  lastToastAt = now;
  toast.error(detail);
}

export function resetApiProblemToastForTests(): void {
  lastToastKey = '';
  lastToastAt = 0;
}
