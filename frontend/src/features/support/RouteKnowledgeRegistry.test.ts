import { describe, expect, it } from 'vitest';
import {
  formatRouteKnowledgeForChat,
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
      expect(Object.keys(entry.components).length, path).toBeGreaterThan(0);
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

  it('returns null for unknown routes and a safe fallback chat block', () => {
    expect(resolveRouteKnowledge('/totally-unknown-xyz')).toBeNull();
    const block = formatRouteKnowledgeForChat('/totally-unknown-xyz', null);
    expect(block).toContain('No localized page playbook');
    expect(block).toContain('append-only');
  });
});
