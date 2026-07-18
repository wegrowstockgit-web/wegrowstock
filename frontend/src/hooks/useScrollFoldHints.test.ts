import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { createRef } from 'react';
import { useScrollFoldHints } from './useScrollFoldHints';

describe('useScrollFoldHints', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
    vi.stubGlobal(
      'MutationObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reports down-fold when content overflows at top', () => {
    const ref = createRef<HTMLDivElement>();
    const el = document.createElement('div');
    Object.defineProperties(el, {
      scrollTop: { configurable: true, get: () => 0, set() {} },
      scrollHeight: { configurable: true, get: () => 800 },
      clientHeight: { configurable: true, get: () => 200 },
      addEventListener: { value: vi.fn() },
      removeEventListener: { value: vi.fn() },
    });
    (ref as { current: HTMLDivElement | null }).current = el;

    const { result } = renderHook(() => useScrollFoldHints(ref));
    expect(result.current.canScrollDown).toBe(true);
    expect(result.current.canScrollUp).toBe(false);
    expect(result.current.hasOverflow).toBe(true);
  });

  it('reports up-fold after scrolling past top', () => {
    const ref = createRef<HTMLDivElement>();
    let scrollTop = 120;
    const el = document.createElement('div');
    Object.defineProperties(el, {
      scrollTop: {
        configurable: true,
        get: () => scrollTop,
        set(v: number) {
          scrollTop = v;
        },
      },
      scrollHeight: { configurable: true, get: () => 800 },
      clientHeight: { configurable: true, get: () => 200 },
      addEventListener: { value: vi.fn() },
      removeEventListener: { value: vi.fn() },
    });
    (ref as { current: HTMLDivElement | null }).current = el;

    const { result, rerender } = renderHook(() => useScrollFoldHints(ref));
    expect(result.current.canScrollUp).toBe(true);
    expect(result.current.canScrollDown).toBe(true);

    scrollTop = 700;
    act(() => {
      rerender();
    });
  });
});
