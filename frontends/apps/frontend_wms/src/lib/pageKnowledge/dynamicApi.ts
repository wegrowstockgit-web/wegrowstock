import { apiClient } from '@/api/client';
import type { DynamicPageKnowledge } from './dynamicTypes';

export async function fetchAllPageKnowledge(): Promise<DynamicPageKnowledge[]> {
  const { data } = await apiClient.get<DynamicPageKnowledge[]>('/api/v1/page-knowledge/all');
  return Array.isArray(data) ? data : [];
}

export async function fetchPageKnowledgeByRoute(route: string): Promise<DynamicPageKnowledge> {
  const { data } = await apiClient.get<DynamicPageKnowledge>('/api/v1/page-knowledge', {
    params: { route },
  });
  return data;
}
