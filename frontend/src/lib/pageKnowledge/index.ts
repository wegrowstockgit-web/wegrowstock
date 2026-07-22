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
  humanRoleLabels,
  rolePermissionsForRouteKey,
  ROUTE_KNOWLEDGE,
} from './RouteKnowledgeRegistry';

export type {
  RouteKnowledgeColumn,
  RouteKnowledgeColumns,
  RouteKnowledgeComponent,
  RouteKnowledge,
  ResolvedRouteKnowledge,
} from './RouteKnowledgeRegistry';
