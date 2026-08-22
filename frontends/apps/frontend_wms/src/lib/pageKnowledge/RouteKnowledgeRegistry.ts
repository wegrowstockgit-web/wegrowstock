/**
 * Localized page knowledge for the Page Info overlay and support copilot context.
 * Keys are pathname prefixes (longest match wins). Settings tabs use full
 * `/settings?tab=` keys so the copilot can answer tab-specific questions.
 */

import i18n from '@/lib/i18n';

export type RouteKnowledgeColumn = {
  name: string;
  purpose: string;
};

/** Column dictionary: array form or map of column key → plain-English explanation. */
export type RouteKnowledgeColumns = RouteKnowledgeColumn[] | Record<string, string>;

export type RouteKnowledgeComponent = {
  name: string;
  description: string;
  /**
   * Plain-English operational source (never APIs, Java services, or table names).
   * Example: "Purchase orders created by purchasing or imported from suppliers."
   */
  dataOrigin: string;
  columns?: RouteKnowledgeColumns;
  /** Status badge → operational meaning */
  statuses?: Record<string, string>;
};

export interface PageAction {
  label: string;
  route: string;
  icon: string;
  variant?: 'primary' | 'secondary' | 'destructive';
}

export interface TroubleshootingStep {
  issue: string;
  solution: string;
  action: PageAction;
}

/** Hybrid overlay playbook: static teaching copy + click-to-execute actions. */
export interface PageKnowledge {
  title: string;
  description: string;
  markdown: string;
  quickActions: PageAction[];
  troubleshooting?: TroubleshootingStep[];
}

export type RouteKnowledge = {
  title: string;
  /** i18n playbook key under `pageHelp.playbooks.*` (EN/ES/FR). */
  i18nKey?: string;
  description?: string;
  markdown?: string;
  quickActions?: PageAction[];
  troubleshooting?: TroubleshootingStep[];
  purpose: string;
  /**
   * Machine role codes. Prefer {@link ResolvedRouteKnowledge.whoCanUse} for UI copy.
   * When omitted, {@link enrichRouteKnowledge} fills from the route key.
   */
  rolePermissions?: string[];
  /** @deprecated prefer stepByStepFlow via enrich — kept as authoring source */
  flow: string[];
  /** @deprecated prefer howToUndo via enrich — kept as authoring source */
  reversals: string[];
  correlations: string[];
  components: RouteKnowledgeComponent[];
  /** Optional on-screen acronym / term glossary (LPN, FEFO, DKIM, …). */
  glossary?: Record<string, string>;
};

/** Fully resolved playbook for overlay + copilot (human-facing fields filled). */
export type ResolvedRouteKnowledge = RouteKnowledge &
  PageKnowledge & {
    rolePermissions: string[];
    /** Human role labels, e.g. "Warehouse Managers, Floor Pickers". */
    whoCanUse: string[];
    /** Numbered operational steps (1…N). */
    stepByStepFlow: string[];
    /** Plain English undo / fix instructions. */
    howToUndo: string[];
    /** Page-level plain-English source summary. */
    dataOrigin: string;
  };

const OFFICE_ROLES = ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'] as const;
const FLOOR_ROLES = ['PICKER', 'WAREHOUSE_MANAGER', 'ADMIN', 'OWNER'] as const;
const ALL_OPS_ROLES = ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER'] as const;
const ADMIN_ROLES = ['OWNER', 'ADMIN'] as const;
const B2B_ROLES = ['B2B_CUSTOMER', 'OWNER', 'ADMIN'] as const;

/** Infer rolePermissions when an entry omits them (keeps the large corpus maintainable). */
export function rolePermissionsForRouteKey(routeKey: string): string[] {
  const key = (routeKey || '/').toLowerCase();
  if (key.startsWith('/showroom')) return [...B2B_ROLES];
  if (key.startsWith('/settings')) return [...ADMIN_ROLES, 'WAREHOUSE_MANAGER'];
  if (
    key.startsWith('/fulfillment')
    || key.startsWith('/inbound')
    || key.startsWith('/returns/receive')
    || key.startsWith('/manufacturing/terminal')
    || key.startsWith('/field')
    || key.startsWith('/issue-supplies')
    || key.startsWith('/rtls')
  ) {
    return [...FLOOR_ROLES];
  }
  if (key.startsWith('/reports') || key.startsWith('/import')) {
    return [...OFFICE_ROLES];
  }
  return [...ALL_OPS_ROLES];
}

export function normalizeColumns(columns?: RouteKnowledgeColumns): RouteKnowledgeColumn[] {
  if (!columns) return [];
  if (Array.isArray(columns)) return columns;
  return Object.entries(columns).map(([name, purpose]) => ({ name, purpose }));
}

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Owners',
  ADMIN: 'Administrators',
  WAREHOUSE_MANAGER: 'Warehouse Managers',
  PICKER: 'Floor Pickers',
  VIEWER: 'Viewers',
  B2B_CUSTOMER: 'B2B Buyers',
};

export function humanRoleLabels(roles: string[]): string[] {
  return roles.map((r) => ROLE_LABELS[r] ?? r);
}

