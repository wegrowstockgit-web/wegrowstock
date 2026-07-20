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
        graph.upsertNode("flow-exception", "FLOW", "Fulfillment exception", "ops-skip-and-flag-exceptions");
        graph.upsertNode("flow-cross-dock", "FLOW", "Cross-dock routing", "ops-cross-dock-intercept");

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

        log.info("Support GraphRAG seeded nodes={}", graph.nodeCount());
    }
}
