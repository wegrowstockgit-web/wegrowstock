/**
 * Localized page knowledge for the Page Info overlay and support copilot context.
 * Keys are pathname prefixes (longest match wins).
 */

export type RouteKnowledge = {
  /** Short display title for the overlay header. */
  title: string;
  purpose: string;
  flow: string[];
  reversals: string[];
  correlations: string[];
  components: Record<string, string>;
};

export const ROUTE_KNOWLEDGE: Record<string, RouteKnowledge> = {
  '/dashboard': {
    title: 'Dashboard',
    purpose:
      'Command center for live warehouse KPIs — open orders, exceptions, sync conflicts, and AR signals — so managers see what needs attention without digging into every module.',
    flow: [
      'Scan the KPI cards and SSE live tiles for red/amber signals.',
      'Open Sync Conflicts or Exceptions banners when they appear.',
      'Drill into Sales Orders, Purchase Orders, or Fulfillment from the quick links.',
    ],
    reversals: [
      'Dashboard itself does not mutate inventory — reverse work on the source page (orders, exceptions, or sync conflicts).',
      'Dismissing a banner only hides the alert until the next refetch; it does not undo the underlying transaction.',
    ],
    correlations: [
      'Office side: KPI snapshots are fed by floor scans, allocations, and invoice webhooks.',
      'Floor side: parked offline conflicts surface here for manager adjudication.',
    ],
    components: {
      'KPI cards': 'CQRS snapshot metrics (orders, ATP pressure, exceptions).',
      'Sync conflict banner': 'Deep-link into the parked offline mutation queue.',
      'SSE stream': 'Live invoice/order status without polling.',
    },
  },

  '/purchase-orders': {
    title: 'Purchase Orders',
    purpose:
      'Create and submit inbound supply contracts against approved suppliers so the floor can receive freight against expected lines.',
    flow: [
      'Select a supplier and add lines (SKU, qty, unit cost, UOM).',
      'Save as Draft, then Submit when the buy is firm.',
      'Optionally attach landed cost (freight/customs) before or after submit.',
      'Hand off to Floor Receive when the truck arrives.',
    ],
    reversals: [
      'Draft POs can be edited or deleted before submit.',
      'Submitted POs cannot silently erase ledger receives — cancel open lines only if nothing has been received; otherwise reverse via Returns / inventory adjust with manager attribution.',
      'Never delete a PO that already has RECEIVE ledger rows; use RMA or credit notes instead.',
    ],
    correlations: [
      'Floor: submitted POs become the scan baseline on Inbound Receive.',
      'Suppliers: tokenized portal acknowledgments update promised ship dates here.',
      'ATP: receiving against this PO unlocks sellable stock for Sales Orders and B2B.',
    ],
    components: {
      'PO grid': 'Virtualized list of inbound documents and status chips.',
      'Floor receive CTA': 'Deep-links the handheld to /inbound/receive for the selected PO.',
      'Landed cost': 'Distributes freight/customs across line unit costs.',
    },
  },

  '/inbound/receive': {
    title: 'Inbound Receive',
    purpose:
      'Scan freight into inventory: match PO → product (GS1) → destination bin so the append-only ledger records a RECEIVE and ATP increases.',
    flow: [
      'Unlock the floor PIN if prompted.',
      'Scan the PO / ASN barcode so expected lines appear.',
      'Scan each product (capture lot/expiry/serial when prompted).',
      'Confirm quantity, then scan the putaway bin (or follow a cross-dock staging overlay).',
      'Confirm — the ledger posts immediately.',
    ],
    reversals: [
      'Use the 5-second undo buffer on a mis-scan before it commits to the offline queue.',
      'After commit, do not invent a negative scan — open Exceptions / Returns or post a manager-attributed inventory ADJUST (ERROR_CORRECTION) from the office.',
      'Skip & Flag is for outbound pick problems, not for reversing a successful receive.',
      'Cross-dock misroutes: transfer stock from staging back to reserve with a documented TRANSFER, never delete ledger rows.',
    ],
    correlations: [
      'Office: RECEIVE unlocks allocation and clears backorders (including cross-dock auto-allocate).',
      'Compliance: lot/serial captured here feeds Lot Trace genealogy.',
      'Offline: failed business rules park in Sync Conflicts for managers — pickers keep working.',
    ],
    components: {
      'Barcode wedge': 'HID scanner focus target for PO / item / bin.',
      'Expected lines': 'PO lines remaining to receive.',
      'Cross-dock overlay': 'Routes to staging when open backorder demand exists.',
    },
  },

  '/sales-orders': {
    title: 'Sales Orders',
    purpose:
      'Confirm customer demand, allocate FEFO lots, and release picking waves so floor operators can fulfill outbound orders.',
    flow: [
      'Confirm a DRAFT/pending order.',
      'Click Allocate to reserve on-hand (or leave BACKORDERED if stock is short).',
      'Generate / optimize / release a picking wave for ALLOCATED orders.',
      'Track PARTIALLY_SHIPPED → SHIPPED as the floor packs out.',
    ],
    reversals: [
      'Un-allocate / Cancel releases ACTIVE allocations back to the ATP pool without writing a ledger SHIP.',
      'Cancel before pick to free reserved lots; after picks, reverse via shipment void + inventory ADJUST — never erase SHIP ledger rows.',
      'Credit-hold freezes are cleared by raising the customer credit limit or reducing order value, not by forcing allocate.',
    ],
    correlations: [
      'Floor: released waves become pick tasks on Fulfillment.',
      'B2B portal: showroom orders enter this same pipeline; buyers only see status chips.',
      'Finance: shipment and invoicing depend on successful allocation/ship.',
    ],
    components: {
      'Allocation header': 'Confirm / Allocate / Wave actions for the selected order.',
      'Status chips': 'DRAFT → CONFIRMED → ALLOCATED | BACKORDERED → SHIPPED.',
      'Wave controls': 'Generate, optimize path, and release to pickers.',
    },
  },

  '/fulfillment': {
    title: 'Fulfillment (Floor)',
    purpose:
      'Execute pick, pack, and ship scans against released wave tasks with glove-friendly, PIN-locked hardware scanning.',
    flow: [
      'Unlock shift PIN.',
      'Claim a batch / task from the wave.',
      'Scan the directed location and SKU (Idempotency-Key protects double-posts).',
      'At pack, cartonize and capture scale weight, then print the carrier label.',
    ],
    reversals: [
      'Mis-scan: use the short undo window before the mutation queues.',
      'Empty bin / damaged stock: Skip & Flag — frees the allocation without writing a false ledger ADJUST.',
      'Wrong pick already posted: stop and escalate; managers reverse via inventory ADJUST (ERROR_CORRECTION) or void the shipment — never delete ledger history.',
      'Quarantined offline conflicts: managers resolve from Sync Conflicts; do not re-scan the same Idempotency-Key.',
    ],
    correlations: [
      'Office: each SCAN_PICK consumes allocations and feeds ship/label outbox events.',
      'Task interleaving may inject COUNT/PUTAWAY between picks to reduce travel.',
    ],
    components: {
      'Scan wedge field': 'Primary HID input for pick/receive/count modes.',
      'Skip & Flag': 'Exception path that shunts allocations for office review.',
      'Quarantine badge': 'Local offline conflicts awaiting replay adjudication.',
    },
  },

  '/products': {
    title: 'Products',
    purpose:
      'Maintain the item master (variants, dimensions, packaging, temperature zones) and view on-hand / allocated / ATP derived from the ledger.',
    flow: [
      'Search or scroll the virtualized grid (or mobile cards).',
      'Edit dimensions, UOM, and compliance fields as needed.',
      'Use Columns / Density to personalize the view — layout changes do not touch the ledger.',
      'Open Import for bulk catalog loads.',
    ],
    reversals: [
      'Catalog field edits can be patched again; they do not reverse inventory.',
      'To reverse quantity truth, use Cycle Counts, inventory ADJUST, or Ledger History Undo (ERROR_CORRECTION) — never edit on-hand as a free-text field.',
      'Import mistakes: re-import corrected rows or adjust variants; do not wipe ledger history.',
    ],
    correlations: [
      'Cartonization and shipping use dimensions from this master.',
      'Floor scans and office allocations read ATP from levels maintained by the delta flush worker.',
    ],
    components: {
      'VirtualizedTable / Mobile cards': 'Responsive catalog view.',
      'Column visibility': 'Pin/hide fields without changing stock.',
      'Import button': 'Opens the bulk ingestion wizard.',
    },
  },

  '/inventory/ledger': {
    title: 'Inventory Ledger',
    purpose:
      'Inspect append-only stock movements (RECEIVE, ADJUST, SHIP, TRANSFER, ASSEMBLY). The ledger is the system of record; levels are a projection.',
    flow: [
      'Filter by SKU, location, date, or movement type.',
      'Open a row to see reason code, actor, lot/serial, and references.',
      'Use Undo only on reversible rows when policy allows.',
    ],
    reversals: [
      'Prefer the built-in Undo / reverse-transaction action — it posts a compensating ERROR_CORRECTION row attributed to you.',
      'Never delete or rewrite historical ledger rows (compliance / DSCSA / FSMA).',
      'If Undo is hidden, post an explicit ADJUST with a clear reason code from the office adjust flow.',
    ],
    correlations: [
      'Every floor scan and office allocate/ship eventually appears here.',
      'Lot Trace walks these rows recursively for recall response.',
    ],
    components: {
      'History table': 'Chronological movements with reverse affordances.',
      'Reason codes': 'Business justification stamped on each append.',
    },
  },

  '/cycle-counts': {
    title: 'Cycle Counts',
    purpose:
      'Blind physical counts that reconcile on-hand to reality. Small variances auto-adjust; large ones escalate to manager review.',
    flow: [
      'Open a count task on the scanner (expected qty hidden).',
      'Enter the physical quantity and confirm.',
      'Managers approve PENDING_MANAGER_REVIEW variances from the office board.',
    ],
    reversals: [
      'Before confirm, clear the entry and re-count.',
      'After an auto-adjust posts, reverse with a manager ADJUST (ERROR_CORRECTION) — do not re-count the same line to “cancel”.',
      'Rejecting a variance leaves the slot locked until a correct count or disposition is recorded.',
    ],
    correlations: [
      'Approved counts write inventory_ledger ADJUST and reconcile levels via the delta flush.',
      'Pickers should not invent adjusts outside the count workflow.',
    ],
    components: {
      'Blind count input': 'No expected qty shown — reduces confirmation bias.',
      'Variance queue': 'Office approval for high-impact deltas.',
    },
  },

  '/exceptions': {
    title: 'Exceptions',
    purpose:
      'Resolve floor Skip & Flag incidents and (on the Sync tab) adjudicate parked offline mutation conflicts without corrupting the ledger.',
    flow: [
      'Open an OPEN fulfillment exception.',
      'Investigate the bin / damage, then Resolve with disposition.',
      'Optionally post a ledger ADJUST only after physical truth is known.',
      'On Sync Conflicts: correct schema fields and Approve & Re-process, or Discard.',
    ],
    reversals: [
      'Resolving an exception does not automatically restore the original allocation — re-allocate the sales order if still needed.',
      'Discarding a sync conflict permanently drops the parked scan; Approve posts OFFLINE_CONFLICT_OVERRIDE as the manager.',
      'Do not reverse by re-flagging the same allocation blindly.',
    ],
    correlations: [
      'Pickers create exceptions to keep waves moving; managers close the loop.',
      'Sync Conflicts tie offline floor work to office identity for compliance.',
    ],
    components: {
      'Exception board': 'OPEN → RESOLVED fulfillment exceptions.',
      'Sync Conflicts panel': 'Schema-driven conflict resolution form.',
    },
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
    components: {
      'Replenishment queue': 'Prioritized move tasks.',
      'Confirm scan pair': 'Source + destination barcodes.',
    },
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
    components: {
      'Credit line': 'Exposure check during confirm/allocate.',
      'Customer grid': 'Accounts master list.',
    },
  },

  '/invoices': {
    title: 'Invoices',
    purpose: 'Track AR invoices and payment state; Stripe webhooks flip PAID and sync accounting via the outbox.',
    flow: [
      'Review AR aging and open invoices.',
      'Send payment requests when configured.',
      'Watch live PAID updates over SSE after Stripe settles.',
    ],
    reversals: [
      'Do not manually flip PAID without a finance process — void/credit in the accounting system and let webhooks or finance tools reconcile.',
      'Refunds are accounting events, not inventory reversals; stock returns use the Returns module.',
    ],
    correlations: [
      'Paid invoices may release credit exposure for new allocations.',
      'Owners see the same PAID signal on the dashboard SSE feed.',
    ],
    components: {
      'Invoice grid': 'AR status and amounts.',
      'SSE live row': 'Turns green on payment_intent.succeeded.',
    },
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
    components: {
      'Supplier form': 'Terms, contacts, banking.',
      'Masked IBAN': 'Last-4 display after vault encrypt.',
    },
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
      'Restocked units that should not sell: move back to quarantine or scrap with a documented ADJUST — do not delete the RECEIVE.',
    ],
    correlations: [
      'Restock increases sellable ATP; scrap does not.',
      'Finance may issue credits separately from inventory disposition.',
    ],
    components: {
      'RMA board': 'Office approval workflow.',
      'Disposition controls': 'RESTOCK vs SCRAP.',
    },
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
    components: {
      'RMA scanner': 'Matches returned GTIN/lot to the authorized return.',
    },
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
    components: {
      'Genealogy graph/table': 'Parent/child ledger walk.',
      'CSV export': 'Audit package download.',
    },
  },

  '/rtls': {
    title: 'RTLS map',
    purpose: 'Spatial digital twin — live picker positions, congestion heat, and walkable edges for wayfinding.',
    flow: [
      'Open the map and watch SSE position updates.',
      'Inspect 7-day heatmap of ledger activity.',
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
    components: {
      'Spatial canvas': 'Bins + picker dots.',
      'Heat overlay': 'Congestion from recent movements.',
    },
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
    components: {
      'BOM editor': 'Components, ops, outputs.',
    },
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
      'After ASSEMBLY ledger posts, reverse with compensating assembly/adjust entries — never delete completed production history.',
    ],
    correlations: [
      'Locks components away from outbound sales picks.',
      'Terminal labor timesheets attach cost to the order.',
    ],
    components: {
      'Allocate Components': 'Row-locks raw levels for the run.',
    },
  },

  '/manufacturing/terminal': {
    title: 'Production Terminal',
    purpose: 'Floor assembly station — start/stop labor, consume components, mint finished goods labels.',
    flow: [
      'Scan the work order and Start timesheet.',
      'Complete the assembly run.',
      'Stop timesheet and post assemble — ledger ASSEMBLY consume + receive FG.',
    ],
    reversals: [
      'Stop a timesheet without assemble if the run aborts (labor cost may still apply).',
      'Wrong assemble: manager posts compensating ASSEMBLY/ADJUST with OFFLINE or ERROR_CORRECTION attribution.',
    ],
    correlations: [
      'Finished goods become allocatable for sales immediately after assemble.',
    ],
    components: {
      'Timesheet controls': 'Labor duration × rate.',
      'Assemble action': 'Dual ledger write (consume + receive).',
    },
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
      'After confirm, reverse with a positive ADJUST to the stockroom attributed to the manager, referencing the original issue.',
    ],
    correlations: [
      'Charges the cost center budget; does not affect customer allocations.',
    ],
    components: {
      'Cost center picker': 'Budget clearance gate.',
      'Issue confirm': 'ADJUST ledger write.',
    },
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
      'After sync, reverse via depot ADJUST/TRANSFER back onto the van with manager reason codes.',
    ],
    correlations: [
      'Reorder-point triggers truck replenishment from the warehouse.',
    ],
    components: {
      'Van scan field': 'Consumes from VEHICLE location.',
    },
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
      'Headline KPIs may come from CQRS snapshots, not live aggregation.',
    ],
    components: {
      'Recharts boards': 'Visual analytics.',
    },
  },

  '/settings': {
    title: 'Organization settings',
    purpose: 'Tenant-wide rules (blind receiving, adjustment limits, scanner options) that instantly govern floor behavior.',
    flow: [
      'Toggle the operational rule you need.',
      'Save — audit_log records the actor and JSON diff.',
      'Confirm floor devices pick up the new rule on next action.',
    ],
    reversals: [
      'Toggle the rule back; every change is append-only audited (no silent history wipe).',
      'Fintech / billing subsections are OWNER-scoped — reverse subscription changes in the billing portal if needed.',
    ],
    correlations: [
      'Blind receiving and variance thresholds change what pickers may post without manager review.',
    ],
    components: {
      'Operations toggles': 'Global floor policy.',
      'Users tab': 'Role and warehouse assignments.',
      'Sync Conflicts tab': 'Alias into conflict adjudication.',
    },
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
    components: {
      'Mapping dropdowns': 'Enterprise column binding.',
      'Preflight grid': 'Ready vs blocked rows.',
    },
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
    components: {
      'Catalog': 'Price list + volume breaks.',
      'Order tracker': 'Status chips only — no bin maps.',
    },
  },
};

