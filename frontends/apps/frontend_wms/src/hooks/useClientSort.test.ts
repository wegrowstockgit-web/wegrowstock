import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useClientSort } from '@/hooks/useClientSort';

describe('useClientSort', () => {
  const rows = [
    { id: '1', name: 'Bravo', qty: 2 },
    { id: '2', name: 'Alpha', qty: 10 },
    { id: '3', name: 'Charlie', qty: 5 },
  ];

  it('sorts ascending then descending on toggle', () => {
    const { result } = renderHook(() =>
      useClientSort(rows, {
        name: (r) => r.name,
        qty: (r) => r.qty,
      }),
    );

    act(() => result.current.toggle('name'));
    expect(result.current.sorted.map((r) => r.name)).toEqual(['Alpha', 'Bravo', 'Charlie']);

    act(() => result.current.toggle('name'));
    expect(result.current.sorted.map((r) => r.name)).toEqual(['Charlie', 'Bravo', 'Alpha']);
  });

  it('sorts numeric columns', () => {
    const { result } = renderHook(() =>
      useClientSort(rows, { qty: (r) => r.qty }, { key: 'qty', dir: 'desc' }),
    );
    expect(result.current.sorted.map((r) => r.qty)).toEqual([10, 5, 2]);
  });
});
