import type { PaginatedResponse } from '@/api/types';

export interface OffsetQuery {
  page?: number;
  size?: number;
  search?: string;
  sort?: string;
  status?: string;
}

export function unwrapPageItems<T>(data: PaginatedResponse<T> | T[] | null | undefined): T[] {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.items ?? [];
}

export function asPage<T>(data: PaginatedResponse<T> | T[] | null | undefined): PaginatedResponse<T> {
  if (!data) {
    return { items: [], totalElements: 0, totalPages: 0, page: 1, size: 50, hasMore: false };
  }
  if (Array.isArray(data)) {
    return {
      items: data,
      totalElements: data.length,
      totalPages: 1,
      page: 1,
      size: data.length,
      hasMore: false,
    };
  }
  return {
    ...data,
    items: data.items ?? [],
    totalElements: data.totalElements ?? data.total ?? data.items?.length ?? 0,
    totalPages: data.totalPages ?? 1,
    page: data.page ?? 1,
    size: data.size ?? data.items?.length ?? 50,
    hasMore: data.hasMore ?? false,
  };
}
