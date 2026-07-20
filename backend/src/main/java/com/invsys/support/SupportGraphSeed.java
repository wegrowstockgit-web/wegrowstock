package com.invsys.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds GraphRAG nodes/edges: zones, flows, and doc anchors with 2-hop traversable links.
 */
@Component
@Order(41)
public class SupportGraphSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupportGraphSeed.class);

    private final SupportGraphRepository graph;

    public SupportGraphSeed(SupportGraphRepository graph) {
        this.graph = graph;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Physical zones
        graph.upsertNode("zone-dock", "ZONE", "Receiving Dock", null);
        graph.upsertNode("zone-racks", "ZONE", "Storage Racks / Bins", null);
        graph.upsertNode("zone-staging", "ZONE", "Shipping Staging", null);

        // Digital flows / entities
        graph.upsertNode("flow-procurement", "FLOW", "Procurement", "office-create-po");
        graph.upsertNode("flow-fulfillment", "FLOW", "Fulfillment", "office-allocate-wave");
        graph.upsertNode("entity-purchase-order", "ENTITY", "PurchaseOrder", "office-create-po");
        graph.upsertNode("entity-sales-order", "ENTITY", "SalesOrder", "office-allocate-wave");
        graph.upsertNode("entity-bin", "ENTITY", "Bin", "picker-inbound-receive");
        graph.upsertNode("role-picker", "ROLE", "Picker", "scanner-hardware-basics");

        // Doc anchors
        graph.upsertNode("doc-inbound", "DOC", "Inbound receive", "picker-inbound-receive");
        graph.upsertNode("doc-allocate", "DOC", "Allocate & wave", "office-allocate-wave");
        graph.upsertNode("doc-po", "DOC", "Create PO", "office-create-po");
        graph.upsertNode("doc-damage", "DOC", "Damaged item", "manager-damaged-exception");
        graph.upsertNode("doc-showroom", "DOC", "B2B showroom", "b2b-showroom-orders");
        graph.upsertNode("doc-scanner", "DOC", "Scanner basics", "scanner-hardware-basics");
        graph.upsertNode("doc-offline-parking", "DOC", "Offline conflict parking", "ops-offline-mutation-parking");
        graph.upsertNode("doc-skip-flag", "DOC", "Skip & Flag exceptions", "ops-skip-and-flag-exceptions");
        graph.upsertNode("doc-cross-dock", "DOC", "Cross-dock intercept", "ops-cross-dock-intercept");
        graph.upsertNode("doc-landed-cost", "DOC", "Landed cost distribution", "ops-landed-cost-distribution");
        graph.upsertNode("doc-fefo-credit", "DOC", "FEFO allocation & credit holds", "ops-fefo-allocation-credit-holds");
        graph.upsertNode("doc-ledger-reverse", "DOC", "Append-only ledger reversals", "ops-append-only-ledger-reversals");
        graph.upsertNode("doc-blind-count", "DOC", "Blind cycle count escalation", "ops-blind-cycle-count-escalation");
        graph.upsertNode("doc-conflict-panel", "DOC", "Offline conflict panel resolve", "ops-offline-conflict-panel-resolve");
        graph.upsertNode("doc-status-codes", "DOC", "PO/SO/Invoice/RMA status guide", "ops-status-codes-po-so-invoice-rma");
        graph.upsertNode("flow-exception", "FLOW", "Fulfillment exception", "ops-skip-and-flag-exceptions");
        graph.upsertNode("flow-cross-dock", "FLOW", "Cross-dock routing", "ops-cross-dock-intercept");
        graph.upsertNode("flow-cycle-count", "FLOW", "Blind cycle count", "ops-blind-cycle-count-escalation");
        graph.upsertNode("flow-credit", "FLOW", "Customer credit hold", "ops-fefo-allocation-credit-holds");
        graph.upsertNode("entity-invoice", "ENTITY", "Invoice", "ops-status-codes-po-so-invoice-rma");
        graph.upsertNode("entity-rma", "ENTITY", "ReturnOrder", "ops-status-codes-po-so-invoice-rma");
        graph.upsertNode("entity-production-order", "ENTITY", "ProductionOrder", "ops-status-codes-po-so-invoice-rma");

        // Relationships
        graph.upsertEdge("entity-purchase-order", "entity-sales-order", "FULFILLS");
        graph.upsertEdge("entity-purchase-order", "doc-inbound", "RECEIVES_INTO");
        graph.upsertEdge("doc-inbound", "entity-bin", "PUTAWAY_TO");
        graph.upsertEdge("entity-bin", "zone-racks", "LOCATED_IN");
        graph.upsertEdge("zone-dock", "doc-inbound", "HOSTS");
        graph.upsertEdge("role-picker", "entity-bin", "SCANS");
        graph.upsertEdge("role-picker", "doc-scanner", "USES");
        graph.upsertEdge("doc-allocate", "entity-sales-order", "ALLOCATES");
        graph.upsertEdge("doc-allocate", "zone-staging", "STAGES_TO");
        graph.upsertEdge("entity-sales-order", "flow-fulfillment", "PART_OF");
        graph.upsertEdge("flow-procurement", "entity-purchase-order", "CREATES");
        graph.upsertEdge("doc-inbound", "doc-allocate", "UNLOCKS");
        graph.upsertEdge("doc-allocate", "doc-po", "DEPENDS_ON_STOCK_FROM");
        graph.upsertEdge("doc-damage", "doc-allocate", "UNBLOCKS");
        graph.upsertEdge("doc-showroom", "entity-sales-order", "TRACKS_STATUS_OF");
        // Conflict-resolution graph: parking ↔ skip/flag ↔ cross-dock ↔ allocate
        graph.upsertEdge("role-picker", "doc-offline-parking", "PARKS_CONFLICTS_IN");
        graph.upsertEdge("doc-offline-parking", "doc-skip-flag", "RELATED_TO");
        graph.upsertEdge("doc-skip-flag", "flow-exception", "OPENS");
        graph.upsertEdge("doc-skip-flag", "doc-allocate", "RELEASES_ALLOCATION_FOR");
        graph.upsertEdge("doc-inbound", "doc-cross-dock", "MAY_TRIGGER");
        graph.upsertEdge("doc-cross-dock", "flow-cross-dock", "ROUTES_VIA");
        graph.upsertEdge("doc-cross-dock", "zone-staging", "DIVERTS_TO");
        graph.upsertEdge("doc-cross-dock", "doc-allocate", "AUTO_ALLOCATES");
        graph.upsertEdge("entity-sales-order", "doc-cross-dock", "BACKORDER_DEMAND_FOR");
        // Ops playbooks: landed cost, FEFO/credit, ledger reverse, blind count, conflict panel, statuses
        graph.upsertEdge("doc-po", "doc-landed-cost", "APPLIES_SURCHARGE_VIA");
        graph.upsertEdge("doc-landed-cost", "entity-purchase-order", "VALUES");
        graph.upsertEdge("doc-landed-cost", "doc-inbound", "BEFORE_RECEIVE");
        graph.upsertEdge("flow-procurement", "doc-landed-cost", "INCLUDES");
        graph.upsertEdge("doc-allocate", "doc-fefo-credit", "USES_POLICY");
        graph.upsertEdge("doc-fefo-credit", "entity-sales-order", "GATES");
        graph.upsertEdge("doc-fefo-credit", "flow-credit", "ENFORCES");
        graph.upsertEdge("doc-fefo-credit", "flow-fulfillment", "FEEDS");
        graph.upsertEdge("doc-ledger-reverse", "doc-damage", "CORRECTS");
        graph.upsertEdge("doc-ledger-reverse", "doc-skip-flag", "RELATED_TO");
        graph.upsertEdge("doc-ledger-reverse", "doc-offline-parking", "RELATED_TO");
        graph.upsertEdge("doc-blind-count", "flow-cycle-count", "RUNS");
        graph.upsertEdge("doc-blind-count", "entity-bin", "COUNTS");
        graph.upsertEdge("doc-blind-count", "doc-ledger-reverse", "MAY_NEED");
        graph.upsertEdge("role-picker", "doc-blind-count", "PERFORMS");
        graph.upsertEdge("doc-offline-parking", "doc-conflict-panel", "RESOLVED_IN");
        graph.upsertEdge("doc-conflict-panel", "flow-exception", "SURFACES_IN");
        graph.upsertEdge("doc-conflict-panel", "doc-ledger-reverse", "MAY_POST");
        graph.upsertEdge("doc-status-codes", "entity-purchase-order", "DESCRIBES");
        graph.upsertEdge("doc-status-codes", "entity-sales-order", "DESCRIBES");
        graph.upsertEdge("doc-status-codes", "entity-invoice", "DESCRIBES");
        graph.upsertEdge("doc-status-codes", "entity-rma", "DESCRIBES");
        graph.upsertEdge("doc-status-codes", "entity-production-order", "DESCRIBES");
        graph.upsertEdge("doc-showroom", "doc-status-codes", "READS");
        graph.upsertEdge("doc-allocate", "doc-status-codes", "DEPENDS_ON");

        log.info("Support GraphRAG seeded nodes={}", graph.nodeCount());
    }
}
