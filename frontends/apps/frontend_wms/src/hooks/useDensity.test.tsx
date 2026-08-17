import { beforeEach, describe, expect, it } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { useDensity, getDensityStyles, TableDensityScope } from './useDensity';
import { usePreferencesStore } from '@/stores/preferencesStore';

describe('useDensity', () => {
  beforeEach(() => {
    localStorage.clear();
    usePreferencesStore.setState({ densityMode: 'cozy', tableDensityById: {} });
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

  it('scopes density to a grid without changing the html default or other grids', () => {
    document.documentElement.setAttribute('data-density', 'cozy');
    const wrapper = ({ children }: { children: ReactNode }) => (
      <TableDensityScope gridId="purchase-orders">{children}</TableDensityScope>
    );
    const { result } = renderHook(() => useDensity(), { wrapper });
    act(() => {
      result.current.setDensityMode('compact');
    });
    expect(result.current.densityMode).toBe('compact');
    expect(usePreferencesStore.getState().densityMode).toBe('cozy');
    expect(usePreferencesStore.getState().tableDensityById['purchase-orders']).toBe('compact');
    expect(document.documentElement.getAttribute('data-density')).toBe('cozy');

    const { result: other } = renderHook(() => useDensity('suppliers'));
    expect(other.current.densityMode).toBe('cozy');
  });
});
