import { useMemo, useState } from 'react';

export type SortDir = 'asc' | 'desc';

export interface SortState {
  key: string;
  dir: SortDir;
}

export type SortAccessors<T> = Record<
  string,
  (row: T) => string | number | boolean | null | undefined | Date
>;

function compareValues(
  a: string | number | boolean | null | undefined | Date,
  b: string | number | boolean | null | undefined | Date,
): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  if (a instanceof Date || b instanceof Date) {
    const av = a instanceof Date ? a.getTime() : new Date(String(a)).getTime();
    const bv = b instanceof Date ? b.getTime() : new Date(String(b)).getTime();
    return av - bv;
  }
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b);
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
}

/**
 * Client-side column sort for Surface A list tables.
 * Toggle: unsorted key → asc → desc → asc.
 */
export function useClientSort<T>(
  rows: T[],
  accessors: SortAccessors<T>,
  initial?: SortState | null,
) {
  const [sort, setSort] = useState<SortState | null>(initial ?? null);

  const toggle = (key: string) => {
    setSort((prev) => {
      if (!prev || prev.key !== key) return { key, dir: 'asc' };
      return { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' };
    });
  };

  const sorted = useMemo(() => {
    if (!sort || !accessors[sort.key]) return rows;
    const accessor = accessors[sort.key];
    const dir = sort.dir === 'asc' ? 1 : -1;
    return [...rows].sort((ra, rb) => dir * compareValues(accessor(ra), accessor(rb)));
  }, [rows, accessors, sort]);

  return { sort, toggle, sorted, setSort };
}
