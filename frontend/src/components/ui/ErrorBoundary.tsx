import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { reportError } from '@/lib/errorTelemetry';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** Optional label for telemetry (e.g. route name). */
  boundaryName?: string;
  /** Replace default fallback UI. */
  fallback?: ReactNode;
  className?: string;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * Catches render-phase failures so one page crash cannot blank the AppShell.
 * Retry reloads the document — safest recovery for inconsistent React trees.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    reportError(error, {
      source: 'ErrorBoundary',
      boundaryName: this.props.boundaryName ?? 'default',
      componentStack: info.componentStack,
    });
  }

  private handleRetry = (): void => {
    window.location.reload();
  };

  render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      if (this.props.className) {
        return <div className={this.props.className}>{this.props.children}</div>;
      }
      return this.props.children;
    }

    if (this.props.fallback) {
      return this.props.fallback;
    }

    return (
      <div
        role="alert"
        data-testid="error-boundary"
        className={cn(
          'flex min-h-[50dvh] flex-col items-center justify-center gap-4 px-4 py-12 text-center',
          'sm:min-h-[60dvh] sm:px-8',
          this.props.className,
        )}
      >
        <div className="rounded-full bg-danger/10 p-4">
          <AlertTriangle className="h-8 w-8 text-danger" aria-hidden />
        </div>
        <div className="max-w-md space-y-2">
          <h1 className="text-balance text-xl font-semibold text-text sm:text-2xl">
            Something went wrong
          </h1>
          <p className="text-pretty text-sm text-text-muted">
            This screen hit an unexpected error. Your session is intact — retry to reload, or
            navigate elsewhere from the menu.
          </p>
        </div>
        <Button
          type="button"
          onClick={this.handleRetry}
          data-testid="error-boundary-retry"
          className="min-h-11 min-w-[10rem]"
        >
          <RefreshCw className="h-4 w-4" />
          Retry
        </Button>
      </div>
    );
  }
}
