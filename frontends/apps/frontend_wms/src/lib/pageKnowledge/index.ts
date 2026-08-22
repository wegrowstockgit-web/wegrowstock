/**
 * Core page-knowledge API for the Page Info ("i") overlay.
 * Lives outside the optional chatbot module so help content remains when Co-Pilot is disabled.
 */
export {
  knowledgeContextKey,
  normalizeColumns,
  resolveKnowledgeContext,
  resolveRouteKnowledge,
  formatRouteKnowledgeForChat,
  enrichRouteKnowledge,
  playbookI18nKey,
  humanRoleLabels,
  rolePermissionsForRouteKey,
  ROUTE_KNOWLEDGE,
} from './RouteKnowledgeRegistry';

export type {
  PageAction,
  TroubleshootingStep,
  PageKnowledge,
  RouteKnowledgeColumn,
  RouteKnowledgeColumns,
  RouteKnowledgeComponent,
  RouteKnowledge,
  ResolvedRouteKnowledge,
} from './RouteKnowledgeRegistry';

export { resolveDynamicPageKnowledge } from './resolveDynamicPageKnowledge';
export { usePageKnowledge, usePageKnowledgeCatalog, pageKnowledgeQueryOptions } from './usePageKnowledge';
export { fetchAllPageKnowledge } from './dynamicApi';
export { PAGE_KNOWLEDGE_QUERY_KEY } from './dynamicTypes';
export type { DynamicPageKnowledge, MistakeFix } from './dynamicTypes';