/** Longest-prefix match so `/returns/receive` wins over `/returns`. */
export function resolveRouteKnowledge(pathname: string): RouteKnowledge | null {
  const path = (pathname.split('?')[0] || '/').replace(/\/+$/, '') || '/';
  if (ROUTE_KNOWLEDGE[path]) {
    return ROUTE_KNOWLEDGE[path];
  }
  const keys = Object.keys(ROUTE_KNOWLEDGE).sort((a, b) => b.length - a.length);
  for (const key of keys) {
    if (path === key || path.startsWith(`${key}/`)) {
      return ROUTE_KNOWLEDGE[key];
    }
  }
  // Showroom nested routes
  if (path.startsWith('/showroom')) {
    return ROUTE_KNOWLEDGE['/showroom'];
  }
  return null;
}

/** Compact system-context block injected into support chat prompts. */
export function formatRouteKnowledgeForChat(pathname: string, knowledge: RouteKnowledge | null): string {
  if (!knowledge) {
    return `System Context: The user is currently on ${pathname}. No localized page playbook is registered. Emphasize safe reversals that never delete append-only ledger rows.`;
  }
  const reversals = knowledge.reversals.join(' ');
  return [
    `System Context: The user is currently on the ${knowledge.title} page (${pathname}).`,
    `Purpose: ${knowledge.purpose}`,
    `Reversal mechanism: ${reversals}`,
    'Emphasize how to safely reverse or undo transactions without corrupting the append-only inventory ledger.',
    'User Query:',
  ].join(' ');
}
