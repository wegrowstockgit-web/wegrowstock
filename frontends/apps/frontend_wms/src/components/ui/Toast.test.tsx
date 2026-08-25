import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ToastProvider, useToast } from './Toast';

function Trigger() {
  const { toast } = useToast();
  return (
    <button type="button" onClick={() => toast('Restock bin is required.', { tone: 'danger' })}>
      Fail
    </button>
  );
}

describe('ToastProvider', () => {
  it('anchors the stack below the header in the top-right', () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    const region = screen.getByTestId('toast-region');
    expect(region.className).toContain('fixed');
    expect(region.className).toContain('right-6');
    expect(region.className).toContain('z-[200]');
    expect(region.className).not.toContain('bottom-');
    expect(region.className).not.toContain('left-1/2');
    expect(region.style.top).toContain('--header-height');
  });

  it('does not stack the same message from interceptor and local onError', () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Fail' }));
    fireEvent.click(screen.getByRole('button', { name: 'Fail' }));
    expect(screen.getAllByTestId('app-toast')).toHaveLength(1);
  });

  it('slides the toast in from the top', () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Fail' }));
    const toast = screen.getByTestId('app-toast');
    expect(toast).toHaveAttribute('data-toast-state', 'enter');
    expect(toast.className).toContain('toast-slide-in');
    expect(toast).toHaveTextContent('Restock bin is required.');
  });

  it('slides the toast back up on dismiss', () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Fail' }));
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.getByTestId('app-toast')).toHaveAttribute('data-toast-state', 'leave');
    expect(screen.getByTestId('app-toast').className).toContain('toast-slide-out');
  });
});
