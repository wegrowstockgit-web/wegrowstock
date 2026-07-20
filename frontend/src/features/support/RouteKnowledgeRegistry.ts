/**
 * Localized page knowledge for the Page Info overlay and support copilot context.
 * Keys are pathname prefixes (longest match wins). Settings tabs use full
 * `/settings?tab=` keys so the copilot can answer tab-specific questions.
 */

export type RouteKnowledgeColumn = {
  name: string;
  purpose: string;
};

export type RouteKnowledgeComponent = {
  name: string;
  description: string;
  dataOrigin: string;
  columns?: RouteKnowledgeColumn[];
  /** Status enum key → human explanation */
  statuses?: Record<string, string>;
};

export type RouteKnowledge = {
  title: string;
  purpose: string;
  flow: string[];
  reversals: string[];
  correlations: string[];
  components: RouteKnowledgeComponent[];
};

export const ROUTE_KNOWLEDGE: Record<string, RouteKnowledge> = {
  '/dashboard': {
    title: 'Dashboard',
    purpose:
      'Command center for live warehouse KPIs — stock value, low stock, open orders, work-queue cards, exceptions, and sync conflicts — so managers see what needs attention without digging into every module.',
    flow: [
      'Scan Headline KPIs (Stock Value, Low Stock Count, Open Orders) for red/amber signals.',
      'Work the Work Queue cards — Needs Allocation and Ready to Invoice first.',
      'Open Sync Conflicts or Exceptions banners when they appear.',
      'Drill into Sales Orders, Purchase Orders, or Fulfillment from the quick links.',
    ],
    reversals: [
      'Dashboard itself does not mutate inventory — reverse work on the source page (orders, exceptions, or sync conflicts).',
      'Dismissing a banner only hides the alert until the next refetch; it does not undo the underlying transaction.',
    ],
    correlations: [
      'Office side: KPI snapshots are fed by floor scans, allocations, and invoice webhooks via DashboardKpiSnapshot CQRS.',
      'Floor side: parked offline conflicts surface here for manager adjudication.',
    ],
    components: [
      {
        name: 'Headline KPIs',
        description:
          'Stock Value, Low Stock Count, and Open Orders — the three numbers managers glance at first each shift.',
        dataOrigin: 'DashboardKpiService → DashboardKpiSnapshot (CQRS read model)',
        columns: [
          { name: 'Stock Value', purpose: 'Extended on-hand value in tenant currency.' },
          { name: 'Low Stock Count', purpose: 'SKUs at or below reorder point.' },
          { name: 'Open Orders', purpose: 'Sales orders still in flight (not shipped/closed/cancelled).' },
        ],
      },
      {
        name: 'Work Queue cards',
        description:
          'Actionable counts that deep-link into the next operational screen — especially Needs Allocation and Ready to Invoice.',
        dataOrigin: 'DashboardService work-queue projection',
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
        description: 'Deep-link into the parked offline mutation queue for manager review.',
        dataOrigin: 'OfflineMutationQueue / SyncConflictStore',
      },
      {
        name: 'SSE stream',
        description: 'Live invoice/order status without polling.',
        dataOrigin: 'DashboardStream (SSE) + InvoiceService webhooks',
      },
    ],
  },

  '/purchase-orders': {
    title: 'Purchase Orders',
    purpose:
      'Create and submit inbound supply contracts against approved suppliers so the floor can receive freight against expected lines.',
    flow: [
      'Select a supplier and add lines (SKU, qty, unit cost, UOM).',
      'Save as Draft, then Submit when the buy is firm.',
      'Optionally attach landed cost (freight/customs) before or after submit.',
      'Mark In Transit when the vendor ships, then hand off to Floor Receive when the truck arrives.',
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
    components: [
      {
        name: 'PO grid',
        description: 'Virtualized list of inbound documents and status chips.',
        dataOrigin: 'PurchaseOrderService',
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
        description: 'Deep-links the handheld to /inbound/receive for the selected PO.',
        dataOrigin: 'PurchaseOrderService + InboundReceive flow',
      },
      {
        name: 'Landed cost',
        description: 'Distributes freight/customs across line unit costs.',
        dataOrigin: 'LandedCostService',
      },
    ],
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
    components: [
      {
        name: 'Barcode wedge',
        description: 'HID scanner focus target for PO / item / bin.',
        dataOrigin: 'InboundReceivePage scanner focus + InventoryService.receive',
      },
      {
        name: 'Expected lines',
        description: 'PO lines remaining to receive.',
        dataOrigin: 'PurchaseOrderService',
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
        dataOrigin: 'CrossDockService',
      },
    ],
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
    components: [
      {
        name: 'Allocation header',
        description: 'Confirm / Allocate / Wave actions for the selected order.',
        dataOrigin: 'SalesOrderService + AllocationService',
      },
      {
        name: 'Status chips',
        description: 'Lifecycle of the outbound order from draft through ship or cancel.',
        dataOrigin: 'SalesOrderService',
        statuses: {
          DRAFT: 'Not yet confirmed — editable, no stock reserved.',
          CONFIRMED: 'Customer demand accepted; ready to allocate.',
          ALLOCATED: 'Lots reserved (FEFO); eligible for pick waves.',
          BACKORDERED: 'Allocate ran but no stock was reserved — waiting on inbound.',
          PARTIALLY_SHIPPED: 'Some lines shipped; remainder still open.',
          SHIPPED: 'All shippable qty left the warehouse.',
          CANCELLED: 'Order stopped; allocations released without a SHIP ledger write.',
        },
      },
      {
        name: 'Wave controls',
        description: 'Generate, optimize path, and release to pickers.',
        dataOrigin: 'WaveService',
      },
    ],
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
    components: [
      {
        name: 'Scan wedge field',
        description: 'Primary HID input for pick/receive/count modes.',
        dataOrigin: 'FulfillmentController + InventoryService',
      },
      {
        name: 'Skip & Flag',
        description: 'Exception path that shunts allocations for office review without inventing an ADJUST.',
        dataOrigin: 'FulfillmentExceptionService',
      },
      {
        name: 'Quarantine badge',
        description: 'Local offline conflicts awaiting replay adjudication.',
        dataOrigin: 'SyncConflictStore / OfflineMutationQueue',
      },
    ],
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
    components: [
      {
        name: 'VirtualizedTable / Mobile cards',
        description: 'Responsive catalog view with on-hand / allocated / ATP.',
        dataOrigin: 'ProductService + InventoryLevel projection',
      },
      {
        name: 'Column visibility',
        description: 'Pin/hide fields without changing stock.',
        dataOrigin: 'PreferencesStore (client layout only)',
      },
      {
        name: 'Import button',
        description: 'Opens the bulk ingestion wizard.',
        dataOrigin: 'ImportService',
      },
    ],
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
    components: [
      {
        name: 'History table',
        description: 'Chronological movements with reverse affordances.',
        dataOrigin: 'InventoryService / InventoryLedgerRepository',
        columns: [
          { name: 'Type', purpose: 'RECEIVE, ADJUST, SHIP, TRANSFER, ASSEMBLY, etc.' },
          { name: 'Reason code', purpose: 'Business justification stamped on each append.' },
          { name: 'Actor', purpose: 'User or system identity that posted the row.' },
        ],
      },
      {
        name: 'Reason codes',
        description: 'Business justification stamped on each append.',
        dataOrigin: 'InventoryService reason-code catalog',
      },
    ],
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
    components: [
      {
        name: 'Blind count input',
        description: 'No expected qty shown — reduces confirmation bias.',
        dataOrigin: 'CycleCountService',
      },
      {
        name: 'Variance queue',
        description: 'Office approval for high-impact deltas before ledger ADJUST posts.',
        dataOrigin: 'CycleCountService',
        statuses: {
          PENDING: 'Count submitted; variance evaluation not finished.',
          AUTO_APPROVED: 'Variance within threshold — ledger ADJUST posted automatically.',
          PENDING_MANAGER_REVIEW: 'Variance exceeds limit — waiting on manager approve/reject.',
          APPROVED: 'Manager approved; compensating ADJUST posted.',
          RECOUNT_REQUESTED: 'Manager sent the line back for another physical count.',
        },
      },
    ],
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
    components: [
      {
        name: 'Exception board',
        description: 'Fulfillment Skip & Flag incidents awaiting disposition.',
        dataOrigin: 'FulfillmentExceptionService',
        statuses: {
          OPEN: 'Picker flagged the allocation; office has not closed it yet.',
          RESOLVED: 'Manager applied disposition; wave can continue after re-allocate if needed.',
        },
      },
      {
        name: 'Sync Conflicts panel',
        description: 'Schema-driven conflict resolution form for parked offline mutations.',
        dataOrigin: 'OfflineMutationQueue / SyncConflict adjudication',
        statuses: {
          PARKED: 'Offline mutation failed business rules and is waiting for a manager.',
          APPROVED: 'Manager corrected fields and re-processed with OFFLINE_CONFLICT_OVERRIDE.',
          DISCARDED: 'Parked mutation permanently dropped — do not re-scan the same Idempotency-Key.',
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
        dataOrigin: 'ReplenishmentService',
      },
      {
        name: 'Confirm scan pair',
        description: 'Source + destination barcodes that post a TRANSFER.',
        dataOrigin: 'InventoryService.transfer',
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
        dataOrigin: 'CustomerService + SalesOrderService credit gate',
      },
      {
        name: 'Customer grid',
        description: 'Accounts master list with ship-to links.',
        dataOrigin: 'CustomerService',
      },
    ],
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
    components: [
      {
        name: 'Invoice grid',
        description: 'AR status and amounts.',
        dataOrigin: 'InvoiceService',
        statuses: {
          DRAFT: 'Invoice not yet sent to the customer.',
          OPEN: 'Awaiting payment.',
          PAID: 'Stripe (or accounting sync) marked settled.',
          VOID: 'Cancelled for finance reasons — does not reverse warehouse stock.',
        },
      },
      {
        name: 'SSE live row',
        description: 'Turns green on payment_intent.succeeded.',
        dataOrigin: 'Stripe webhook → InvoiceService → Dashboard SSE',
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
        dataOrigin: 'SupplierService',
      },
      {
        name: 'Masked IBAN',
        description: 'Last-4 display after vault encrypt.',
        dataOrigin: 'SupplierService envelope encryption',
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
      'Restocked units that should not sell: move back to quarantine or scrap with a documented ADJUST — do not delete the RECEIVE.',
    ],
    correlations: [
      'Restock increases sellable ATP; scrap does not.',
      'Finance may issue credits separately from inventory disposition.',
    ],
    components: [
      {
        name: 'RMA board',
        description: 'Office approval workflow for customer returns.',
        dataOrigin: 'ReturnsService',
      },
      {
        name: 'Disposition controls',
        description: 'RESTOCK vs SCRAP after physical intake.',
        dataOrigin: 'ReturnsService + InventoryService',
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
        dataOrigin: 'ReturnsService.receive',
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
        description: 'Parent/child ledger walk for recall response.',
        dataOrigin: 'LotTraceService',
      },
      {
        name: 'CSV export',
        description: 'Audit package download.',
        dataOrigin: 'LotTraceService export',
      },
    ],
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
    components: [
      {
        name: 'Spatial canvas',
        description: 'Bins + picker dots on the warehouse graph.',
        dataOrigin: 'RtlsService + SSE position stream',
      },
      {
        name: 'Heat overlay',
        description: 'Congestion from recent movements.',
        dataOrigin: 'RtlsAnalyticsService',
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
        dataOrigin: 'BomService',
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
      'After ASSEMBLY ledger posts, reverse with compensating assembly/adjust entries — never delete completed production history.',
    ],
    correlations: [
      'Locks components away from outbound sales picks.',
      'Terminal labor timesheets attach cost to the order.',
    ],
    components: [
      {
        name: 'Allocate Components',
        description: 'Row-locks raw levels for the run.',
        dataOrigin: 'ProductionOrderService',
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
      'Stop timesheet and post assemble — ledger ASSEMBLY consume + receive FG.',
    ],
    reversals: [
      'Stop a timesheet without assemble if the run aborts (labor cost may still apply).',
      'Wrong assemble: manager posts compensating ASSEMBLY/ADJUST with OFFLINE or ERROR_CORRECTION attribution.',
    ],
    correlations: [
      'Finished goods become allocatable for sales immediately after assemble.',
    ],
    components: [
      {
        name: 'Timesheet controls',
        description: 'Labor duration × rate attached to the production order.',
        dataOrigin: 'ProductionTerminalService',
      },
      {
        name: 'Assemble action',
        description: 'Dual ledger write (consume components + receive finished goods).',
        dataOrigin: 'ProductionOrderService.assemble',
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
      'After confirm, reverse with a positive ADJUST to the stockroom attributed to the manager, referencing the original issue.',
    ],
    correlations: [
      'Charges the cost center budget; does not affect customer allocations.',
    ],
    components: [
      {
        name: 'Cost center picker',
        description: 'Budget clearance gate before issue.',
        dataOrigin: 'CostCenterService + InternalConsumptionService',
      },
      {
        name: 'Issue confirm',
        description: 'ADJUST ledger write against the stockroom.',
        dataOrigin: 'InternalConsumptionService',
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
      'After sync, reverse via depot ADJUST/TRANSFER back onto the van with manager reason codes.',
    ],
    correlations: [
      'Reorder-point triggers truck replenishment from the warehouse.',
    ],
    components: [
      {
        name: 'Van scan field',
        description: 'Consumes from VEHICLE location on the tech truck.',
        dataOrigin: 'FieldService / InventoryService',
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
      'Headline KPIs may come from CQRS snapshots, not live aggregation.',
    ],
    components: [
      {
        name: 'Recharts boards',
        description: 'Visual analytics for profit, COGS, and turns.',
        dataOrigin: 'ReportingService + DashboardKpiSnapshot',
      },
    ],
  },

  '/settings': {
    title: 'Organization settings',
    purpose:
      'Tenant-wide configuration hub — profile defaults, users, warehouses, inventory rules, documents, security, reconciliation, accounting, integrations, partner mesh, operations, sync conflicts, and cost centers.',
    flow: [
      'Open the tab that matches the change you need (Operations for floor rules, Users for roles/LBAC).',
      'Save — audit_log records the actor and JSON diff.',
      'Confirm floor devices pick up the new rule on next action.',
    ],
    reversals: [
      'Toggle the rule back; every change is append-only audited (no silent history wipe).',
      'Fintech / billing subsections are OWNER-scoped — reverse subscription changes in the billing portal if needed.',
    ],
    correlations: [
      'Blind receiving and variance thresholds change what pickers may post without manager review.',
      'User warehouse assignments (LBAC) scope which bins and documents each role can see.',
    ],
    components: [
      {
        name: 'Settings tabs',
        description: 'Profile, Users, Warehouses, Inventory, Documents, Security, Reconciliation, Accounting, Integrations, Mesh, Operations, Sync Conflicts, Cost Centers.',
        dataOrigin: 'TenantSettingsService + SettingsPage tab router',
      },
      {
        name: 'Operations toggles',
        description: 'Global floor policy (blind receiving, adjustment limits, scanner options).',
        dataOrigin: 'TenantSettingsService (operations map) + AuditLogService',
      },
      {
        name: 'Users tab',
        description: 'Role and warehouse assignments (LBAC).',
        dataOrigin: 'UserAdminService',
      },
    ],
  },

  '/settings?tab=profile': {
    title: 'Settings — Profile',
    purpose: 'Your user profile and the default organization identity shown across the app.',
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
        description: 'Personal identity and notification preferences for the signed-in user.',
        dataOrigin: 'UserProfileService',
      },
    ],
  },

  '/settings?tab=users': {
    title: 'Settings — Users',
    purpose:
      'Invite and manage tenant users, assign roles (OWNER / ADMIN / WAREHOUSE_MANAGER / PICKER / VIEWER / B2B_CUSTOMER), and scope warehouse access via LBAC.',
    flow: [
      'Invite a user or open an existing account.',
      'Assign one or more roles that match their job.',
      'Check the warehouses they may access (LBAC) — pickers only see assigned buildings.',
      'Save — the next login enforces the new capabilities.',
    ],
    reversals: [
      'Remove a role or warehouse checkbox and save again.',
      'Deactivate rather than delete users tied to historical ledger actors.',
      'OWNER cannot be casually demoted — transfer ownership first.',
    ],
    correlations: [
      'LBAC warehouse assignments gate which bins, waves, and documents appear for WAREHOUSE_MANAGER and PICKER.',
      'ADMIN/OWNER can change tenant rules; PICKER cannot open Operations toggles.',
    ],
    components: [
      {
        name: 'User grid',
        description: 'Tenant accounts with invite status and role chips.',
        dataOrigin: 'UserAdminService',
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
        dataOrigin: 'UserAdminService + LBAC warehouse membership',
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
    purpose: 'Define buildings, zones, and bins that LBAC and putaway rules reference.',
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
      'User LBAC membership must include a warehouse before pickers can scan there.',
    ],
    components: [
      {
        name: 'Warehouse list',
        description: 'Tenant locations with codes and active flags.',
        dataOrigin: 'WarehouseService / TenantLocationService',
      },
      {
        name: 'Warehouse visualizer',
        description: 'Spatial layout editor for bins and edges.',
        dataOrigin: 'WarehouseVisualizer + Rtls graph',
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
        dataOrigin: 'TenantSettingsService (inventory map)',
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
        dataOrigin: 'DocumentTemplateService',
      },
    ],
  },

  '/settings?tab=security': {
    title: 'Settings — Security & SSO',
    purpose: 'SSO configuration, session policy, and authentication hardening for the tenant.',
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
      'Affects how OWNER/ADMIN/PICKER authenticate; does not change warehouse LBAC by itself.',
    ],
    components: [
      {
        name: 'SSO config',
        description: 'Identity provider connection for the tenant.',
        dataOrigin: 'SsoConfigService',
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
      'Reconciliation jobs do not delete ledger rows — they report drift for manager ADJUST.',
    ],
    correlations: [
      'Pairs with Accounting Sync and Cycle Counts when numbers disagree.',
    ],
    components: [
      {
        name: 'Reconciliation panel',
        description: 'Drift reports between InvSys levels and external books.',
        dataOrigin: 'ReconciliationService',
      },
    ],
  },

  '/settings?tab=accounting': {
    title: 'Settings — Accounting Sync',
    purpose: 'Connect QuickBooks/Xero (or similar) so invoices and journals flow through the outbox.',
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
      'Integrations hub also deep-links here for accounting adapters.',
    ],
    components: [
      {
        name: 'Accounting Sync panel',
        description: 'Adapter connection, tax maps, and sync log.',
        dataOrigin: 'AccountingSyncService / outbox adapters',
        statuses: {
          SYNCED: 'Last payload accepted by the accounting system.',
          PENDING: 'Queued in the outbox.',
          FAILED: 'Needs retry or field correction.',
          SKIPPED: 'Intentionally not sent.',
        },
      },
    ],
  },

  '/settings?tab=integrations': {
    title: 'Settings — Integrations',
    purpose:
      'Wire e-commerce storefronts and accounting webhooks so orders and payments land in InvSys without double entry.',
    flow: [
      'Choose an e-commerce or webhook connector.',
      'Paste API credentials / webhook secrets.',
      'Enable the channel and verify a test order or payment event.',
    ],
    reversals: [
      'Disable a connector to stop inbound events; already-imported sales orders stay in the outbound pipeline.',
      'Rotate webhook secrets if a key leaks — update both InvSys and the external dashboard.',
    ],
    correlations: [
      'E-commerce webhooks create/update sales orders that still allocate and ship like office-entered SOs.',
      'Accounting webhooks (and Stripe) flip invoice PAID and refresh dashboard SSE.',
    ],
    components: [
      {
        name: 'E-commerce connectors',
        description: 'Storefront channels that push orders into SalesOrderService.',
        dataOrigin: 'IntegrationsService (e-commerce adapters)',
      },
      {
        name: 'Accounting / payment webhooks',
        description: 'Inbound payment and journal events (Stripe, accounting adapters).',
        dataOrigin: 'WebhookIngress + InvoiceService / AccountingSyncService',
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
        dataOrigin: 'CrossTenantMeshBridgeService + PartnerCatalogMapping',
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
      'Toggle the rule back; every change is append-only audited (no silent history wipe).',
      'Raising an adjustment limit does not auto-approve past PENDING_MANAGER_REVIEW counts — managers still close those lines.',
    ],
    correlations: [
      'Blind receiving and variance thresholds change what pickers may post without manager review.',
      'Adjustment limits feed CycleCountService escalation to PENDING_MANAGER_REVIEW.',
    ],
    components: [
      {
        name: 'Operations toggles',
        description: 'Global floor policy: blind receiving, scanner options, and related tenant rules.',
        dataOrigin: 'TenantSettingsService (operations map)',
      },
      {
        name: 'Adjustment limits',
        description: 'Maximum variance / adjust magnitude before manager review is required.',
        dataOrigin: 'TenantSettingsService → CycleCountService thresholds',
      },
      {
        name: 'Audit Log',
        description: 'Append-only history of settings and sensitive admin actions (actor + JSON diff).',
        dataOrigin: 'AuditLogService / ActivityTimeline',
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
    purpose: 'Adjudicate parked offline floor mutations that failed business rules on replay.',
    flow: [
      'Open a PARKED conflict.',
      'Correct schema fields if needed.',
      'Approve & Re-process (OFFLINE_CONFLICT_OVERRIDE) or Discard.',
    ],
    reversals: [
      'Discard permanently drops the parked scan — do not re-scan the same Idempotency-Key expecting a duplicate post.',
      'Approve posts under the manager identity; reverse later only with a compensating ledger ADJUST if physical truth differs.',
    ],
    correlations: [
      'Same board is reachable from Exceptions Sync tab and the dashboard banner.',
      'Pickers keep working while managers clear the quarantine.',
    ],
    components: [
      {
        name: 'Sync Conflicts panel',
        description: 'Schema-driven form for parked offline mutations.',
        dataOrigin: 'OfflineMutationQueue / SyncConflictsPanel',
        statuses: {
          PARKED: 'Waiting for manager adjudication.',
          APPROVED: 'Re-processed with OFFLINE_CONFLICT_OVERRIDE.',
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
      'After issue, reverse stock with a manager ADJUST referencing the original consumption.',
    ],
    correlations: [
      'Issue Supplies on the floor reads these centers for budget clearance.',
    ],
    components: [
      {
        name: 'Cost center list',
        description: 'Budget-bearing internal accounts.',
        dataOrigin: 'CostCenterService',
      },
      {
        name: 'Internal requisitions',
        description: 'Approval workflow before stockroom issue.',
        dataOrigin: 'InternalConsumptionService',
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
      'Deep-links from the header avatar; Users tab remains the place for role/LBAC changes.',
    ],
    components: [
      {
        name: 'Profile settings form',
        description: 'Personal account fields outside the tabbed settings hub.',
        dataOrigin: 'UserProfileService',
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
        dataOrigin: 'BillingService / Stripe Customer Portal',
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
      'Uses invoice PAID signals and open AR from InvoiceService.',
    ],
    components: [
      {
        name: 'Fintech dashboard',
        description: 'Cash position and financing entry points.',
        dataOrigin: 'FintechService',
      },
    ],
  },

  '/settings/integrations': {
    title: 'Integrations Hub',
    purpose: 'Hub that routes into e-commerce, accounting, and operations integration surfaces.',
    flow: [
      'Pick the connector category (storefront, accounting, or operations).',
      'Follow the deep-link into the matching Settings tab or adapter page.',
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
        description: 'Navigation into webhook and adapter setup.',
        dataOrigin: 'IntegrationsHubPage → IntegrationsService',
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
        dataOrigin: 'ImportService',
      },
      {
        name: 'Preflight grid',
        description: 'Ready vs blocked rows before commit.',
        dataOrigin: 'ImportService preflight',
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
        dataOrigin: 'PortalService / ShowroomCatalogService',
      },
      {
        name: 'Order tracker',
        description: 'Status chips only — no bin maps.',
        dataOrigin: 'PortalService → SalesOrderService statuses',
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
export function resolveKnowledgeContext(pathname: string, search = ''): RouteKnowledge | null {
  const path = normalizePathname(pathname.includes('?') ? pathname.split('?')[0]! : pathname);
  const searchPart = pathname.includes('?') && !search
    ? pathname.slice(pathname.indexOf('?'))
    : search;

  if (path === '/settings') {
    const tab = readSettingsTab(searchPart);
    if (tab) {
      const tabKey = `/settings?tab=${tab}`;
      if (ROUTE_KNOWLEDGE[tabKey]) {
        return ROUTE_KNOWLEDGE[tabKey]!;
      }
    }
    return (
      ROUTE_KNOWLEDGE['/settings'] ??
      ROUTE_KNOWLEDGE['/settings?tab=profile'] ??
      null
    );
  }

  const exactKey = knowledgeContextKey(path, searchPart);
  if (ROUTE_KNOWLEDGE[exactKey]) {
    return ROUTE_KNOWLEDGE[exactKey]!;
  }

  const prefixHit = matchLongestPrefix(path);
  if (prefixHit) {
    return prefixHit;
  }

  if (path.startsWith('/showroom')) {
    return ROUTE_KNOWLEDGE['/showroom'] ?? null;
  }

  return null;
}

/** Back-compat: pathname may include ?query */
export function resolveRouteKnowledge(pathname: string): RouteKnowledge | null {
  const raw = (pathname || '/').split('#')[0] || '/';
  if (raw.includes('?')) {
    const qIndex = raw.indexOf('?');
    return resolveKnowledgeContext(raw.slice(0, qIndex), raw.slice(qIndex));
  }
  return resolveKnowledgeContext(raw, '');
}

function formatComponentForChat(component: RouteKnowledgeComponent): string {
  const parts = [`${component.name}: ${component.description} (via ${component.dataOrigin})`];
  if (component.statuses && Object.keys(component.statuses).length > 0) {
    const statusBits = Object.entries(component.statuses)
      .map(([code, meaning]) => `${code}=${meaning}`)
      .join('; ');
    parts.push(`Statuses: ${statusBits}`);
  }
  return parts.join(' ');
}

/** Compact system-context block injected into support chat prompts. */
export function formatRouteKnowledgeForChat(
  routeKey: string,
  knowledge: RouteKnowledge | null,
): string {
  if (!knowledge) {
    return `System Context: The user is currently on ${routeKey}. No localized page playbook is registered. Emphasize safe reversals that never delete append-only ledger rows.`;
  }

  const componentBits = knowledge.components.map(formatComponentForChat).join(' | ');
  const reversals = knowledge.reversals.join(' ');

  return [
    `System Context: The user is currently on the ${knowledge.title} page (${routeKey}).`,
    `Purpose: ${knowledge.purpose}`,
    `Components: ${componentBits}`,
    `Reversal mechanism: ${reversals}`,
    'Emphasize how to safely reverse or undo transactions without corrupting the append-only inventory ledger.',
    'User Query:',
  ].join(' ');
}
