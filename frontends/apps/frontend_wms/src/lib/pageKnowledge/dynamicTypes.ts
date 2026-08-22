export type MistakeFix = {
  mistake: string;
  solution: string;
  requiredRole: string;
};

export type DynamicPageKnowledge = {
  id: string;
  routePattern: string;
  category: string;
  title: string;
  summary: string;
  rolePrivileges: string;
  keyActions: string[];
  commonMistakes: MistakeFix[];
  proTip?: string | null;
  updatedAt?: string | null;
};

export const PAGE_KNOWLEDGE_QUERY_KEY = ['page-knowledge', 'all'] as const;
