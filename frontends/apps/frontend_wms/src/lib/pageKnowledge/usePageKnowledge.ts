import { useQuery } from '@tanstack/react-query';
import { fetchAllPageKnowledge } from './dynamicApi';
import { PAGE_KNOWLEDGE_QUERY_KEY } from './dynamicTypes';
import { resolveDynamicPageKnowledge } from './resolveDynamicPageKnowledge';

const THIRTY_MINUTES = 1000 * 60 * 30;

export function usePageKnowledgeCatalog() {
  return useQuery({
    queryKey: PAGE_KNOWLEDGE_QUERY_KEY,
    queryFn: fetchAllPageKnowledge,
    staleTime: THIRTY_MINUTES,
    gcTime: THIRTY_MINUTES * 2,
    retry: 1,
  });
}

/** Instant resolve from the preloaded catalog (0ms after hydration). */
export function usePageKnowledge(pathname: string, search = '') {
  const { data } = usePageKnowledgeCatalog();
  return resolveDynamicPageKnowledge(data, pathname, search);
}

export function pageKnowledgeQueryOptions() {
  return {
    queryKey: PAGE_KNOWLEDGE_QUERY_KEY,
    queryFn: fetchAllPageKnowledge,
    staleTime: THIRTY_MINUTES,
  } as const;
}