/** Ensure every resolved playbook exposes human-facing fields (no developer jargon). */
/** Stable i18n key under `pageHelp.playbooks.*` for every registered route. */
export function playbookI18nKey(routeKey: string, explicit?: string): string {
  if (explicit) return explicit;
  const slug = routeKey
    .replace(/^\//, '')
    .replace(/[/?=&-]+/g, '_')
    .replace(/^_+|_+$/g, '');
  return slug || 'page';
}

export function enrichRouteKnowledge(
  routeKey: string,
  knowledge: RouteKnowledge,
): ResolvedRouteKnowledge {
  const rolePermissions =
    knowledge.rolePermissions && knowledge.rolePermissions.length > 0
      ? knowledge.rolePermissions
      : rolePermissionsForRouteKey(routeKey);
  const components = knowledge.components.map((c) => ({
    ...c,
    columns: normalizeColumns(c.columns),
  }));
  const pageOrigin =
    components[0]?.dataOrigin
    ?? 'Information your team enters on this screen or that syncs from connected storefronts.';
  const description = knowledge.description?.trim() || knowledge.purpose;
  const markdown = knowledge.markdown?.trim() || knowledge.flow.join('\n');

  return {
    ...knowledge,
    i18nKey: playbookI18nKey(routeKey, knowledge.i18nKey),
    description,
    markdown,
    quickActions: knowledge.quickActions ?? [],
    rolePermissions,
    whoCanUse: humanRoleLabels(rolePermissions),
    stepByStepFlow: knowledge.flow,
    howToUndo: knowledge.reversals,
    dataOrigin: pageOrigin,
    components,
  };
}

export const ROUTE_KNOWLEDGE: Record<string, RouteKnowledge> = {
  '/dashboard': {
    i18nKey: 'dashboard',
    title: 'Command Center',
    description: 'Your daily overview of warehouse operations, active tasks, and system health.',
    markdown:
      'The dashboard monitors pending shipments, incoming deliveries, and worker velocity. Use the quick actions below to jump directly into your daily queue.',
    quickActions: [
      { label: 'View My Tasks', route: '/tasks/my-queue', icon: 'ListTodo', variant: 'primary' },
      { label: 'Review Labor Analytics', route: '/dashboard/labor', icon: 'BarChart' },
    ],
    purpose:
      'Command center for live warehouse KPIs — stock value, low stock, open orders, work-queue cards, exceptions, and sync conflicts — so managers see what needs attention without digging into every module.',
    flow: [
      'Scan Headline KPIs (Stock Value, Low Stock Count, Open Orders) for red/amber signals.',
      'Work the Work Queue cards — Needs Allocation and Ready to Invoice first.',
      'Open Sync Conflicts or Exceptions banners when they appear.',
      'Drill into Sales Orders, Purchase Orders, or Fulfillment from the quick links.',
    ],
    reversals: [
      'Dashboard itself does not change inventory — reverse work on the source page (orders, exceptions, or sync conflicts).',
      'Dismissing a banner only hides the alert until the next refresh; it does not undo the underlying transaction.',
    ],
    correlations: [
      'Office side: headline numbers update from floor scans, allocations, and paid invoices from recent warehouse activity.',
      'Floor side: parked offline conflicts surface here for manager adjudication.',
    ],
    components: [
      {
        name: 'Headline KPIs',
        description:
          'Stock Value, Low Stock Count, and Open Orders — the three numbers managers glance at first each shift.',
        dataOrigin: 'Live warehouse totals refreshed from recent floor and office activity.',
        columns: [
          { name: 'Stock Value', purpose: 'Extended on-hand value in tenant currency.' },
          { name: 'Low Stock Count', purpose: 'SKUs at or below reorder point.' },
          { name: 'Open Orders', purpose: 'Sales orders still in flight (not shipped/closed/cancelled).' },
        ],
      },
      {
        name: 'Work Queue cards',
        description:
          'Actionable counts that open the next operational screen — especially Needs Allocation and Ready to Invoice.',
        dataOrigin: 'Work items waiting for your team — allocation, invoicing, and similar follow-ups.',
        columns: [
          {
            name: 'Needs Allocation',
            purpose: 'Confirmed sales orders waiting for stock reservation.',
          },
          {
            name: 'Ready to Invoice',
            purpose: 'Allocated or shipped orders that finance can bill.',
          },
        ],
      },
      {
        name: 'Sync conflict banner',
        description: 'Shortcut into the parked offline scan queue for manager review.',
        dataOrigin: 'Scans that need a manager review after an offline sync problem.',
      },
      {
        name: 'Live updates',
        description: 'Live invoice/order status without polling.',
        dataOrigin: 'Live status updates from orders and invoices as they change.',
      },
    ],
  },

  '/purchase-orders': {
    i18nKey: 'purchaseOrders',
    title: 'Purchase Orders',
    description: 'Order new stock from your suppliers.',
    markdown:
      'Draft purchase orders to restock your warehouse. Once a PO is confirmed, the receiving dock will be notified to expect the delivery.',
    quickActions: [
      { label: 'Draft New PO', route: '/purchase-orders/new', icon: 'FilePlus', variant: 'primary' },
      { label: 'View Supplier Directory', route: '/suppliers', icon: 'Users' },
    ],
    purpose:
      'Create and submit inbound supply contracts against approved suppliers so the floor can receive freight against expected lines.',
    rolePermissions: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    glossary: {
      LPN: 'License Plate Number — barcode identity for a pallet or carton.',
      PO: 'Purchase Order — inbound buy document against a supplier.',
      UOM: 'Unit of Measure (each, case, pallet).',
      'Landed cost': 'Freight, duty, and other charges rolled into unit inventory valuation.',
    },
    flow: [
      'Select a supplier and add lines (SKU, qty, unit cost, UOM).',
      'Save as Draft, then Submit when the buy is firm.',
      'Optionally attach landed cost (freight/customs) before or after submit.',
      'Mark In Transit when the vendor ships, then hand off to Floor Receive when the truck arrives.',
    ],
    reversals: [
      'Draft POs can be edited or deleted before submit.',
      'Submitted POs cannot silently erase receives — cancel open lines only if nothing has been received; otherwise reverse via Returns or a manager stock correction.',
      'Never delete a PO that already has receives posted; use RMA or credit notes instead.',
    ],
    correlations: [
      'Floor: submitted POs become the scan baseline on Inbound Receive.',
      'Suppliers: tokenized portal acknowledgments update promised ship dates here.',
      'ATP: receiving against this PO unlocks sellable stock for Sales Orders and B2B.',
    ],
    components: [
      {
        name: 'PO grid',
        description: 'Scrollable list of inbound documents and status chips.',
        dataOrigin: 'Purchase orders created by purchasing or imported from suppliers.',
        statuses: {
          DRAFT: 'Editable buy document — not yet sent to the supplier.',
          SUBMITTED: 'Firm order; floor may receive against expected lines.',
          IN_TRANSIT: 'Vendor confirmed shipment; freight is on the way.',
          PARTIALLY_RECEIVED: 'Some lines/qty received; remainder still open.',
          RECEIVED: 'All expected quantity received; PO is complete.',
        },
      },
      {
        name: 'Floor receive CTA',
        description: 'Opens receiving on the handheld for the selected purchase order.',
        dataOrigin: 'Open purchase orders ready for the floor to receive.',
      },
      {
        name: 'Landed cost',
        description: 'Distributes freight/customs across line unit costs.',
        dataOrigin: 'Freight and duty costs your purchasing team adds to a purchase order.',
      },
    ],
  },

  '/inbound/receive': {
    i18nKey: 'inboundReceive',
    title: 'Inbound Receiving',
    description: 'Check in newly arrived inventory from suppliers.',
    markdown:
      'Scan the incoming Purchase Order, verify the physical quantities match the invoice, and generate License Plates (LPNs) to put the items away on shelves.',
    quickActions: [
      { label: 'Scan Incoming PO', route: '/inbound/receive/scan', icon: 'Barcode', variant: 'primary' },
      { label: 'Start Put-Away Tasks', route: '/inbound/putaway', icon: 'ArrowDownToLine' },
    ],
    troubleshooting: [],
    rolePermissions: ['PICKER', 'WAREHOUSE_MANAGER', 'ADMIN', 'OWNER'],
    glossary: {
      LPN: 'License Plate Number scanned on the carton or pallet.',
      'Cross-dock': 'Bypass deep storage and divert stock straight to a staging lane for open backorders.',
      Putaway: 'Directed move from the dock into a storage or pick-face bin.',
    },
    purpose:
      'Scan freight into inventory: match PO → product → destination bin so stock on hand increases and waiting orders can allocate.',
    flow: [
      'Unlock the floor PIN if prompted.',
      'Scan the PO / ASN barcode so expected lines appear.',
      'Scan each product (capture lot/expiry/serial when prompted).',
      'Confirm quantity, then scan the putaway bin (or follow a cross-dock staging overlay).',
      'Confirm — on-hand quantity updates immediately.',
    ],
    reversals: [
      'Use the 5-second undo buffer on a mis-scan before it commits to the offline queue.',
      'After commit, do not invent a negative scan — open Exceptions / Returns or ask a manager to post a stock correction from the office.',
      'Skip & Flag is for outbound pick problems, not for reversing a successful receive.',
      'Cross-dock misroutes: transfer stock from staging back to reserve — never erase stock history.',
    ],
    correlations: [
      'Office: RECEIVE unlocks allocation and clears backorders (including cross-dock auto-allocate).',
      'Compliance: lot/serial captured here feeds Lot Trace genealogy.',
      'Offline: failed business rules park in Sync Conflicts for managers — pickers keep working.',
    ],
    components: [
      {
        name: 'Barcode wedge',
        description: 'Barcode scan field for PO, item, and bin.',
        dataOrigin: 'Barcode scans entered on the receiving screen.',
      },
      {
        name: 'Expected lines',
        description: 'PO lines remaining to receive.',
        dataOrigin: 'Purchase orders created by purchasing or imported from suppliers.',
        statuses: {
          SUBMITTED: 'PO ready for first receipt.',
          IN_TRANSIT: 'Expected freight in motion.',
          PARTIALLY_RECEIVED: 'Continue scanning remaining qty.',
          RECEIVED: 'Nothing left to receive on this PO.',
        },
      },
      {
        name: 'Cross-dock overlay',
        description: 'Routes to staging when open backorder demand exists.',
        dataOrigin: 'Rules that send urgent stock straight to shipping staging when customers are waiting.',
      },
    ],
  },

  '/sales-orders': {
    i18nKey: 'salesOrders',
    title: 'Sales Orders',
    description: 'Manage customer orders awaiting processing and shipment.',
    markdown:
      'Orders drop in here from Shopify or B2B portals. To process an order, it must have sufficient inventory to be Allocated, after which it drops into the Fulfillment queue.',
    quickActions: [
      { label: 'Create Manual Order', route: '/sales-orders/new', icon: 'Plus' },
      { label: 'Go to Fulfillment', route: '/fulfillment', icon: 'Package', variant: 'primary' },
    ],
    troubleshooting: [
      {
        issue: 'An order is stuck in "Unallocated".',
        solution: 'You lack inventory for the requested items. Run a replenishment or adjust stock.',
        action: { label: 'Check Inventory Levels', route: '/inventory', icon: 'Search' },
      },
    ],
    purpose:
      'Confirm customer demand, reserve earliest-expiry lots, and release picking waves so floor operators can fulfill outbound orders.',
    rolePermissions: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    glossary: {
      FEFO: 'First Expired, First Out — allocation prefers lots with the earliest expiry.',
      ATP: 'Available to Promise — sellable qty after reservations.',
      Wave: 'Batch of pick tasks released to handhelds after allocation.',
      BACKORDERED: 'Confirmed demand that could not be fully reserved from on-hand stock.',
      'Un-allocate': 'Release reserved stock back to available-to-promise without erasing history.',
    },
    flow: [
      'Confirm a DRAFT/pending order.',
      'Click Allocate to reserve on-hand (or leave BACKORDERED if stock is short).',
      'Generate / optimize / release a picking wave for ALLOCATED orders.',
      'Track PARTIALLY_SHIPPED → SHIPPED as the floor packs out.',
    ],
    reversals: [
      'Un-allocate / Cancel releases reserved stock back to available without creating a shipment.',
      'Cancel before pick to free reserved lots; after picks, reverse via shipment void + stock correction — never erase shipped history.',
      'Credit-hold freezes are cleared by raising the customer credit limit or reducing order value, not by forcing allocate.',
    ],
    correlations: [
      'Floor: released waves become pick tasks on Fulfillment.',
      'B2B portal: showroom orders enter this same pipeline; buyers only see status chips.',
      'Finance: shipment and invoicing depend on successful allocation/ship.',
    ],
    components: [
      {
        name: 'Allocation header',
        description: 'Confirm / Allocate / Wave actions for the selected order.',
        dataOrigin: 'Sales orders after stock has been reserved for picking.',
      },
      {
        name: 'Status chips',
        description: 'Lifecycle of the outbound order from draft through ship or cancel.',
        dataOrigin: 'Sales orders entered by your office team or connected storefronts.',
        statuses: {
          DRAFT: 'Order is being created and has not yet reserved stock.',
          CONFIRMED: 'Customer demand accepted; ready to allocate.',
          ALLOCATED: 'Physical inventory is reserved in the warehouse and ready for picking.',
          BACKORDERED: 'Waiting for incoming supplier shipment before stock can be reserved.',
          PARTIALLY_SHIPPED: 'Some lines shipped; remainder still open.',
          SHIPPED: 'All shippable qty left the warehouse.',
          CANCELLED: 'Order stopped; reserved stock is released without shipping.',
        },
      },
      {
        name: 'Wave controls',
        description: 'Generate, optimize path, and release to pickers.',
        dataOrigin: 'Picking waves released so handheld devices receive pick tasks.',
      },
    ],
  },

  '/fulfillment': {
    i18nKey: 'fulfillment',
    title: 'Fulfillment & Picking',
    description: 'Group orders into waves, pick items from shelves, and pack them for shipping.',
    markdown:
      'Step 1: Generate a wave to group similar orders.\nStep 2: Claim the wave to your scanner.\nStep 3: Pick the items.\nStep 4: Pack and print shipping labels.',
    quickActions: [
      { label: 'Generate Picking Wave', route: '/fulfillment/waves/new', icon: 'Waves', variant: 'primary' },
      { label: 'Start Picking', route: '/fulfillment/pick', icon: 'Scan' },
      { label: 'Pack & Ship', route: '/fulfillment/pack', icon: 'Box' },
    ],
    troubleshooting: [
      {
        issue: 'I picked a damaged item.',
        solution: 'Flag the item as an exception. The system will direct you to pick a replacement.',
        action: {
          label: 'Go to Exceptions Desk',
          route: '/exceptions',
          icon: 'AlertTriangle',
          variant: 'destructive',
        },
      },
    ],
    purpose:
      'Execute pick, pack, and ship scans against released wave tasks with glove-friendly, PIN-locked hardware scanning.',
    rolePermissions: ['PICKER', 'WAREHOUSE_MANAGER', 'ADMIN', 'OWNER'],
    glossary: {
      Wedge: 'USB barcode scanner that types into the focused scan field.',
      'Skip & Flag': 'Mark a pick line unpickable and release its allocation without inventing stock.',
      PIN: 'Shift unlock code on shared warehouse hardware.',
    },
    flow: [
      'Unlock shift PIN.',
      'Claim a batch / task from the wave.',
      'Scan the directed location and SKU (the system blocks accidental double-posts).',
      'At pack, cartonize and capture scale weight, then print the carrier label.',
    ],
    reversals: [
      'Mis-scan: use the short undo window before the scan is saved offline.',
      'Empty bin / damaged stock: Skip & Flag — frees the allocation without inventing a false stock change.',
      'Wrong pick already posted: stop and escalate; managers reverse via a stock correction or void the shipment — never erase stock history.',
      'Quarantined offline conflicts: managers resolve from Sync Conflicts; do not re-scan the same line until resolved.',
    ],
    correlations: [
      'Office: each pick scan consumes allocations and feeds ship/label and finance updates.',
      'Task interleaving may inject COUNT/PUTAWAY between picks to reduce travel.',
    ],
    components: [
      {
        name: 'Scan wedge field',
        description: 'Primary barcode scan field for pick, receive, and count modes.',
        dataOrigin: 'Pick, pack, and ship scans from the floor handheld.',
      },
      {
        name: 'Skip & Flag',
        description: 'Exception path that shunts allocations for office review without inventing a stock correction.',
        dataOrigin: 'Lines pickers marked as Skip & Flag for manager follow-up.',
      },
      {
        name: 'Quarantine badge',
        description: 'Local offline conflicts awaiting manager review.',
        dataOrigin: 'Parked offline scans waiting for a manager decision.',
      },
    ],
  },

  '/products': {
    i18nKey: 'products',
    title: 'Product Catalog',
    description: 'The master database of everything you sell and store.',
    markdown:
      'Manage SKUs, barcodes, Unit of Measure (UoM) conversions, and product imagery. Changes here sync globally across all facilities.',
    quickActions: [
      { label: 'Add New Product', route: '/products/new', icon: 'PackagePlus', variant: 'primary' },
      { label: 'Import from CSV', route: '/import', icon: 'Upload' },
    ],
    purpose:
      'Maintain the item master (variants, dimensions, packaging, temperature zones) and view on-hand, allocated, and available-to-promise quantities.',
    flow: [
      'Search or scroll the product grid (or mobile cards).',
      'Edit dimensions, UOM, and compliance fields as needed.',
      'Use Columns / Density to personalize the view — layout changes do not change stock.',
      'Open Import for bulk catalog loads.',
    ],
    reversals: [
      'Catalog field edits can be patched again; they do not reverse inventory.',
      'To reverse quantity truth, use Cycle Counts, stock correction, or Ledger History Undo — never edit on-hand as a free-text field.',
      'Import mistakes: re-import corrected rows or adjust variants; do not erase stock history.',
    ],
    correlations: [
      'Cartonization and shipping use dimensions from this master.',
      'Floor scans and office allocations read available-to-promise from quantities kept in sync after each movement.',
    ],
    components: [
      {
        name: 'Product grid / Mobile cards',
        description: 'Responsive catalog view with on-hand / allocated / ATP.',
        dataOrigin: 'Product catalog with current on-hand quantities.',
      },
      {
        name: 'Column visibility',
        description: 'Pin/hide fields without changing stock.',
        dataOrigin: 'Your personal column and layout preferences on this device.',
      },
      {
        name: 'Import button',
        description: 'Opens the bulk ingestion wizard.',
        dataOrigin: 'Spreadsheets uploaded by your team to create or update records.',
      },
    ],
  },

  '/inventory/ledger': {
    title: 'Inventory Ledger',
    purpose:
      'Review stock movements (receive, adjust, ship, transfer, assemble). History is never erased — corrections add a new row.',
    flow: [
      'Filter by SKU, location, date, or movement type.',
      'Open a row to see reason code, actor, lot/serial, and references.',
      'Use Undo only on reversible rows when policy allows.',
    ],
    reversals: [
      'Prefer the built-in Undo / reverse-transaction action — it posts a compensating stock correction row attributed to you.',
      'Never delete or rewrite historical stock movements (compliance / recall rules).',
      'If Undo is hidden, post an explicit stock correction with a clear reason from the office adjust flow.',
    ],
    correlations: [
      'Every floor scan and office allocate/ship eventually appears here.',
      'Lot Trace walks these rows recursively for recall response.',
    ],
    components: [
      {
        name: 'History table',
        description: 'Chronological movements with reverse affordances.',
        dataOrigin: 'Stock movement history shown for managers (every receive, pick, and adjust).',
        columns: [
          { name: 'Type', purpose: 'RECEIVE, stock correction, SHIP, TRANSFER, ASSEMBLY, etc.' },
          { name: 'Reason code', purpose: 'Business justification stamped on each append.' },
          { name: 'Actor', purpose: 'User or system name that posted the row.' },
        ],
      },
      {
        name: 'Reason codes',
        description: 'Business justification stamped on each append.',
        dataOrigin: 'Approved reasons managers pick when correcting stock.',
      },
    ],
  },

  '/cycle-counts': {
    i18nKey: 'cycleCounts',
    title: 'Cycle Counts',
    description: 'Audit your physical shelves to ensure software accuracy.',
    markdown:
      'Generate blind counts for your team. If the physical count differs from the software ledger, a manager must approve the variance.',
    quickActions: [
      { label: 'Generate Blind Count', route: '/cycle-counts/new', icon: 'ClipboardList', variant: 'primary' },
      { label: 'Review Variances', route: '/cycle-counts/variances', icon: 'Scale' },
    ],
    purpose:
      'Blind physical counts that reconcile on-hand to reality. Small variances auto-adjust; large ones escalate to manager review.',
    flow: [
      'Open a count task on the scanner (expected qty hidden).',
      'Enter the physical quantity and confirm.',
      'Managers approve Pending manager review variances from the office board.',
    ],
    reversals: [
      'Before confirm, clear the entry and re-count.',
      'After an auto-adjust posts, reverse with a manager stock correction — do not re-count the same line to “cancel”.',
      'Rejecting a variance leaves the slot locked until a correct count or disposition is recorded.',
    ],
    correlations: [
      'Approved counts write a stock correction and refresh on-hand quantities.',
      'Pickers should not invent adjusts outside the count workflow.',
    ],
    components: [
      {
        name: 'Blind count input',
        description: 'No expected qty shown — reduces confirmation bias.',
        dataOrigin: 'Cycle count worksheets and variance approvals.',
      },
      {
        name: 'Variance queue',
        description: 'Office approval for high-impact deltas before a stock correction posts.',
        dataOrigin: 'Cycle count worksheets and variance approvals.',
        statuses: {
          PENDING: 'Count submitted; variance evaluation not finished.',
          AUTO_APPROVED: 'Variance within threshold — stock correction posted automatically.',
          PENDING_MANAGER_REVIEW: 'Variance exceeds limit — waiting on manager approve/reject.',
          APPROVED: 'Manager approved; compensating stock correction posted.',
          RECOUNT_REQUESTED: 'Manager sent the line back for another physical count.',
        },
      },
    ],
  },

  '/exceptions': {
    i18nKey: 'exceptions',
    title: 'Exceptions Desk',
    description: 'Resolve hardware conflicts, damaged goods, and picking errors.',
    markdown:
      'When workers flag issues on the floor or devices lose internet connection, the errors land here for management resolution.',
    quickActions: [
      { label: 'Review Pending Exceptions', route: '/exceptions/pending', icon: 'AlertCircle', variant: 'primary' },
      { label: 'Resolve Sync Conflicts', route: '/exceptions?tab=sync', icon: 'WifiOff' },
    ],
    purpose:
      'Resolve floor Skip & Flag incidents and (on the Sync tab) review parked offline scan conflicts without erasing stock history.',
    rolePermissions: ['WAREHOUSE_MANAGER', 'ADMIN', 'OWNER'],
    glossary: {
      'Needs review': 'A scan could not finish after reconnecting — it is waiting for a manager.',
      'Conflict Panel': 'Manager screen to Discard or Approve & Re-process parked offline scans.',
      'Stock correction': 'A manager-approved fix that undoes a mistaken stock movement without erasing history.',
    },
    flow: [
      'Open an OPEN fulfillment exception.',
      'Investigate the bin / damage, then Resolve with disposition.',
      'Optionally post a stock correction only after physical truth is known.',
      'On Sync Conflicts: correct the highlighted fields and Approve & Re-process, or Discard.',
    ],
    reversals: [
      'Resolving an exception does not automatically restore the original allocation — re-allocate the sales order if still needed.',
      'Discarding a sync conflict permanently drops the parked scan; Approve posts a stock correction under the manager name.',
      'Do not reverse by re-flagging the same allocation blindly.',
    ],
    correlations: [
      'Pickers create exceptions to keep waves moving; managers close the loop.',
      'Sync Conflicts tie offline floor work to the approving manager for compliance.',
    ],
    components: [
      {
        name: 'Exception board',
        description: 'Fulfillment Skip & Flag incidents awaiting disposition.',
        dataOrigin: 'Lines pickers marked as Skip & Flag for manager follow-up.',
        statuses: {
          OPEN: 'Picker flagged the allocation; office has not closed it yet.',
          RESOLVED: 'Manager applied disposition; wave can continue after re-allocate if needed.',
        },
      },
      {
        name: 'Sync Conflicts panel',
        description: 'Conflict resolution form for parked offline scans.',
        dataOrigin: 'Parked offline scans managers can discard or approve.',
        statuses: {
          PARKED: 'An offline scan could not finish and is waiting for a manager.',
          APPROVED: 'Manager corrected fields and re-processed with a stock correction under the manager name.',
          DISCARDED: 'Parked scan permanently dropped — do not re-scan the same line expecting a duplicate post.',
        },
      },
    ],
  },

  '/replenishments': {
    title: 'Replenishments',
    purpose:
      'Move stock from reserve/bulk into pick faces so outbound waves do not starve.',
    flow: [
      'Review suggested movements (min/max rules + predictive triggers).',
      'Scan source, scan destination, confirm the TRANSFER.',
    ],
    reversals: [
      'Before confirm, cancel the task.',
      'After confirm, reverse with an opposite TRANSFER (pick face → reserve) documented as a replenishment correction — do not delete the original TRANSFER ledger rows.',
    ],
    correlations: [
      'Prevents pick-line stockouts that would otherwise become Skip & Flag exceptions.',
      'Predictive worker looks ~48h ahead at wave demand.',
    ],
    components: [
      {
        name: 'Replenishment queue',
        description: 'Prioritized move tasks from reserve into pick faces.',
        dataOrigin: 'Suggested moves from reserve storage into pick faces.',
      },
      {
        name: 'Confirm scan pair',
        description: 'Source + destination barcodes that post a TRANSFER.',
        dataOrigin: 'Stock moves between bins recorded by authorized users.',
      },
    ],
  },

  '/customers': {
    title: 'Customers',
    purpose: 'Maintain customer master data and credit lines that gate outbound allocation.',
    flow: [
      'Create or edit a customer profile.',
      'Set or adjust the line-of-credit limit.',
      'Link ship-to addresses used on sales orders.',
    ],
    reversals: [
      'Credit limit changes take effect on the next allocate — lower a limit carefully if open orders exist.',
      'Deactivating a customer does not cancel open SOs; cancel those orders separately.',
    ],
    correlations: [
      'Over-limit allocate attempts freeze orders for review.',
      'B2B showroom price lists and portal orders bind to these customer records.',
    ],
    components: [
      {
        name: 'Credit line',
        description: 'Exposure check during confirm/allocate.',
        dataOrigin: 'Customer credit limits that can hold orders before allocation.',
      },
      {
        name: 'Customer grid',
        description: 'Accounts master list with ship-to links.',
        dataOrigin: 'Customer records maintained by your office team.',
      },
    ],
  },

  '/invoices': {
    title: 'Invoices',
    purpose: 'Track invoices and payment state; when a payment clears, the invoice shows PAID for finance.',
    flow: [
      'Review AR aging and open invoices.',
      'Send payment requests when configured.',
      'Watch live PAID updates after payment settles.',
    ],
    reversals: [
      'Do not manually flip PAID without a finance process — void or credit in accounting and let finance tools reconcile.',
      'Refunds are accounting events, not inventory reversals; stock returns use the Returns module.',
    ],
    correlations: [
      'Paid invoices may release credit exposure for new allocations.',
      'Owners see the same PAID signal on the dashboard live feed.',
    ],
    components: [
      {
        name: 'Invoice grid',
        description: 'AR status and amounts.',
        dataOrigin: 'Invoices created after orders ship or are ready to bill.',
        statuses: {
          DRAFT: 'Invoice not yet sent to the customer.',
          OPEN: 'Awaiting payment.',
          PAID: 'Payment provider or accounting sync marked settled.',
          VOID: 'Cancelled for finance reasons — does not reverse warehouse stock.',
        },
      },
      {
        name: 'Live payment status',
        description: 'Turns green when payment succeeds.',
        dataOrigin: 'Payment status updates from your payment provider.',
      },
    ],
  },

  '/suppliers': {
    title: 'Suppliers',
    purpose: 'Vendor master — terms, lead times, quality ratings, and envelope-encrypted banking details.',
    flow: [
      'Create or edit a supplier profile.',
      'Set lead times that feed replenishment planning.',
      'Store banking details (shown masked after save).',
    ],
    reversals: [
      'Correct master data with another edit; encryption vault versions prior secrets.',
      'Deactivate rather than delete suppliers tied to historical POs.',
    ],
    correlations: [
      'PO creation requires an approved supplier.',
      'Lead times influence automated replenishment suggestions.',
    ],
    components: [
      {
        name: 'Supplier form',
        description: 'Terms, contacts, banking.',
        dataOrigin: 'Supplier records maintained by purchasing.',
      },
      {
        name: 'Masked IBAN',
        description: 'Last-4 display after vault encrypt.',
        dataOrigin: 'Protected supplier contact details visible only to authorized roles.',
      },
    ],
  },

  '/returns': {
    title: 'Returns / RMA',
    purpose: 'Authorize customer returns, then receive and disposition (restock vs scrap) on the floor.',
    flow: [
      'Create and approve an RMA in the office.',
      'Floor receives against the RMA on /returns/receive.',
      'Disposition RESTOCK (often via quarantine) or SCRAP.',
      'Release from quarantine when inspection passes.',
    ],
    reversals: [
      'Cancel an unreceived RMA before floor intake.',
      'Restocked units that should not sell: move back to quarantine or scrap with a stock correction — do not erase the receive.',
    ],
    correlations: [
      'Restock increases sellable ATP; scrap does not.',
      'Finance may issue credits separately from inventory disposition.',
    ],
    components: [
      {
        name: 'RMA board',
        description: 'Office approval workflow for customer returns.',
        dataOrigin: 'Customer return (RMA) documents.',
      },
      {
        name: 'Disposition controls',
        description: 'RESTOCK vs SCRAP after physical intake.',
        dataOrigin: 'Returned goods that put stock back into inventory.',
        statuses: {
          RESTOCK: 'Return units to sellable (often via quarantine release).',
          SCRAP: 'Write off units — no ATP increase.',
        },
      },
    ],
  },

  '/returns/receive': {
    title: 'Returns Receive (Floor)',
    purpose: 'Scan returned goods against an approved RMA and apply quarantine-aware disposition.',
    flow: [
      'Open the RMA on the handheld.',
      'Scan the returned item and confirm disposition path.',
      'Complete putaway to quarantine or scrap location as directed.',
    ],
    reversals: [
      'Mis-scan undo window applies before commit.',
      'After restock, only managers release/scrap with attributed ledger moves.',
    ],
    correlations: [
      'Closes RMA lines visible to office Returns.',
      'Quarantine release is an office decision, not a second floor invent.',
    ],
    components: [
      {
        name: 'RMA scanner',
        description: 'Matches returned GTIN/lot to the authorized return.',
        dataOrigin: 'Return receipts scanned on the floor.',
      },
    ],
  },

  '/compliance/lot-trace': {
    title: 'Lot Trace',
    purpose:
      'Read-only genealogy of a lot/serial across Supplier → PO → Receive → Assembly → Ship → Customer for recall and FSMA/DSCSA response.',
    flow: [
      'Enter the lot or serial number.',
      'Review the recursive ledger chain.',
      'Export CSV for regulators or customers when needed.',
    ],
    reversals: [
      'Lot Trace is read-only — there is nothing to undo on this page.',
      'Correct source data via returns, adjusts, or re-ship processes elsewhere.',
    ],
    correlations: [
      'VIEWER role is enough; operational roles supply the underlying ledger writes.',
    ],
    components: [
      {
        name: 'Genealogy graph/table',
        description: 'Parent/child stock history for recall response.',
        dataOrigin: 'Lot and serial history for recalls and quality checks.',
      },
      {
        name: 'CSV export',
        description: 'Audit package download.',
        dataOrigin: 'Downloadable lot/serial reports for auditors.',
      },
    ],
  },

  '/rtls': {
    title: 'RTLS map',
    purpose: 'Spatial digital twin — live picker positions, congestion heat, and walkable edges for wayfinding.',
    flow: [
      'Open the map and watch live position updates.',
      'Inspect the 7-day heatmap of stock movement activity.',
      'Adjust coordinates / edges when the physical layout changes.',
    ],
    reversals: [
      'Coordinate edits can be patched again; they do not reverse inventory.',
      'Heatmap history is analytical — not a transaction log to undo.',
    ],
    correlations: [
      'A* pick pathing and task interleaving consume this graph.',
      'Floor scans feed both ledger heat and live tags.',
    ],
    components: [
      {
        name: 'Spatial canvas',
        description: 'Bins + picker dots on the warehouse graph.',
        dataOrigin: 'Live device or asset positions on the warehouse map.',
      },
      {
        name: 'Heat overlay',
        description: 'Congestion from recent movements.',
        dataOrigin: 'Heatmap and dwell summaries for the floor map.',
      },
    ],
  },

  '/manufacturing/boms': {
    title: 'Bills of Materials',
    purpose: 'Define assembly recipes (components, operations, co-products) that drive production orders.',
    flow: [
      'Create a BOM for a finished SKU.',
      'Add component lines, operations, and outputs.',
      'Save — production orders will consume this recipe.',
    ],
    reversals: [
      'Edit or version the BOM before orders allocate against it.',
      'After production has run, do not delete historical BOMs tied to completed assemblies; supersede with a new revision.',
    ],
    correlations: [
      'Component availability is checked when production orders allocate.',
    ],
    components: [
      {
        name: 'BOM editor',
        description: 'Components, ops, outputs for a finished SKU.',
        dataOrigin: 'Bills of materials that define what goes into a finished good.',
      },
    ],
  },

  '/manufacturing/orders': {
    title: 'Production Orders',
    purpose: 'Schedule work orders and allocate raw components so sales picks cannot steal reserved materials.',
    flow: [
      'Create a production order from a BOM.',
      'Allocate components.',
      'Release to the Production Terminal for assembly.',
    ],
    reversals: [
      'Deallocate components before assembly to return raw materials to ATP.',
      'After an assemble posts, reverse with compensating assembly or stock correction entries — never erase completed production history.',
    ],
    correlations: [
      'Locks components away from outbound sales picks.',
      'Terminal labor timesheets attach cost to the order.',
    ],
    components: [
      {
        name: 'Allocate Components',
        description: 'Row-locks raw levels for the run.',
        dataOrigin: 'Manufacturing work orders.',
        statuses: {
          DRAFT: 'Work order created; components not reserved.',
          COMPONENTS_ALLOCATED: 'Raw materials locked for the run.',
          WIP: 'Floor assembly in progress.',
          COMPLETED: 'Finished goods received; components consumed.',
          CANCELLED: 'Order stopped; component reservations released.',
        },
      },
    ],
  },

  '/manufacturing/terminal': {
    title: 'Production Terminal',
    purpose: 'Floor assembly station — start/stop labor, consume components, mint finished goods labels.',
    flow: [
      'Scan the work order and Start timesheet.',
      'Complete the assembly run.',
      'Stop timesheet and post assemble — components are consumed and finished goods received.',
    ],
    reversals: [
      'Stop a timesheet without assemble if the run aborts (labor cost may still apply).',
      'Wrong assemble: manager posts a compensating assembly or stock correction under their name.',
    ],
    correlations: [
      'Finished goods become allocatable for sales immediately after assemble.',
    ],
    components: [
      {
        name: 'Timesheet controls',
        description: 'Labor duration × rate attached to the production order.',
        dataOrigin: 'Shop-floor terminal scan steps for production.',
      },
      {
        name: 'Assemble action',
        description: 'Dual ledger write (consume components + receive finished goods).',
        dataOrigin: 'Assembly completions that consume components and make finished goods.',
      },
    ],
  },

  '/issue-supplies': {
    title: 'Issue Supplies',
    purpose: 'Internal consumption against a cost center — deducts stockroom qty without creating a customer SO.',
    flow: [
      'Select the cost center.',
      'Scan the supply SKU and confirm issue.',
    ],
    reversals: [
      'Before confirm, cancel the issue.',
      'After confirm, reverse with a positive stock correction to the stockroom attributed to the manager, referencing the original issue.',
    ],
    correlations: [
      'Charges the cost center budget; does not affect customer allocations.',
    ],
    components: [
      {
        name: 'Cost center picker',
        description: 'Budget clearance gate before issue.',
        dataOrigin: 'Cost centers and their internal supply usage.',
      },
      {
        name: 'Issue confirm',
        description: 'stock correction ledger write against the stockroom.',
        dataOrigin: 'Internal issue of supplies to cost centers or jobs.',
      },
    ],
  },

  '/field/truck': {
    title: 'Technician Truck',
    purpose: 'Consume van stock (VEHICLE location) on-site; low stock signals depot replenishment.',
    flow: [
      'Scan components used on the job.',
      'Confirm consumption from the assigned vehicle location.',
      'Work offline if needed — queue replays on reconnect.',
    ],
    reversals: [
      'Undo window before offline queue commit.',
      'After sync, reverse via depot stock correction/TRANSFER back onto the van with manager reason codes.',
    ],
    correlations: [
      'Reorder-point triggers truck replenishment from the warehouse.',
    ],
    components: [
      {
        name: 'Van scan field',
        description: 'Consumes from VEHICLE location on the tech truck.',
        dataOrigin: 'Stock assigned to service trucks and field techs.',
      },
    ],
  },

  '/reports': {
    title: 'Reports',
    purpose: 'Financial and operational analytics (profit, COGS, turns) over RLS-scoped warehouse data.',
    flow: [
      'Open the analysis board you need.',
      'Filter by date / warehouse.',
      'Export or screenshot for leadership packs.',
    ],
    reversals: [
      'Reports are read-only — reverse underlying transactions on operational pages.',
    ],
    correlations: [
      'Headline KPIs may refresh on a short delay rather than updating every second.',
    ],
    components: [
      {
        name: 'Recharts boards',
        description: 'Visual analytics for profit, COGS, and turns.',
        dataOrigin: 'Saved operational reports and KPI snapshots.',
      },
    ],
  },

  '/settings': {
    i18nKey: 'settings',
    title: 'Tenant Settings',
    description: 'Configure integrations, hardware, and access controls.',
    markdown:
      'Manage your connected Shopify stores, configure Bluetooth scales and Zebra scanners, and define Role-Based Access Control (RBAC) permissions for your staff.',
    quickActions: [
      { label: 'Connect Integration', route: '/settings/integrations', icon: 'Link', variant: 'primary' },
      { label: 'Configure Hardware', route: '/settings/scanner', icon: 'Printer' },
      { label: 'Manage Users', route: '/settings/roles', icon: 'Shield' },
    ],
    purpose:
      'Tenant-wide configuration hub — profile defaults, users, warehouses, inventory rules, documents, Retail POS, security, reconciliation, accounting, integrations, partner mesh, operations, sync conflicts, and cost centers.',
    flow: [
      'Open the tab that matches the change you need (Operations for floor rules, Users for roles and warehouse access).',
      'Save — audit_log records the actor and JSON diff.',
      'Confirm floor devices pick up the new rule on next action.',
    ],
    reversals: [
      'Toggle the rule back; every change is recorded in the audit history (nothing is silently wiped).',
      'Fintech / billing subsections are OWNER-scoped — reverse subscription changes in the billing portal if needed.',
    ],
    correlations: [
      'Blind receiving and variance thresholds change what pickers may post without manager review.',
      'User warehouse assignments scope which bins and documents each role can see.',
    ],
    components: [
      {
        name: 'Settings tabs',
        description: 'Profile, Users, Warehouses, Inventory, Documents, Retail POS, Security, Reconciliation, Accounting, Integrations, Mesh, Operations, Sync Conflicts, Cost Centers.',
        dataOrigin: 'Company settings your administrators maintain.',
      },
      {
        name: 'Operations toggles',
        description: 'Global floor policy (blind receiving, adjustment limits, scanner options).',
        dataOrigin: 'Floor rules plus a history of who changed them.',
      },
      {
        name: 'Users tab',
        description: 'Role and warehouse assignments.',
        dataOrigin: 'User invitations and role assignments.',
      },
    ],
  },

  '/settings?tab=profile': {
    title: 'Settings — Profile',
    purpose: 'Your user profile and the default organization name shown across the app.',
    flow: [
      'Update display name, contact, and locale preferences.',
      'Save — changes apply to your session immediately.',
    ],
    reversals: [
      'Edit the fields again; profile edits do not touch inventory or roles.',
    ],
    correlations: [
      'Org-level branding and legal name may also appear on documents generated from the Documents tab.',
    ],
    components: [
      {
        name: 'Profile form',
        description: 'Personal profile and notification preferences for the signed-in user.',
        dataOrigin: 'Your signed-in profile details.',
      },
    ],
  },

  '/settings?tab=users': {
    title: 'Settings — Users',
    purpose:
      'Invite and manage company users, assign roles (Owner, Admin, Warehouse Manager, Picker, Viewer, B2B Customer), and scope which warehouses each person can access.',
    flow: [
      'Invite a user or open an existing account.',
      'Assign one or more roles that match their job.',
      'Check the warehouses they may access — pickers only see assigned buildings.',
      'Save — the next login enforces the new capabilities.',
    ],
    reversals: [
      'Remove a role or warehouse checkbox and save again.',
      'Deactivate rather than delete users tied to historical stock movements.',
      'OWNER cannot be casually demoted — transfer ownership first.',
    ],
    correlations: [
      'Warehouse assignments control which bins, waves, and documents appear for managers and pickers.',
      'ADMIN/OWNER can change tenant rules; PICKER cannot open Operations toggles.',
    ],
    components: [
      {
        name: 'User grid',
        description: 'Tenant accounts with invite status and role chips.',
        dataOrigin: 'User invitations and role assignments.',
        statuses: {
          ACTIVE: 'User can sign in.',
          INVITED: 'Invite sent — awaiting first login.',
          DISABLED: 'Login blocked; history retained.',
        },
      },
      {
        name: 'Role & warehouse assignments',
        description:
          'Capability matrix: OWNER (billing + full admin), ADMIN (org config), WAREHOUSE_MANAGER (approve variances, sync conflicts), PICKER (floor scan only), VIEWER (read-only), B2B_CUSTOMER (showroom).',
        dataOrigin: 'Which warehouses each user may access.',
        columns: [
          { name: 'OWNER', purpose: 'Billing, fintech, and full tenant control.' },
          { name: 'ADMIN', purpose: 'Users, settings, and most office operations.' },
          {
            name: 'WAREHOUSE_MANAGER',
            purpose: 'Approve cycle-count variances, resolve sync conflicts, manage waves.',
          },
          { name: 'PICKER', purpose: 'Floor scan modes only — no office policy changes.' },
        ],
      },
    ],
  },

  '/settings?tab=warehouses': {
    title: 'Settings — Warehouses',
    purpose: 'Define buildings, zones, and bins that warehouse access and putaway rules reference.',
    flow: [
      'Add or edit a warehouse.',
      'Maintain zones/bins (or open the visualizer).',
      'Assign users to the warehouse from the Users tab.',
    ],
    reversals: [
      'Deactivate unused warehouses instead of deleting ones with ledger history.',
      'Bin coordinate edits can be patched again; they do not reverse stock.',
    ],
    correlations: [
      'Pick pathing, RTLS, and replenishment all depend on this layout.',
      'Users must be assigned to a warehouse before pickers can scan there.',
    ],
    components: [
      {
        name: 'Warehouse list',
        description: 'Tenant locations with codes and active flags.',
        dataOrigin: 'Warehouses and bin locations for your company.',
      },
      {
        name: 'Warehouse visualizer',
        description: 'Spatial layout editor for bins and edges.',
        dataOrigin: 'Visual warehouse layout for planning.',
      },
    ],
  },

  '/settings?tab=inventory': {
    title: 'Settings — Inventory Rules',
    purpose: 'Reorder points, UOM defaults, and inventory policy knobs that feed ATP and replenishment.',
    flow: [
      'Adjust reorder / safety-stock defaults as needed.',
      'Save — planning and low-stock KPIs pick up the new thresholds.',
    ],
    reversals: [
      'Restore prior thresholds with another save; audited in Operations/Audit Log when policy fields overlap.',
    ],
    correlations: [
      'Dashboard Low Stock Count and purchase suggestions use these thresholds.',
    ],
    components: [
      {
        name: 'Inventory rules form',
        description: 'Tenant defaults for reorder and stock policy.',
        dataOrigin: 'Inventory policy settings (reorder rules, units, and similar).',
      },
    ],
  },

  '/settings?tab=documents': {
    title: 'Settings — Documents',
    purpose: 'Templates and numbering for POs, packing slips, invoices, and other printable documents.',
    flow: [
      'Pick the document type to customize.',
      'Update logo, footer, or number series.',
      'Save — next print jobs use the new template.',
    ],
    reversals: [
      'Revert template fields and save again; already-printed PDFs are not rewritten.',
    ],
    correlations: [
      'Sales ship and PO submit flows render from these templates.',
    ],
    components: [
      {
        name: 'Document templates',
        description: 'Printable layout and numbering controls.',
        dataOrigin: 'Printable labels and document templates.',
      },
    ],
  },

  '/settings?tab=retailPos': {
    title: 'Settings — Retail POS',
    purpose:
      'Configure the Retail POS addon — receipt branding, default currency, Mexican CFDI, and shift-end blind closeout.',
    rolePermissions: ['OWNER', 'ADMIN'],
    flow: [
      'Choose the register default currency and whether CFDI 4.0 invoicing is on.',
      'Edit the receipt header and footer printed at checkout.',
      'Toggle blind closeout if cashiers must count the drawer without seeing the expected total.',
      'Save — the next POS session reads the updated tenant settings.',
    ],
    reversals: [
      'Edit the fields again and save; receipt text is not versioned on paper already printed.',
      'Turning off CFDI does not void invoices already issued from the register.',
    ],
    correlations: [
      'Only tenants with the Retail POS module see this tab.',
      'Blind closeout is a register guardrail, separate from warehouse blind cycle counts.',
    ],
    components: [
      {
        name: 'Localization & Compliance',
        description: 'Default register currency (USD or MXN) and CFDI 4.0 facturación toggle.',
        dataOrigin: 'Retail POS settings your administrators maintain.',
      },
      {
        name: 'Receipt Configuration',
        description: 'Header and footer printed on every POS receipt.',
        dataOrigin: 'Store name, address, tax ID, and return-policy copy you enter here.',
      },
      {
        name: 'Security & Loss Prevention',
        description: 'Blind closeout forces cashiers to count cash without seeing the expected drawer total.',
        dataOrigin: 'Shift-end register policy for this company.',
      },
    ],
  },

  '/settings?tab=security': {
    title: 'Settings — Security & SSO',
    purpose: 'SSO configuration, session policy, and authentication hardening for the tenant.',
    rolePermissions: ['OWNER', 'ADMIN'],
    glossary: {
      SSO: 'Single Sign-On via your company sign-in provider.',
      DKIM: 'DomainKeys Identified Mail — email domain authentication for outbound mail.',
      MFA: 'Multi-factor authentication for sensitive admin sessions.',
    },
    flow: [
      'Configure SSO provider fields if your IdP is ready.',
      'Review session / MFA related toggles.',
      'Save — next login follows the new policy.',
    ],
    reversals: [
      'Disable SSO carefully — ensure password/local login still works for admins before cutting over.',
      'Every security change is audited.',
    ],
    correlations: [
      'Affects how owners, admins, and pickers sign in; does not change warehouse access by itself.',
    ],
    components: [
      {
        name: 'SSO config',
        description: 'Sign-in provider connection for your company.',
        dataOrigin: 'Sign-in and security options for your company.',
        statuses: {
          ACTIVE: 'SSO login is enabled.',
          DISCONNECTED: 'SSO not wired — local login only.',
        },
      },
    ],
  },

  '/settings?tab=reconciliation': {
    title: 'Settings — Reconciliation',
    purpose: 'Tools and schedules for reconciling inventory levels, accounting balances, and external sync ledgers.',
    flow: [
      'Review the last reconciliation run.',
      'Trigger or schedule a reconcile when finance asks.',
      'Investigate mismatches on the linked operational pages.',
    ],
    reversals: [
      'Reconciliation jobs do not delete ledger rows — they report drift for manager stock correction.',
    ],
    correlations: [
      'Pairs with Accounting Sync and Cycle Counts when numbers disagree.',
    ],
    components: [
      {
        name: 'Reconciliation panel',
        description: 'Drift reports between InvSys levels and external books.',
        dataOrigin: 'Comparisons between this system and connected storefronts.',
      },
    ],
  },

  '/settings?tab=accounting': {
    title: 'Settings — Accounting Sync',
    purpose: 'Connect QuickBooks/Xero (or similar) so invoices and journals flow through the finance sync.',
    flow: [
      'Connect or refresh the accounting adapter.',
      'Map tax schemes and accounts as prompted.',
      'Watch sync status chips for FAILED rows and retry.',
    ],
    reversals: [
      'Disconnecting stops new syncs; already-posted external journals must be voided in the accounting system.',
    ],
    correlations: [
      'Paid invoices and COGS journals depend on this bridge.',
      'Integrations hub also opens here for accounting connections.',
    ],
    components: [
      {
        name: 'Accounting Sync panel',
        description: 'Adapter connection, tax maps, and sync log.',
        dataOrigin: 'Accounting export status for finance systems.',
        statuses: {
          SYNCED: 'Last update accepted by the accounting system.',
          PENDING: 'Queued for the next accounting sync.',
          FAILED: 'Needs retry or field correction.',
          SKIPPED: 'Intentionally not sent.',
        },
      },
    ],
  },

  '/settings?tab=integrations': {
    title: 'Settings — Integrations',
    purpose:
      'Connect e-commerce storefronts and accounting systems so orders and payments land in InvSys without double entry.',
    flow: [
      'Choose an e-commerce or accounting connector.',
      'Paste the connection keys provided by the storefront or accounting system.',
      'Enable the channel and verify a test order or payment event.',
    ],
    reversals: [
      'Disable a connector to stop inbound events; already-imported sales orders stay in the outbound pipeline.',
      'Rotate connection keys if a key leaks — update both InvSys and the external dashboard.',
    ],
    correlations: [
      'Storefront connections create or update sales orders that still allocate and ship like office-entered orders.',
      'Accounting connections flip invoice PAID and refresh the dashboard live feed.',
    ],
    components: [
      {
        name: 'E-commerce connectors',
        description: 'Storefront channels that push orders into Sales Orders.',
        dataOrigin: 'Connections to storefronts and marketplaces.',
      },
      {
        name: 'Accounting / payment connections',
        description: 'Inbound payment and accounting events from connected finance systems.',
        dataOrigin: 'Incoming status updates from connected business systems.',
        statuses: {
          ACTIVE: 'Connector accepting events.',
          DISCONNECTED: 'No live credentials — events will not land.',
          FAILED: 'Last delivery rejected; check secrets and payload maps.',
        },
      },
    ],
  },

  '/settings?tab=mesh': {
    title: 'Settings — Partner Catalog',
    purpose: 'Cross-tenant mesh mappings so seller SKUs resolve to buyer products on multi-party POs/SOs.',
    flow: [
      'Open Partner Catalog Mapping.',
      'Map partner SKUs to local variants.',
      'Save — mesh bridge uses the map on the next PURCHASE_ORDER_SUBMITTED.',
    ],
    reversals: [
      'Unmap or remap a SKU; historical documents keep the snapshot they were created with.',
    ],
    correlations: [
      'Unmapped mesh lines may create DRAFT exception sales orders for review.',
    ],
    components: [
      {
        name: 'Partner Catalog Mapping panel',
        description: 'Seller↔buyer SKU bridges for the mesh.',
        dataOrigin: 'Partner catalog sharing between trusted companies.',
      },
    ],
  },

  '/settings?tab=operations': {
    title: 'Settings — Operations',
    purpose:
      'Tenant floor rules — blind receiving, adjustment limits, scanner options — plus the Audit Log of who changed what.',
    flow: [
      'Toggle the operational rule you need (e.g. blind receiving, max adjust qty).',
      'Save — audit_log records the actor and JSON diff.',
      'Confirm floor devices pick up the new rule on the next scan.',
      'Use Audit Log / Activity Timeline when investigating who changed a limit.',
    ],
    reversals: [
      'Toggle the rule back; every change is recorded in the audit history (nothing is silently wiped).',
      'Raising an adjustment limit does not auto-approve past Pending manager review counts — managers still close those lines.',
    ],
    correlations: [
      'Blind receiving and variance thresholds change what pickers may post without manager review.',
      'Adjustment limits escalate large count differences to Pending manager review.',
    ],
    components: [
      {
        name: 'Operations toggles',
        description: 'Global floor policy: blind receiving, scanner options, and related tenant rules.',
        dataOrigin: 'Floor operating rules (receiving and scanner options).',
      },
      {
        name: 'Adjustment limits',
        description: 'Maximum variance / adjust magnitude before manager review is required.',
        dataOrigin: 'How large a count variance needs manager approval.',
      },
      {
        name: 'Audit Log',
        description: 'Append-only history of settings and sensitive admin actions (actor + JSON diff).',
        dataOrigin: 'History of important setting and inventory changes.',
        columns: [
          { name: 'Actor', purpose: 'Who made the change.' },
          { name: 'Diff', purpose: 'Before/after JSON for the settings map.' },
          { name: 'Timestamp', purpose: 'When the change was appended.' },
        ],
      },
    ],
  },

  '/settings?tab=syncConflicts': {
    title: 'Settings — Sync Conflicts',
    purpose: 'Review parked offline floor scans that could not finish after reconnecting.',
    flow: [
      'Open a PARKED conflict.',
      'Correct the highlighted fields if needed.',
      'Approve & Re-process (a manager-approved offline fix) or Discard.',
    ],
    reversals: [
      'Discard permanently drops the parked scan — do not re-scan the same line expecting a duplicate post.',
      'Approve posts under the manager name; reverse later only with a compensating stock correction if physical truth differs.',
    ],
    correlations: [
      'Same board is reachable from Exceptions Sync tab and the dashboard banner.',
      'Pickers keep working while managers clear the quarantine.',
    ],
    components: [
      {
        name: 'Sync Conflicts panel',
        description: 'Form for reviewing parked offline scans.',
        dataOrigin: 'The conflict list on Dashboard or Exceptions.',
        statuses: {
          PARKED: 'Waiting for manager adjudication.',
          APPROVED: 'Re-processed with a stock correction under the manager name.',
          DISCARDED: 'Dropped permanently from the replay queue.',
        },
      },
    ],
  },

  '/settings?tab=costCenters': {
    title: 'Settings — Cost Centers & Requisitions',
    purpose: 'Internal budgets and requisitions that authorize Issue Supplies without a customer sales order.',
    flow: [
      'Create or edit a cost center budget.',
      'Review DRAFT requisitions and approve when appropriate.',
      'Floor Issue Supplies charges against the approved center.',
    ],
    reversals: [
      'Cancel DRAFT requisitions before issue.',
      'After issue, reverse stock with a manager stock correction referencing the original consumption.',
    ],
    correlations: [
      'Issue Supplies on the floor reads these centers for budget clearance.',
    ],
    components: [
      {
        name: 'Cost center list',
        description: 'Budget-bearing internal accounts.',
        dataOrigin: 'Cost centers used when issuing internal supplies.',
      },
      {
        name: 'Internal requisitions',
        description: 'Approval workflow before stockroom issue.',
        dataOrigin: 'Internal issue of supplies to cost centers or jobs.',
        statuses: {
          DRAFT: 'Awaiting approval.',
          APPROVED: 'Eligible for Issue Supplies.',
          ISSUED: 'Stock deducted against the center.',
          CANCELLED: 'Stopped before issue.',
        },
      },
    ],
  },

  '/settings/profile': {
    title: 'Profile settings',
    purpose: 'Dedicated profile page for the signed-in user (same domain as Settings → Profile).',
    flow: [
      'Update personal details.',
      'Save and return to the app shell.',
    ],
    reversals: [
      'Edit again; no inventory impact.',
    ],
    correlations: [
      'Opens from the header avatar; Users tab remains the place for role and location access changes.',
    ],
    components: [
      {
        name: 'Profile settings form',
        description: 'Personal account fields outside the tabbed settings hub.',
        dataOrigin: 'Your signed-in profile details.',
      },
    ],
  },

  '/settings/billing': {
    title: 'Billing',
    purpose: 'OWNER-scoped subscription and plan management for the tenant.',
    flow: [
      'Review the current plan and seats.',
      'Change plan or payment method in the billing portal when needed.',
    ],
    reversals: [
      'Plan downgrades may take effect at period end — confirm in the billing portal.',
      'Billing changes do not reverse warehouse transactions.',
    ],
    correlations: [
      'Only OWNER (and sometimes ADMIN) can open this hub.',
    ],
    components: [
      {
        name: 'Billing portal',
        description: 'Subscription status and payment method.',
        dataOrigin: 'Subscription and billing managed by owners.',
      },
    ],
  },

  '/settings/fintech': {
    title: 'Cash Flow & Financing',
    purpose: 'OWNER-scoped cash-flow and financing insights tied to AR/AP signals.',
    flow: [
      'Review cash-flow panels.',
      'Open financing offers only when OWNER policy allows.',
    ],
    reversals: [
      'Financing acceptances are contractual — reverse via the fintech partner, not the inventory ledger.',
    ],
    correlations: [
      'Uses paid invoices and open receivables from the Invoices page.',
    ],
    components: [
      {
        name: 'Fintech dashboard',
        description: 'Cash position and financing entry points.',
        dataOrigin: 'Payment and payout options configured by owners.',
      },
    ],
  },

  '/settings/integrations': {
    title: 'Integrations Hub',
    purpose: 'Hub that routes into e-commerce, accounting, and operations integration surfaces.',
    flow: [
      'Pick the connector category (storefront, accounting, or operations).',
      'Follow the shortcut into the matching Settings tab or connection page.',
    ],
    reversals: [
      'Disable connectors from the Integrations or Accounting tabs — the hub itself does not mutate stock.',
    ],
    correlations: [
      'Shortcuts land on /settings?tab=integrations, accounting, or operations.',
    ],
    components: [
      {
        name: 'Integrations Hub cards',
        description: 'Navigation into storefront and accounting connection setup.',
        dataOrigin: 'Integration setup screens for storefronts and partners.',
      },
    ],
  },

  '/import': {
    title: 'Import wizard',
    purpose: 'Bulk-load products/variants via mapped CSV/Excel with preflight validation.',
    flow: [
      'Download the template.',
      'Map columns and run preflight.',
      'Resolve missing products, then commit the import.',
    ],
    reversals: [
      'Stop before commit if preflight shows errors.',
      'After commit, correct with a follow-up import or manual variant edits — imports do not delete ledger stock.',
    ],
    correlations: [
      'Imported masters immediately feed PO/SO and floor barcode resolution.',
    ],
    components: [
      {
        name: 'Mapping dropdowns',
        description: 'Enterprise column binding for CSV/Excel headers.',
        dataOrigin: 'Spreadsheets uploaded by your team to create or update records.',
      },
      {
        name: 'Preflight grid',
        description: 'Ready vs blocked rows before commit.',
        dataOrigin: 'Preview of spreadsheet rows before you confirm the import.',
      },
    ],
  },

  '/showroom': {
    title: 'B2B Showroom',
    purpose: 'Customer portal for catalog browse, cart, checkout, and order status at negotiated prices.',
    flow: [
      'Browse the restricted catalog.',
      'Add to cart and checkout.',
      'Track order status under Showroom Orders.',
    ],
    reversals: [
      'Remove cart lines before checkout.',
      'After place-order, cancellations go through the office sales-order Cancel/Un-allocate path — customers cannot wipe warehouse allocations.',
    ],
    correlations: [
      'Portal DRAFT orders enter the same outbound pipeline as office-entered SOs.',
    ],
    components: [
      {
        name: 'Catalog',
        description: 'Price list + volume breaks for the signed-in B2B customer.',
        dataOrigin: 'Products your B2B customers can browse in the showroom.',
      },
      {
        name: 'Order tracker',
        description: 'Status chips only — no bin maps.',
        dataOrigin: 'Order status your B2B customers see after checkout.',
        statuses: {
          DRAFT: 'Portal order placed; office may still confirm.',
          CONFIRMED: 'Accepted by the warehouse tenant.',
          ALLOCATED: 'Stock reserved for the buyer.',
          SHIPPED: 'Carrier has the freight.',
          CANCELLED: 'Order stopped by the office.',
        },
      },
    ],
  },
};

/** Strip hash fragments and normalize an optional search string to `?key=value` form. */
function normalizeSearch(search: string): string {
  const raw = (search || '').split('#')[0].trim();
  if (!raw) return '';
  return raw.startsWith('?') ? raw : `?${raw}`;
}

/** Normalize path only (no query): strip hash, trailing slashes. */
function normalizePathname(pathname: string): string {
  const withoutHash = (pathname || '/').split('#')[0] || '/';
  const pathOnly = withoutHash.split('?')[0] || '/';
  return pathOnly.replace(/\/+$/, '') || '/';
}

/** Read `tab` from a search string (`?tab=operations` or `tab=operations`). */
function readSettingsTab(search: string): string | null {
  const normalized = normalizeSearch(search);
  if (!normalized) return null;
  const tab = new URLSearchParams(normalized).get('tab');
  return tab && tab.trim() ? tab.trim() : null;
}

/**
 * Normalize path + optional search into a knowledge lookup key.
 * Settings with `?tab=` become `/settings?tab=X`; other routes stay path-only.
 */
export function knowledgeContextKey(pathname: string, search = ''): string {
  const withoutHash = (pathname || '/').split('#')[0] || '/';
  let path = withoutHash;
  let searchPart = search;

  if (path.includes('?')) {
    const [p, q] = path.split('?');
    path = p || '/';
    if (!searchPart && q) {
      searchPart = q;
    }
  }

  path = normalizePathname(path);
  const tab = path === '/settings' ? readSettingsTab(searchPart) : null;
  if (path === '/settings' && tab) {
    return `/settings?tab=${tab}`;
  }
  return path;
}

function matchLongestPrefix(path: string): RouteKnowledge | null {
  const keys = Object.keys(ROUTE_KNOWLEDGE)
    .filter((key) => !key.includes('?'))
    .sort((a, b) => b.length - a.length);
  for (const key of keys) {
    if (path === key || path.startsWith(`${key}/`)) {
      return ROUTE_KNOWLEDGE[key] ?? null;
    }
  }
  return null;
}

/**
 * Resolve using pathname AND search (settings ?tab=).
 * Longest/exact match; settings tabs preferred.
 */
export function resolveKnowledgeContext(
  pathname: string,
  search = '',
): ResolvedRouteKnowledge | null {
  const path = normalizePathname(pathname.includes('?') ? pathname.split('?')[0]! : pathname);
  const searchPart = pathname.includes('?') && !search
    ? pathname.slice(pathname.indexOf('?'))
    : search;

  if (path === '/settings') {
    const tab = readSettingsTab(searchPart);
    if (tab) {
      const tabKey = `/settings?tab=${tab}`;
      if (ROUTE_KNOWLEDGE[tabKey]) {
        return enrichRouteKnowledge(tabKey, ROUTE_KNOWLEDGE[tabKey]!);
      }
    }
    const fallback =
      ROUTE_KNOWLEDGE['/settings'] ?? ROUTE_KNOWLEDGE['/settings?tab=profile'] ?? null;
    return fallback ? enrichRouteKnowledge('/settings', fallback) : null;
  }

  const exactKey = knowledgeContextKey(path, searchPart);
  if (ROUTE_KNOWLEDGE[exactKey]) {
    return enrichRouteKnowledge(exactKey, ROUTE_KNOWLEDGE[exactKey]!);
  }

  const prefixHit = matchLongestPrefix(path);
  if (prefixHit) {
    const prefixKey =
      Object.keys(ROUTE_KNOWLEDGE)
        .filter((key) => !key.includes('?'))
        .sort((a, b) => b.length - a.length)
        .find((key) => path === key || path.startsWith(`${key}/`)) ?? path;
    return enrichRouteKnowledge(prefixKey, prefixHit);
  }

  if (path.startsWith('/showroom')) {
    const showroom = ROUTE_KNOWLEDGE['/showroom'];
    return showroom ? enrichRouteKnowledge('/showroom', showroom) : null;
  }

  return null;
}

/** Back-compat: pathname may include ?query */
export function resolveRouteKnowledge(pathname: string): ResolvedRouteKnowledge | null {
  const raw = (pathname || '/').split('#')[0] || '/';
  if (raw.includes('?')) {
    const qIndex = raw.indexOf('?');
    return resolveKnowledgeContext(raw.slice(0, qIndex), raw.slice(qIndex));
  }
  return resolveKnowledgeContext(raw, '');
}

function formatComponentForChat(component: RouteKnowledgeComponent): string {
  const parts = [
    `${component.name}: ${component.description} (source: ${component.dataOrigin})`,
  ];
  const cols = normalizeColumns(component.columns);
  if (cols.length > 0) {
    parts.push(`Columns: ${cols.map((c) => `${c.name}=${c.purpose}`).join('; ')}`);
  }
  if (component.statuses && Object.keys(component.statuses).length > 0) {
    const statusBits = Object.entries(component.statuses)
      .map(([code, meaning]) => `${code}=${meaning}`)
      .join('; ');
    parts.push(`Statuses: ${statusBits}`);
  }
  return parts.join(' ');
}

/** Compact system-context block injected into support chat prompts (plain English only). */
export function formatRouteKnowledgeForChat(
  routeKey: string,
  knowledge: RouteKnowledge | null,
): string {
  if (!knowledge) {
    return `System Context: The user is currently on ${routeKey}. No localized page playbook is registered. Explain only on-screen buttons and safe undo steps. Never mention APIs, databases, or code.`;
  }

  const enriched = enrichRouteKnowledge(routeKey, knowledge);
  const ns = enriched.i18nKey;
  const loc = (suffix: string, fallback: string) =>
    ns ? String(i18n.t(`pageHelp.playbooks.${ns}.${suffix}`, { defaultValue: fallback })) : fallback;
  const title = loc('title', enriched.title);
  const purpose = loc('purpose', enriched.purpose);
  const description = loc('description', enriched.description);
  const dataOrigin = loc('dataOrigin', enriched.dataOrigin);
  const whoCanUse = enriched.rolePermissions
    .map((code) => String(i18n.t(`roles.${code}`, { defaultValue: ROLE_LABELS[code] ?? code })))
    .join(', ');
  const undo = enriched.howToUndo
    .map((item, index) => loc(`reversals.${index}`, item))
    .join(' ');
  const componentBits = enriched.components.map(formatComponentForChat).join(' | ');
  const glossaryBits = enriched.glossary
    ? Object.entries(enriched.glossary)
        .map(([term, meaning]) => `${term}=${meaning}`)
        .join('; ')
    : '';
  const actionBits = enriched.quickActions
    .map((action, index) => `${loc(`actions.${index}`, action.label)}→${action.route}`)
    .join('; ');
  const troubleBits = (enriched.troubleshooting ?? [])
    .map(
      (step, index) =>
        `${loc(`troubleshooting.${index}.issue`, step.issue)} => ${loc(`troubleshooting.${index}.solution`, step.solution)} (${loc(`troubleshooting.${index}.action`, step.action.label)}→${step.action.route})`,
    )
    .join('; ');

  return [
    `System Context: The user is currently on the ${title} page.`,
    `Purpose: ${purpose}`,
    description ? `Overview: ${description}` : '',
    `Who can use this page: ${whoCanUse}.`,
    `Where the information comes from: ${dataOrigin}`,
    `On-screen areas: ${componentBits}`,
    glossaryBits ? `Glossary: ${glossaryBits}` : '',
    actionBits ? `Quick actions: ${actionBits}` : '',
    troubleBits ? `If stuck: ${troubleBits}` : '',
    `How to undo: ${undo}`,
    'Answer only with UI button labels and 1…N operational steps. Never mention APIs, HTTP codes, databases, services, or code files.',
    'User Query:',
  ]
    .filter(Boolean)
    .join(' ');
}
