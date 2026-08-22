import { describe, expect, it } from 'vitest';
import type { DynamicPageKnowledge } from './dynamicTypes';
import { resolveDynamicPageKnowledge } from './resolveDynamicPageKnowledge';

function entry(routePattern: string, title = routePattern): DynamicPageKnowledge {
  return {
    id: routePattern,
    routePattern,
    category: 'Core',
    title,
    summary: 'Summary',
    rolePrivileges: 'Owners',
    keyActions: [],
    commonMistakes: [],
    proTip: null,
  };
}

describe('resolveDynamicPageKnowledge', () => {
  const catalog = [
    entry('/purchase-orders', 'Purchase Orders'),
    entry('/settings?tab=users', 'Settings — Users'),
    entry('/settings', 'Tenant Settings'),
    entry('/fulfillment', 'Fulfillment'),
  ];

  it('matches exact routes and settings tabs', () => {
    expect(resolveDynamicPageKnowledge(catalog, '/purchase-orders')?.title).toBe('Purchase Orders');
    expect(resolveDynamicPageKnowledge(catalog, '/settings', '?tab=users')?.title).toBe('Settings — Users');
    expect(resolveDynamicPageKnowledge(catalog, '/settings', '')?.title).toBe('Tenant Settings');
  });

  it('uses longest prefix for nested ids', () => {
    expect(resolveDynamicPageKnowledge(catalog, '/purchase-orders/abc-123')?.title).toBe('Purchase Orders');
    expect(resolveDynamicPageKnowledge(catalog, '/fulfillment/pack')?.title).toBe('Fulfillment');
  });

  it('returns null for unknown routes and empty catalogs', () => {
    expect(resolveDynamicPageKnowledge(catalog, '/totally-unknown')).toBeNull();
    expect(resolveDynamicPageKnowledge([], '/dashboard')).toBeNull();
    expect(resolveDynamicPageKnowledge(undefined, '/dashboard')).toBeNull();
  });
});
