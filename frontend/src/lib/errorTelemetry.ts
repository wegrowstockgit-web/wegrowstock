/**
 * Frontend error telemetry — console today, OpenTelemetry / Sentry tomorrow.
 *
 * OpenTelemetry (optional):
 *   import { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
 *   import { DocumentLoadInstrumentation } from '@opentelemetry/instrumentation-document-load';
 *   // provider.register(); then reportError → tracer.startSpan('frontend.error').recordException(err)
 *
 * Sentry (optional):
 *   import * as Sentry from '@sentry/react';
 *   Sentry.init({ dsn: import.meta.env.VITE_SENTRY_DSN, integrations: [Sentry.browserTracingIntegration()] });
 *   // reportError → Sentry.captureException(error, { extra: context })
 */

export type ErrorTelemetryContext = Record<string, unknown>;

export type ErrorReporter = (error: unknown, context?: ErrorTelemetryContext) => void;

let customReporter: ErrorReporter | null = null;

export function setErrorReporter(reporter: ErrorReporter | null): void {
  customReporter = reporter;
}

export function reportError(error: unknown, context: ErrorTelemetryContext = {}): void {
  const normalized =
    error instanceof Error
      ? error
      : new Error(typeof error === 'string' ? error : 'Unknown error', { cause: error });

  // Always keep a local breadcrumb for support / DevTools.
  console.error('[invsys:telemetry]', normalized.message, {
    ...context,
    name: normalized.name,
    stack: normalized.stack,
  });

  customReporter?.(normalized, context);

  // --- Provider hooks (uncomment when a DSN / collector is configured) ---
  // Sentry.captureException(normalized, { extra: context });
  // otelSpan.recordException(normalized); otelSpan.setStatus({ code: SpanStatusCode.ERROR });
}

let installed = false;

/** Hook window.onerror + unhandledrejection once at app boot. */
export function installGlobalErrorTelemetry(): void {
  if (installed || typeof window === 'undefined') return;
  installed = true;

  window.addEventListener('error', (event) => {
    reportError(event.error ?? event.message, {
      source: 'window.error',
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
    });
  });

  window.addEventListener('unhandledrejection', (event) => {
    reportError(event.reason, { source: 'unhandledrejection' });
  });
}

/** Test helper — resets install flag between vitest cases. */
export function resetErrorTelemetryForTests(): void {
  installed = false;
  customReporter = null;
}
