import { useCallback, useMemo } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { PaginatedResponse } from '@/api/types';
import { asPage, type OffsetQuery } from '@/api/page';
import type { SortDir, SortState } from '@/hooks/useClientSort';

export const PAGE_SIZES = [25, 50, 100] as const;
export type PageSize = (typeof PAGE_SIZES)[number];

function parseSize(raw: string | null): PageSize {
  const n = Number(raw);
  return PAGE_SIZES.includes(n as PageSize) ? (n as PageSize) : 50;
}

function parsePage(raw: string | null): number {
  const n = Number(raw);
  return Number.isFinite(n) && n >= 1 ? Math.floor(n) : 1;
}

export function parseSortParam(sort: string, fallback: string): SortState {
  const raw = sort || fallback;
  const [key, dir] = raw.split(',');
  return { key: key || fallback.split(',')[0], dir: dir === 'asc' ? 'asc' : 'desc' };
}

export function formatSortParam(key: string, dir: SortDir): string {
  return `${key},${dir}`;
}

export function useServerTableQuery<T>(options: {
  queryKey: string | string[];
  path: string;
  defaultSort?: string;
  extraParams?: Record<string, string | undefined>;
  fetcher?: (query: OffsetQuery) => Promise<PaginatedResponse<T>>;
  refetchInterval?: number | false | ((query: { state: { status: string } }) => number | false);
}) {
  const defaultSort = options.defaultSort ?? 'createdAt,desc';
  const [params, setParams] = useSearchParams();
  const page = parsePage(params.get('page'));
  const size = parseSize(params.get('size'));
  const search = params.get('search') ?? '';
  const sort = params.get('sort') ?? defaultSort;
  const sortState = useMemo(() => parseSortParam(sort, defaultSort), [sort, defaultSort]);

  const extra = options.extraParams ?? {};
  const extraKey = JSON.stringify(extra);

  const query = useQuery({
    queryKey: [
      ...(Array.isArray(options.queryKey) ? options.queryKey : [options.queryKey]),
      page,
      size,
      search,
      sort,
      extraKey,
    ],
    queryFn: async () => {
      if (options.fetcher) {
        return asPage(
          await options.fetcher({
            page,
            size,
            search: search || undefined,
            sort,
            status: extra.status,
          }),
        );
      }
      const { data } = await apiClient.get<PaginatedResponse<T> | T[]>(options.path, {
        params: {
          page,
          size,
          sort,
          ...(search ? { search } : {}),
          ...Object.fromEntries(Object.entries(extra).filter(([, v]) => Boolean(v))),
        },
      });
      return asPage(data);
    },
    placeholderData: keepPreviousData,
    refetchInterval: options.refetchInterval,
  });

  const patch = useCallback(
    (next: Record<string, string | null>) => {
      const merged = new URLSearchParams(params);
      for (const [key, value] of Object.entries(next)) {
        if (value == null || value === '') merged.delete(key);
        else merged.set(key, value);
      }
      setParams(merged, { replace: true });
    },
    [params, setParams],
  );

  const setPage = useCallback(
    (next: number) => {
      patch({ page: next <= 1 ? null : String(next) });
    },
    [patch],
  );

  const setSize = useCallback(
    (next: number) => {
      patch({
        size: next === 50 ? null : String(next),
        page: null,
      });
    },
    [patch],
  );

  const setSearch = useCallback(
    (next: string) => {
      const trimmed = next.trim();
      if (trimmed === search) return;
      patch({
        search: trimmed || null,
        page: null,
      });
    },
    [patch, search],
  );

  const setSort = useCallback(
    (next: string) => {
      patch({
        sort: next === defaultSort ? null : next,
        page: null,
      });
    },
    [patch, defaultSort],
  );

  const toggleSort = useCallback(
    (apiField: string) => {
      const nextDir: SortDir =
        sortState.key === apiField && sortState.dir === 'asc' ? 'desc' : 'asc';
      setSort(formatSortParam(apiField, nextDir));
    },
    [setSort, sortState],
  );

  const pageData = query.data;
  const items = pageData?.items ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages = Math.max(pageData?.totalPages ?? 0, totalElements === 0 ? 0 : 1);

  return {
    page,
    size,
    search,
    sort,
    sortState,
    setPage,
    setSize,
    setSearch,
    setSort,
    toggleSort,
    items,
    totalElements,
    totalPages,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    isError: query.isError,
    error: query.error,
    refetch: query.refetch,
    isPlaceholderData: query.isPlaceholderData,
  };
}
