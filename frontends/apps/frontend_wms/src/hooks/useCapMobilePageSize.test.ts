import { describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useCapMobilePageSize, MOBILE_PAGE_SIZE } from './useCapMobilePageSize';

describe('useCapMobilePageSize', () => {
  it('caps page size to 25 on mobile viewports', () => {
    const setSize = vi.fn();
    renderHook(() => useCapMobilePageSize(true, 50, setSize));
    expect(setSize).toHaveBeenCalledWith(MOBILE_PAGE_SIZE);
  });

  it('does not change size on desktop or when already 25', () => {
    const setSize = vi.fn();
    renderHook(() => useCapMobilePageSize(false, 50, setSize));
    expect(setSize).not.toHaveBeenCalled();
    renderHook(() => useCapMobilePageSize(true, 25, setSize));
    expect(setSize).not.toHaveBeenCalled();
  });
});
