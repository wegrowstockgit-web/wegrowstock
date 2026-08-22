import type { DynamicPageKnowledge } from './dynamicTypes';
import { knowledgeContextKey } from './RouteKnowledgeRegistry';

function normalizePath(pathname: string): string {
  const withoutHash = (pathname || '/').split('#')[0] || '/';
  const pathOnly = withoutHash.split('?')[0] || '/';
  return pathOnly.replace(/\/+$/, '') || '/';
}

export function resolveDynamicPageKnowledge(
  catalog: DynamicPageKnowledge[] | undefined,
  pathname: string,
  search = '',
): DynamicPageKnowledge | null {
  if (!catalog || catalog.length === 0) {
    return null;
  }
  const path = normalizePath(pathname.includes('?') ? pathname.split('?')[0]! : pathname);
  const searchPart =
    pathname.includes('?') && !search ? pathname.slice(pathname.indexOf('?')) : search;
  const exactKey = knowledgeContextKey(path, searchPart);

  const exact = catalog.find((row) => row.routePattern === exactKey);
  if (exact) return exact;

  if (exactKey.includes('?')) {
    const pathOnly = catalog.find((row) => row.routePattern === path);
    if (pathOnly) return pathOnly;
  }

  const prefixHit = catalog
    .filter((row) => !row.routePattern.includes('?'))
    .sort((a, b) => b.routePattern.length - a.routePattern.length)
    .find((row) => path === row.routePattern || path.startsWith(`${row.routePattern}/`));

  return prefixHit ?? null;
}
