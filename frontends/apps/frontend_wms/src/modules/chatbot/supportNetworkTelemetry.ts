/**
 * Captures the last failed API call (status + W3C / request trace id) for the
 * Support Copilot page-state snapshot.
 */

export type SupportNetworkErrorSnapshot = {
  status: number | null;
  message: string | null;
  /** W3C trace id or X-Request-Id / X-Trace-Id from the failing response. */
  traceId: string | null;
  at: number;
};

let lastError: SupportNetworkErrorSnapshot | null = null;

export function recordSupportNetworkError(input: {
  status?: number | null;
  message?: string | null;
  traceId?: string | null;
}): void {
  lastError = {
    status: input.status ?? null,
    message: input.message?.trim() ? input.message.trim() : null,
    traceId: input.traceId?.trim() ? input.traceId.trim() : null,
    at: Date.now(),
  };
}

export function getLastSupportNetworkError(): SupportNetworkErrorSnapshot | null {
  return lastError;
}

/** Drop stale errors older than 5 minutes so the copilot is not stuck on ancient failures. */
export function getFreshSupportNetworkError(maxAgeMs = 5 * 60_000): SupportNetworkErrorSnapshot | null {
  if (!lastError) return null;
  if (Date.now() - lastError.at > maxAgeMs) return null;
  return lastError;
}

export function clearSupportNetworkError(): void {
  lastError = null;
}
