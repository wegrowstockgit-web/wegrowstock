import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { DebouncedSearchInput } from './DebouncedSearchInput';

describe('DebouncedSearchInput', () => {
  it('emits the value after 300ms and resets are skipped when unchanged', () => {
    vi.useFakeTimers();
    const onDebounced = vi.fn();
    render(<DebouncedSearchInput value="" onDebouncedChange={onDebounced} />);
    fireEvent.change(screen.getByTestId('table-search'), { target: { value: 'Acme' } });
    expect(onDebounced).not.toHaveBeenCalled();
    vi.advanceTimersByTime(299);
    expect(onDebounced).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(onDebounced).toHaveBeenCalledWith('Acme');
    vi.useRealTimers();
  });

  it('syncs when the controlled value changes', () => {
    const onDebounced = vi.fn();
    const { rerender } = render(<DebouncedSearchInput value="Acme" onDebouncedChange={onDebounced} />);
    expect(screen.getByTestId('table-search')).toHaveValue('Acme');
    rerender(<DebouncedSearchInput value="" onDebouncedChange={onDebounced} />);
    expect(screen.getByTestId('table-search')).toHaveValue('');
    expect(onDebounced).not.toHaveBeenCalled();
  });
});
