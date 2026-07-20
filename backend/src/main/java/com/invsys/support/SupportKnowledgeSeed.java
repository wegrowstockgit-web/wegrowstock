package com.invsys.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ingests platform manuals / runbooks into pgvector on startup (idempotent upsert by slug).
 */
@Component
@Order(40)
public class SupportKnowledgeSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupportKnowledgeSeed.class);

    private final SupportKnowledgeRepository repository;
    private final EmbeddingModel embeddingModel;

    public SupportKnowledgeSeed(SupportKnowledgeRepository repository, EmbeddingModel embeddingModel) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        int loaded = 0;
        for (Doc doc : DOCS) {
            float[] embedding = embeddingModel.embed(doc.title() + "\n" + doc.body());
            repository.upsert(
                    doc.slug(),
                    doc.title(),
                    doc.body(),
                    doc.roles(),
                    doc.routes(),
                    doc.source(),
                    embedding);
            loaded++;
        }
        log.info("Support RAG knowledge seeded chunks={} total={}", loaded, repository.count());
    }

    private record Doc(
            String slug,
            String title,
            String body,
            List<String> roles,
            List<String> routes,
            String source
    ) {
    }

    private static final List<Doc> DOCS = List.of(
            new Doc(
                    "picker-inbound-receive",
                    "Inbound receive on the scanner",
                    """
                    Floor pickers process inbound shipments on the mobile warehouse shell — never by creating \
                    purchase orders on the desktop.

                    Steps:
                    1. Open Inbound Receive or Fulfillment receive mode on your handheld.
                    2. Scan the PO / ASN barcode on the paperwork so expected lines appear.
                    3. Scan each product barcode (capture lot/expiry when prompted).
                    4. Follow directed putaway to the suggested bin path.
                    5. Scan the bin barcode to confirm putaway; the ledger records the receive immediately.

                    Office managers create PO documents on desktop; pickers never create POs — only scan-receive.
                    """,
                    List.of("PICKER", "WAREHOUSE_MANAGER"),
                    List.of("/fulfillment", "/inbound/receive"),
                    "USER_GUIDE.md#inbound"),
            new Doc(
                    "office-create-po",
                    "Create a purchase order (office)",
                    """
                    Warehouse managers and admins create purchase orders on the desktop Purchase Orders page. \
                    Pickers do not create POs. After submit, floor staff receive against the PO with scanners.
                    """,
                    List.of("OWNER", "ADMIN", "WAREHOUSE_MANAGER"),
                    List.of("/purchase-orders"),
                    "USER_GUIDE.md#po"),
            new Doc(
                    "b2b-showroom-orders",
                    "B2B showroom orders and catalog status",
                    """
                    B2B customers shop only in the showroom (/showroom/catalog, orders, checkout, billing). \
                    You can browse the catalog with tier pricing, place orders, and track order status in the \
                    showroom order tracker.

                    Customers cannot view internal warehouse maps, bin locations, inventory ledger rows, or \
                    allocation internals. If you need delivery status, use showroom Orders — not warehouse tools.
                    """,
                    List.of("B2B_CUSTOMER"),
                    List.of("/showroom"),
                    "USER_GUIDE.md#showroom"),
            new Doc(
                    "manager-damaged-exception",
                    "Damaged item on the floor",
                    """
                    When a warehouse manager finds damaged stock during pick or putaway:
                    1. On the handheld, use Skip & Flag / fulfillment exception for the line so the order lock \
                       can be released cleanly for that unit.
                    2. From Exceptions (office) or floor flows, confirm disposition.
                    3. Post an inventory adjustment or start a cycle count on the affected bin so on-hand matches reality.
                    4. If the wave is blocked, re-allocate or release remaining lines after the exception is logged.

                    Prefer exception + adjust/count over silently changing quantities.
                    """,
                    List.of("WAREHOUSE_MANAGER", "ADMIN", "OWNER", "PICKER"),
                    List.of("/exceptions", "/rtls", "/fulfillment", "/cycle-counts"),
                    "USER_GUIDE.md#exceptions"),
            new Doc(
                    "office-allocate-wave",
                    "Allocate sales orders and release waves",
                    """
                    On the desktop Sales Orders grid: confirm the order, click Allocate to reserve stock \
                    (FEFO/lots applied), then Generate Wave to release pick tasks to the floor. Use the \
                    products/orders virtualized grids and Columns menu to focus on allocation status.
                    """,
                    List.of("OWNER", "ADMIN", "WAREHOUSE_MANAGER"),
                    List.of("/sales-orders", "/dashboard", "/products"),
                    "USER_GUIDE.md#allocate"),
            new Doc(
                    "scanner-hardware-basics",
                    "Hardware scanner wedge basics",
                    """
                    Rugged scanners send barcodes as fast keyboard wedges ending with Enter. Keep focus on the \
                    fulfillment scan field. Wrong SKUs flash an error and block the pick. Unlock idle screens \
                    with your 4-digit shift PIN before scanning.
                    """,
                    List.of("PICKER", "WAREHOUSE_MANAGER"),
                    List.of("/fulfillment", "/inbound/receive"),
                    "USER_GUIDE.md#scanner")
    );
}
