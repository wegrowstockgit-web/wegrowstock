import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { UndoToast } from '@/components/ui/UndoToast';

describe('UndoToast', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows message and triggers undo', () => {
    const onUndo = vi.fn();
    const onDismiss = vi.fn();

    render(
      <UndoToast
        visible
        message="Scan queued — undo within 5s"
        onUndo={onUndo}
        onDismiss={onDismiss}
        durationMs={5000}
      />
    );

    expect(screen.getByRole('status')).toHaveTextContent('Scan queued');
    fireEvent.click(screen.getByRole('button', { name: /undo/i }));
    expect(onUndo).toHaveBeenCalled();
    expect(onDismiss).not.toHaveBeenCalled();
  });

  it('auto-dismisses after duration', () => {
    const onDismiss = vi.fn();

    render(
      <UndoToast
        visible
        message="Queued"
        onUndo={() => {}}
        onDismiss={onDismiss}
        durationMs={5000}
      />
    );

    vi.advanceTimersByTime(5100);
    expect(onDismiss).toHaveBeenCalled();
  });
});
