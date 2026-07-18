import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  installGlobalErrorTelemetry,
  reportError,
  resetErrorTelemetryForTests,
  setErrorReporter,
} from './errorTelemetry';

describe('errorTelemetry', () => {
  afterEach(() => {
    resetErrorTelemetryForTests();
    vi.restoreAllMocks();
  });

  it('logs normalized errors and forwards to a custom reporter', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const reporter = vi.fn();
    setErrorReporter(reporter);

    reportError(new Error('network down'), { source: 'unit' });

    expect(consoleSpy).toHaveBeenCalled();
    expect(reporter).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'network down' }),
      expect.objectContaining({ source: 'unit' }),
    );
  });

  it('installs window listeners once', () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    installGlobalErrorTelemetry();
    installGlobalErrorTelemetry();
    expect(addSpy).toHaveBeenCalledWith('error', expect.any(Function));
    expect(addSpy).toHaveBeenCalledWith('unhandledrejection', expect.any(Function));
    const errorCalls = addSpy.mock.calls.filter((c) => c[0] === 'error');
    expect(errorCalls).toHaveLength(1);
  });
});
