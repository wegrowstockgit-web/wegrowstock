import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useConcurrentSearch } from './useConcurrentSearch';

describe('useConcurrentSearch', () => {
  it('updates inputValue immediately while exposing a deferred query value', () => {
    const { result } = renderHook(() => useConcurrentSearch(''));

    act(() => {
      result.current.setInputValue('sku-42');
    });

    expect(result.current.inputValue).toBe('sku-42');
    // In jsdom without artificial delay, deferred catches up in the same act flush.
    expect(result.current.deferredValue).toBe('sku-42');
  });

  it('exposes startTransition for non-text filter controls', () => {
    const { result } = renderHook(() => useConcurrentSearch());
    let flagged = false;

    act(() => {
      result.current.startTransition(() => {
        flagged = true;
      });
    });

    expect(flagged).toBe(true);
    expect(typeof result.current.startTransition).toBe('function');
  });
});
