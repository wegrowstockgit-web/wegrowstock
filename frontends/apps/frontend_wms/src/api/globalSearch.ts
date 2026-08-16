import { apiClient } from '@/api/client';

export interface GlobalSearchResult {
  id: string;
  category: string;
  title: string;
  subtitle?: string;
  route: string;
  requiredPermission?: string | null;
  label: string;
  path: string;
}

interface SearchResultDto {
  category: string;
  title: string;
  subtitle?: string | null;
  route: string;
  requiredPermission?: string | null;
}

export async function globalSearch(query: string): Promise<GlobalSearchResult[]> {
  const q = query.trim();
  if (q.length < 2) return [];

  const { data } = await apiClient.get<SearchResultDto[]>('/api/v1/search/global', {
    params: { q },
  });

  return (data ?? []).map((result, index) => ({
    id: `${result.category}-${result.route}-${result.title}-${index}`,
    category: result.category,
    title: result.title,
    subtitle: result.subtitle ?? undefined,
    route: result.route,
    requiredPermission: result.requiredPermission,
    label: result.title,
    path: result.route,
  }));
}
