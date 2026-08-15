import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorBoundary } from './ErrorBoundary';

function Boom(): null {
  throw new Error('render boom');
}

describe('ErrorBoundary', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows recovery UI when a child throws during render', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <ErrorBoundary boundaryName="test">
        <Boom />
      </ErrorBoundary>,
    );

    expect(screen.getByTestId('error-boundary')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByTestId('error-boundary-retry')).toBeInTheDocument();
  });
});
