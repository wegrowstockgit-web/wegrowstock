import { beforeEach, describe, expect, it } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDensity, getDensityStyles } from './useDensity';
import { usePreferencesStore } from '@/stores/preferencesStore';

describe('useDensity', () => {
  beforeEach(() => {
    localStorage.clear();
    usePreferencesStore.setState({ densityMode: 'cozy' });
    document.documentElement.removeAttribute('data-density');
  });

  it('exposes cozy defaults and syncs data-density attribute', () => {
    const { result } = renderHook(() => useDensity());
    expect(result.current.densityMode).toBe('cozy');
    expect(result.current.rowPx).toBe(44);
    expect(document.documentElement.getAttribute('data-density')).toBe('cozy');
  });

  it('updates classes when density changes', () => {
    const { result } = renderHook(() => useDensity());
    act(() => {
      result.current.setDensityMode('spacious');
    });
    expect(result.current.styles.row).toBe('h-16');
    expect(document.documentElement.getAttribute('data-density')).toBe('spacious');
    expect(getDensityStyles('compact').typography).toBe('text-xs');
  });
});
