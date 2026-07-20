import { describe, expect, it } from 'vitest';
import {
  formatRouteKnowledgeForChat,
  knowledgeContextKey,
  resolveKnowledgeContext,
  resolveRouteKnowledge,
  ROUTE_KNOWLEDGE,
} from './RouteKnowledgeRegistry';

describe('RouteKnowledgeRegistry', () => {
  it('requires purpose, flow, reversals, correlations, and components on every entry', () => {
    for (const [path, entry] of Object.entries(ROUTE_KNOWLEDGE)) {
      expect(entry.title, path).toBeTruthy();
      expect(entry.purpose, path).toMatch(/\w+/);
      expect(entry.flow.length, path).toBeGreaterThan(0);
      expect(entry.reversals.length, path).toBeGreaterThan(0);
      expect(entry.correlations.length, path).toBeGreaterThan(0);
      expect(entry.components.length, path).toBeGreaterThan(0);
      for (const component of entry.components) {
        expect(component.name, `${path} component`).toBeTruthy();
        expect(component.description, `${path} ${component.name}`).toMatch(/\w+/);
        expect(component.dataOrigin, `${path} ${component.name}`).toMatch(/\w+/);
      }
    }
  });

  it('resolves longest prefix for nested floor receive', () => {
    const inbound = resolveRouteKnowledge('/inbound/receive?po=PO-1');
    expect(inbound?.title).toBe('Inbound Receive');
    expect(inbound?.reversals.join(' ')).toMatch(/undo|adjust|ledger/i);

    const returnsReceive = resolveRouteKnowledge('/returns/receive');
    expect(returnsReceive?.title).toBe('Returns Receive (Floor)');
  });

  it('formats chat system context with reversal emphasis', () => {
    const knowledge = resolveRouteKnowledge('/sales-orders');
    const block = formatRouteKnowledgeForChat('/sales-orders', knowledge);
    expect(block).toContain('System Context:');
    expect(block).toContain('Sales Orders');
    expect(block).toContain('Reversal mechanism:');
    expect(block).toContain('User Query:');
    expect(block.toLowerCase()).toContain('allocate');
  });

  it('serializes component statuses and dataOrigin for chatbot status questions', () => {
    const knowledge = resolveRouteKnowledge('/sales-orders');
    const block = formatRouteKnowledgeForChat('/sales-orders', knowledge);
    expect(block).toContain('Components:');
    expect(block).toContain('ALLOCATED=');
    expect(block).toMatch(/via SalesOrderService/i);
    expect(block).toContain('BACKORDERED=');
    expect(block).toContain('PARTIALLY_SHIPPED=');
  });

  it('returns null for unknown routes and a safe fallback chat block', () => {
    expect(resolveRouteKnowledge('/totally-unknown-xyz')).toBeNull();
    const block = formatRouteKnowledgeForChat('/totally-unknown-xyz', null);
    expect(block).toContain('No localized page playbook');
    expect(block).toContain('append-only');
  });

  it('builds settings tab knowledge keys from pathname + search', () => {
    expect(knowledgeContextKey('/settings', '?tab=operations')).toBe('/settings?tab=operations');
    expect(knowledgeContextKey('/settings/', 'tab=users')).toBe('/settings?tab=users');
    expect(knowledgeContextKey('/settings?tab=integrations')).toBe('/settings?tab=integrations');
    expect(knowledgeContextKey('/dashboard', '?x=1')).toBe('/dashboard');
    expect(knowledgeContextKey('/settings#hash', '?tab=mesh')).toBe('/settings?tab=mesh');
  });

  it('resolves settings tabs from search and prefers exact tab keys', () => {
    const ops = resolveKnowledgeContext('/settings', '?tab=operations');
    expect(ops?.title).toBe('Settings — Operations');
    expect(ops?.components.some((c) => c.name === 'Audit Log')).toBe(true);
    expect(ops?.components.some((c) => /adjustment limits/i.test(c.name))).toBe(true);

    const users = resolveKnowledgeContext('/settings', '?tab=users');
    expect(users?.title).toBe('Settings — Users');
    expect(users?.purpose).toMatch(/LBAC/i);
    expect(users?.components.some((c) => /OWNER|WAREHOUSE_MANAGER|PICKER/i.test(c.description))).toBe(
      true,
    );
    expect(
      users?.components.some((c) => c.columns?.some((col) => col.name === 'WAREHOUSE_MANAGER')),
    ).toBe(true);

    const integrations = resolveKnowledgeContext('/settings', '?tab=integrations');
    expect(integrations?.title).toBe('Settings — Integrations');
    expect(integrations?.purpose).toMatch(/e-commerce|webhook/i);

    const defaultSettings = resolveKnowledgeContext('/settings', '');
    expect(defaultSettings?.title).toMatch(/Organization settings|Profile/i);

    const profileTab = resolveKnowledgeContext('/settings', '?tab=profile');
    expect(profileTab?.title).toBe('Settings — Profile');

    const backCompat = resolveRouteKnowledge('/settings?tab=syncConflicts');
    expect(backCompat?.title).toBe('Settings — Sync Conflicts');
  });

  it('documents exhaustive statuses for PO, SO, cycle counts, and exceptions', () => {
    const poStatuses = ROUTE_KNOWLEDGE['/purchase-orders']!.components.find((c) => c.statuses)?.statuses;
    expect(poStatuses).toMatchObject({
      DRAFT: expect.any(String),
      SUBMITTED: expect.any(String),
      IN_TRANSIT: expect.any(String),
      PARTIALLY_RECEIVED: expect.any(String),
      RECEIVED: expect.any(String),
    });

    const soStatuses = ROUTE_KNOWLEDGE['/sales-orders']!.components.find((c) => c.statuses)?.statuses;
    expect(soStatuses).toMatchObject({
      DRAFT: expect.any(String),
      CONFIRMED: expect.any(String),
      ALLOCATED: expect.any(String),
      BACKORDERED: expect.any(String),
      PARTIALLY_SHIPPED: expect.any(String),
      SHIPPED: expect.any(String),
      CANCELLED: expect.any(String),
    });

    const countStatuses = ROUTE_KNOWLEDGE['/cycle-counts']!.components.find((c) => c.statuses)?.statuses;
    expect(countStatuses?.PENDING_MANAGER_REVIEW).toMatch(/\w+/);

    const exceptionStatuses = ROUTE_KNOWLEDGE['/exceptions']!.components.find(
      (c) => c.name === 'Exception board',
    )?.statuses;
    expect(exceptionStatuses).toMatchObject({
      OPEN: expect.any(String),
      RESOLVED: expect.any(String),
    });
  });

  it('falls back showroom nested routes and settings subroutes', () => {
    expect(resolveKnowledgeContext('/showroom/orders', '')?.title).toBe('B2B Showroom');
    expect(resolveKnowledgeContext('/settings/billing', '')?.title).toBe('Billing');
    expect(resolveKnowledgeContext('/settings/integrations', '')?.title).toBe('Integrations Hub');
  });
});
