import { Component, type ErrorInfo, type ReactNode } from 'react';

export type ErrorBoundaryProps = {
  children: ReactNode;
  boundaryName?: string;
  fallback?: ReactNode;
  className?: string;
  onError?: (error: Error, info: ErrorInfo) => void;
};

type State = { error: Error | null };

/** Catches render failures so one view cannot blank the admin shell. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    this.props.onError?.(error, info);
  }

  private handleRetry = (): void => {
    window.location.reload();
  };

  render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      return this.props.className ? (
        <div className={this.props.className}>{this.props.children}</div>
      ) : (
        this.props.children
      );
    }
    if (this.props.fallback) {
      return this.props.fallback;
    }
    return (
      <div
        role="alert"
        data-testid="error-boundary"
        className={this.props.className ?? 'flex min-h-[40vh] flex-col items-center justify-center gap-3 p-8 text-center'}
      >
        <h1 className="text-lg font-semibold text-text">Something went wrong</h1>
        <p className="max-w-md text-sm text-text-muted">
          This screen hit an unexpected error. Retry to reload, or navigate elsewhere from the menu.
        </p>
        <button
          type="button"
          className="rounded border border-border px-3 py-1.5 text-sm text-text hover:bg-surface"
          onClick={this.handleRetry}
        >
          Retry
        </button>
      </div>
    );
  }
}
