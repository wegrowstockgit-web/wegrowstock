/**
 * Frontend mirror of system-wide troubleshooting playbooks (GraphRAG seed slugs).
 * Used for copilot starter prompts and regression docs — authoritative text lives in
 * {@code SupportKnowledgeSeed} on the API.
 */
export type SupportPlaybook = {
  slug: string;
  title: string;
  topics: string[];
  routes: string[];
  summary: string;
};

export const SUPPORT_KNOWLEDGE_CORPUS: SupportPlaybook[] = [
  {
    slug: 'ops-landed-cost-distribution',
    title: 'Landed cost into unit valuation',
    topics: ['landed cost', 'surcharge', 'receiving', 'PO'],
    routes: ['/purchase-orders', '/inbound/receive'],
    summary:
      'Freight and duty surcharges distribute across received units so inventory valuation stays accurate.',
  },
  {
    slug: 'ops-fefo-allocation-credit-holds',
    title: 'FEFO allocation and credit holds',
    topics: ['FEFO', 'allocate', 'credit limit', 'BACKORDERED'],
    routes: ['/sales-orders'],
    summary:
      'Allocation picks earliest-expiry lots first; credit holds park orders until billing clears.',
  },
  {
    slug: 'ops-append-only-ledger-reversals',
    title: 'Append-only ledger reversals',
    topics: ['ledger', 'ERROR_CORRECTION', 'reverse'],
    routes: ['/exceptions', '/products'],
    summary:
      'Never delete ledger rows — reverse via compensating ERROR_CORRECTION or the reverse API.',
  },
  {
    slug: 'ops-blind-cycle-count-escalation',
    title: 'Blind cycle counts',
    topics: ['cycle count', 'blind', 'PENDING_MANAGER_REVIEW'],
    routes: ['/cycle-counts'],
    summary:
      'Small variances auto-approve; large deltas escalate to PENDING_MANAGER_REVIEW.',
  },
  {
    slug: 'ops-offline-conflict-panel-resolve',
    title: 'Offline Conflict Panel',
    topics: ['offline', '409', 'Conflict Panel', 'Discard'],
    routes: ['/exceptions'],
    summary:
      'Managers Discard or Approve & Re-process parked offline mutations with a full audit trail.',
  },
  {
    slug: 'ops-status-codes-po-so-invoice-rma',
    title: 'Status codes across documents',
    topics: ['DRAFT', 'ALLOCATED', 'BACKORDERED', 'status'],
    routes: ['/purchase-orders', '/sales-orders', '/invoices'],
    summary:
      'Definitive meanings for PO, SO, Invoice, Production Order, and RMA status codes.',
  },
  {
    slug: 'ops-cross-dock-intercept',
    title: 'Cross-dock intercept',
    topics: ['cross-dock', 'backorder', 'staging'],
    routes: ['/inbound/receive', '/sales-orders'],
    summary: 'Inbound can bypass storage and feed open backorders into staging lanes.',
  },
];

export function playbooksForRoute(pathname: string): SupportPlaybook[] {
  return SUPPORT_KNOWLEDGE_CORPUS.filter((p) =>
    p.routes.some((r) => pathname === r || pathname.startsWith(r + '/')),
  );
}
