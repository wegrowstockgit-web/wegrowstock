package com.invsys.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import com.invsys.modules.sales.domain.Invoice;

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
                    5. Scan the bin barcode to confirm putaway; stock updates immediately.

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

                    Customers cannot view internal warehouse maps, bin locations, or how staff reserve stock. \
                    If you need delivery status, use showroom Orders — not warehouse tools.
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
                    "USER_GUIDE.md#scanner"),
            new Doc(
                    "ops-offline-mutation-parking",
                    "Offline scans that need manager review",
                    """
                    If a scan cannot finish after the handheld reconnects (wrong quantity, stock already moved, \
                    bin locked), the picker is never stuck on an error toast.

                    What happens:
                    1. While offline, the scanner quietly queues the scan.
                    2. When the device reconnects, the queue tries again.
                    3. If the business rule still fails, the scan is parked for the office.
                    4. The handheld shows that the scan is parked — the picker keeps working.
                    5. Managers open Dashboard → Sync Conflicts (or Exceptions) to discard or approve later.

                    Teaching tip: tell pickers "your scan is parked for the office — keep moving." Tell managers \
                    to clear parked scans before forcing a stock correction.
                    """,
                    List.of("PICKER", "WAREHOUSE_MANAGER", "ADMIN", "OWNER"),
                    List.of("/fulfillment", "/inbound/receive", "/dashboard", "/exceptions"),
                    "SEQUENCE_FLOW.md#12"),
            new Doc(
                    "ops-skip-and-flag-exceptions",
                    "Fulfillment exceptions (Skip & Flag)",
                    """
                    When a picker hits an empty bin, damaged stock, or an unpickable line, use **Skip & Flag** — \
                    do not invent a stock number on the floor.

                    What Skip & Flag does:
                    1. The reserved stock for that line is released so the order can be re-planned.
                    2. No stock correction is written yet — physical truth is still unknown.
                    3. An exception appears on the office Exceptions board.
                    4. Warehouse managers resolve it from Exceptions: confirm what happened, then post a \
                       stock correction only after investigation.

                    Pickers: Skip & Flag keeps the wave moving. Managers: resolve exceptions before re-allocating.
                    """,
                    List.of("PICKER", "WAREHOUSE_MANAGER", "ADMIN", "OWNER"),
                    List.of("/fulfillment", "/exceptions", "/sales-orders"),
                    "SEQUENCE_FLOW.md#7.4"),
            new Doc(
                    "ops-cross-dock-intercept",
                    "Cross-docking interrupts on receive",
                    """
                    When inbound stock matches an open BACKORDERED sales order, receiving can divert the \
                    freight straight to an outbound staging lane instead of deep storage.

                    Flow:
                    1. Office confirms a sales order with no stock → it stays BACKORDERED.
                    2. A matching purchase order is submitted for the same item.
                    3. On floor receive, the handheld may show "go to staging" instead of a putaway bin.
                    4. The picker scans the staging barcode to confirm.
                    5. The waiting sales order becomes ready for picking (ALLOCATED) without a long putaway detour.

                    Instruct operators: do not force deep-storage putaway when the staging overlay appears — \
                    staging shortens dock-to-ship time for backorders.
                    """,
                    List.of("PICKER", "WAREHOUSE_MANAGER", "ADMIN", "OWNER"),
                    List.of("/inbound/receive", "/fulfillment", "/sales-orders", "/purchase-orders"),
                    "SEQUENCE_FLOW.md#5.4"),
            new Doc(
                    "ops-landed-cost-distribution",
                    "How landed cost spreads into unit cost on a PO",
                    """
                    Freight, customs, and similar surcharges are not a separate mystery number — they roll into \
                    each line's unit valuation so inventory value stays honest after receive.

                    Steps (office):
                    1. Open Purchase Orders and select the PO (or create lines with base unit cost first).
                    2. Attach the landed-cost surcharge (freight, customs duty, or similar).
                    3. Choose how to spread it — by value, weight, or quantity (the system uses the strategy \
                       for that cost type).
                    4. Confirm. The engine distributes the surcharge across lines and updates unit costs.
                    5. When the floor receives, those updated unit costs are what stock value uses.

                    Tip: do this before or right after Submit so receive does not post with incomplete cost. \
                    Pickers do not enter landed cost on the scanner — office owns the surcharge.
                    """,
                    List.of("OWNER", "ADMIN", "WAREHOUSE_MANAGER"),
                    List.of("/purchase-orders", "/inbound/receive", "/invoices"),
                    "SEQUENCE_FLOW.md#5.1"),
            new Doc(
                    "ops-fefo-allocation-credit-holds",
                    "FEFO lot picks and why credit holds stop orders",
                    """
                    When you Allocate a sales order, the system reserves real stock — not just a number on a sheet.

                    FEFO (first expiry, first out):
                    1. Confirm the sales order on the desktop Sales Orders page.
                    2. Click Allocate. The system picks lots with the soonest expiry first (FEFO).
                    3. If stock is found → status ALLOCATED and those lots are reserved for the wave.
                    4. If no stock → BACKORDERED until inbound (or cross-dock) can fill the demand.
                    5. Generate Wave only after ALLOCATED so pickers get the right lot tasks.

                    Credit holds:
                    1. Accounts set each customer's credit limit under Customers.
                    2. On confirm or Allocate, the system checks open orders against that limit.
                    3. Over the limit → the order is held for review — stock is not waved until credit \
                       is cleared or raised.
                    4. Within limit → allocation proceeds normally.

                    Floor tip: if a wave never appears, ask the office whether the order is BACKORDERED or \
                    on credit hold — do not invent picks.
                    """,
                    List.of("OWNER", "ADMIN", "WAREHOUSE_MANAGER"),
                    List.of("/sales-orders", "/customers", "/dashboard"),
                    "SEQUENCE_FLOW.md#6.1"),
            new Doc(
                    "ops-append-only-ledger-reversals",
                    "How to reverse a bad stock movement",
                    """
                    Every receive, pick, ship, and adjust stays in history forever. You never delete or \
                    overwrite a past movement — that is how audits and lot trace stay trustworthy.

                    When a posted entry is wrong:
                    1. Do not ask anyone to "delete the stock history line."
                    2. From the office inventory tools, use **Reverse** / stock correction on the mistaken entry.
                    3. The system adds a new correcting movement that undoes the quantity while keeping the \
                       original visible.
                    4. Manager-approved offline fixes work the same way — a new attributed correction, not an erase.

                    Teaching tip: history stays; corrections stack. You can reverse a normal entry once; \
                    you cannot reverse a correction that already fixed something.
                    """,
                    List.of("OWNER", "ADMIN", "WAREHOUSE_MANAGER"),
                    List.of("/products", "/exceptions", "/dashboard", "/cycle-counts"),
                    "SEQUENCE_FLOW.md#7"),
            new Doc(
                    "ops-blind-cycle-count-escalation",
                    "Blind cycle counts: auto-approve vs manager review",
                    """
                    Blind counts hide the expected quantity so the picker counts what is really in the bin.

                    Floor (picker):
                    1. Open count mode on the scanner and scan the directed bin.
                    2. Enter the physical quantity you see — no system target is shown.
                    3. Submit. Keep moving; the office handles large variances.

                    What happens next:
                    - Match or small variance → auto-approved; stock updates and the bin unlocks.
                    - Large variance → PENDING_MANAGER_REVIEW and the slot stays locked until a manager acts.

                    Office (manager):
                    1. Open Cycle Counts variance queue.
                    2. Choose **Approve Adjustment** (credited to you) or request a recount.
                    3. Do not tell pickers to invent a separate adjust outside the count workflow.

                    Tip: big swings need human eyes; small ones should not block the aisle.
                    """,
                    List.of("PICKER", "WAREHOUSE_MANAGER", "ADMIN", "OWNER"),
                    List.of("/cycle-counts", "/fulfillment", "/products"),
                    "SEQUENCE_FLOW.md#7.2"),
            new Doc(
                    "ops-offline-conflict-panel-resolve",
                    "Resolve parked scans in the Conflict Panel",
                    """
                    When a picker was offline and a scan cannot finish after reconnecting, the scan parks for \
                    the office — the handheld keeps working. Managers finish the story here.

                    Steps:
                    1. Open Dashboard → Sync Conflicts (or Exceptions → Sync Conflicts).
                    2. Select a parked conflict. Read the human summary.
                    3. Correct any editable fields (locked fields stay locked on purpose).
                    4. Choose one action:
                       - **Discard** — drop the parked scan. Do not re-scan the same barcode afterward.
                       - **Approve & Re-process** — apply your corrections; stock posts under your name.
                    5. Confirm the related order or bin looks right, then clear the next conflict.

                    Tip: tell pickers their scan is parked, not lost. Only managers Discard or Approve.
                    """,
                    List.of("WAREHOUSE_MANAGER", "ADMIN", "OWNER"),
                    List.of("/dashboard", "/exceptions", "/fulfillment"),
                    "SEQUENCE_FLOW.md#12"),
            new Doc(
                    "ops-status-codes-po-so-invoice-rma",
                    "Status guide: PO, SO, Invoice, Production Order, RMA",
                    """
                    Quick chip meanings so office and floor speak the same language.

                    Purchase Order: DRAFT (editable) → SUBMITTED (firm; floor may receive) → IN_TRANSIT \
                    (freight moving) → PARTIALLY_RECEIVED (some qty in) → RECEIVED (complete).

                    Sales Order: DRAFT → CONFIRMED → ALLOCATED (lots reserved) or BACKORDERED (no stock) or \
                    NEEDS_REVIEW (often credit hold) → PARTIALLY_SHIPPED → SHIPPED → CLOSED. Any open state \
                    can become CANCELLED (releases allocations).

                    Invoice: DRAFT (not sent) → OPEN (awaiting payment) → PAID (settled). VOID cancels for \
                    finance and does not reverse warehouse stock.

                    Production Order: DRAFT → COMPONENTS_ALLOCATED (raw locked) → WIP / IN_ROUTING (floor \
                    assembly) → COMPLETED (finished goods in). CANCELLED releases component reservations.

                    RMA / Return: REQUESTED or PENDING_REVIEW → APPROVED (floor may receive) or REJECTED → \
                    RECEIVED after intake. Disposition RESTOCK vs SCRAP decides whether sellable stock returns.

                    Tip: if a wave or receive button is missing, check the status chip first — the document \
                    may not be ready for that step yet.
                    """,
                    List.of("OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "B2B_CUSTOMER"),
                    List.of(
                            "/purchase-orders",
                            "/sales-orders",
                            "/invoices",
                            "/manufacturing/orders",
                            "/returns",
                            "/showroom"),
                    "SEQUENCE_FLOW.md#status")
    );
}
