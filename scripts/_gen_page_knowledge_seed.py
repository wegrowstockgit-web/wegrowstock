"""Generate V131 page-knowledge seed SQL. Run from repo root."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "backend/invsys-core/src/main/resources/db/migration/V131__seed_all_page_knowledge.sql"


def j(value) -> str:
    return json.dumps(value, ensure_ascii=False)


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def row(
    route: str,
    category: str,
    title: str,
    summary: str,
    privileges: str,
    actions: list[str],
    mistakes: list[tuple[str, str, str]],
    tip: str,
) -> str:
    mistakes_json = [
        {"mistake": m, "solution": s, "requiredRole": r} for m, s, r in mistakes
    ]
    return f"""INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    {sql_str(route)},
    {sql_str(category)},
    {sql_str(title)},
    $pk${summary}$pk$,
    $pk${privileges}$pk$,
    $pk${j(actions)}$pk$::jsonb,
    $pk${j(mistakes_json)}$pk$::jsonb,
    $pk${tip}$pk$,
    'flyway-seed'
);
"""


MGR = "WAREHOUSE_MANAGER"
ADMIN = "ADMIN"
OWNER = "OWNER"
PICKER = "PICKER"
ANY = "ANY"

rows: list[str] = []


def add(*args):
    rows.append(row(*args))


# --- Core ---
add(
    "/dashboard",
    "Core",
    "Command Center",
    "Your daily weGrowStock overview of warehouse operations, active tasks, and system health. Headline KPIs (Stock Value, Low Stock Count, Open Orders) sit above Work Queue cards such as Needs Allocation and Ready to Invoice. The dashboard itself never changes inventory.",
    "Owners, Administrators, Warehouse Managers, Floor Pickers, and Viewers can open the dashboard. Pickers usually land on Fulfillment instead.",
    [
        "Scan Headline KPIs (Stock Value, Low Stock, Open Orders) for red or amber signals.",
        "Work Needs Allocation and Ready to Invoice cards first.",
        "Open Sync Conflicts or Exceptions banners when they appear.",
        "Drill into Sales Orders, Purchase Orders, or Fulfillment from the quick links.",
    ],
    [
        (
            "I dismissed a banner and thought the problem went away.",
            "Dismissing only hides the alert until the next refresh. Open Exceptions or Sync Conflicts and resolve the underlying item. The dashboard never posts a ledger entry.",
            MGR,
        ),
        (
            "Numbers look wrong after a fat-fingered receive.",
            "Do not type a correction here. Open the product Ledger History and have a manager click Reverse transaction, or run a cycle count. weGrowStock never erases the original row.",
            MGR,
        ),
    ],
    "Treat red KPI chips as a to-do list, not a report. Live warehouse totals come from floor and office activity — never from a hidden spreadsheet.",
)

# --- Inbound (requested + real WMS paths) ---
PO_SUMMARY = (
    "Draft purchase orders to restock your warehouse. Purpose: create inbound supply contracts against approved suppliers "
    "so the dock can receive freight against expected lines. Search, sort, and page the list — weGrowStock loads one page from the server."
)
PO_PRIV = "Owners, Administrators, and Warehouse Managers create and submit POs. Floor Pickers receive against submitted POs on Inbound Receive."
PO_ACTIONS = [
    "Click New PO, pick a supplier, and add SKU, quantity, unit cost, and UoM.",
    "Save as Draft, then Submit when the buy is firm.",
    "Mark In Transit when the vendor ships, then hand off to Floor receive.",
    "Never delete a PO that already has receipts — use RTV or a reversing receive.",
]
PO_MISTAKES = [
    (
        "I typed the wrong supplier price or quantity (100 instead of 10).",
        "If the PO is still Draft, edit the line. After Submit with no receipts, cancel the open lines and recreate. After receiving, a manager posts a Reverse Receipt / stock correction — never edit the posted ledger row.",
        MGR,
    ),
    (
        "I accidentally created a duplicate PO.",
        "Cancel the unused twin before anyone receives against it. If both were received, reverse the extra receipt and close the extra PO. History of both documents stays visible.",
        MGR,
    ),
    (
        "I picked the wrong supplier name because of a misspelling.",
        "Draft: change the supplier. Submitted with no receipts: cancel and recreate. Received: keep the PO, fix future buys on the supplier record, and use RTV if the freight must go back.",
        ADMIN,
    ),
]
PO_TIP = "Confirm unit cost out loud before Submit. A wrong price on a submitted PO becomes a finance problem; a wrong receive becomes a ledger reversal."

add("/purchase-orders", "Inbound", "Purchase Orders", PO_SUMMARY, PO_PRIV, PO_ACTIONS, PO_MISTAKES, PO_TIP)
add("/purchasing/orders", "Inbound", "Purchase Orders", PO_SUMMARY, PO_PRIV, PO_ACTIONS, PO_MISTAKES, PO_TIP)

SUP_SUMMARY = (
    "Vendor master data — legal name, payment terms, lead times, and masked banking details. "
    "Purchase orders require an approved supplier. Fix typos here; do not invent a second vendor for the same company."
)
SUP_PRIV = "Owners, Administrators, and Warehouse Managers maintain suppliers. Viewers can read. Floor Pickers do not edit vendor master data."
SUP_ACTIONS = [
    "Click Add supplier and enter the legal name exactly as invoices will show it.",
    "Set lead times that feed MRP reorder suggestions.",
    "Save banking details — weGrowStock shows them masked after save.",
    "Deactivate unused vendors instead of deleting ones tied to historical POs.",
]
SUP_MISTAKES = [
    (
        "I created a duplicate vendor because of a typo in the name.",
        "Keep the correctly spelled record. Point new POs at it and ask an Administrator to deactivate the twin. Historical POs stay on the original supplier — we do not merge ledger history.",
        ADMIN,
    ),
    (
        "I misspelled the remittance address.",
        "Edit the supplier and save again. Address typos do not change stock. If a check already went out, finance issues a new payment — not a stock reversal.",
        ADMIN,
    ),
]
SUP_TIP = "Search before you add. A duplicate vendor is the most common purchasing master-data error and it silently splits spend reports."

add("/suppliers", "Inbound", "Suppliers", SUP_SUMMARY, SUP_PRIV, SUP_ACTIONS, SUP_MISTAKES, SUP_TIP)
add("/purchasing/suppliers", "Inbound", "Suppliers", SUP_SUMMARY, SUP_PRIV, SUP_ACTIONS, SUP_MISTAKES, SUP_TIP)

MRP_SUMMARY = (
    "Material Requirements Planning reorder workspace. weGrowStock suggests buy quantities from demand, on-hand, and supplier lead times. "
    "This list can be long — it stays on virtualized scrolling, not page-by-page tables."
)
MRP_PRIV = "Owners, Administrators, and Warehouse Managers run MRP (mrp:run). Pickers and Viewers do not consolidate suggestions."
MRP_ACTIONS = [
    "Review suggested lines and uncheck anything you do not want.",
    "Consolidate selected suggestions into draft purchase orders.",
    "Open the new POs and confirm prices before Submit.",
]
MRP_MISTAKES = [
    (
        "I over-ordered because I accepted every calculated suggestion.",
        "Do not receive the extra. Cancel unreceived PO lines or create an RTV after goods arrive. Suggestions are advice, not a purchase.",
        MGR,
    ),
    (
        "I consolidated too early and created a PO for the wrong supplier.",
        "Cancel the draft/unreceived PO and re-run MRP after you fix the supplier on the product. Do not receive against the wrong vendor to 'make it match'.",
        MGR,
    ),
]
MRP_TIP = "Treat MRP like a shopping list you still proofread. Lead-time fat-fingers on the supplier record are a common reason suggestions look huge."

add("/mrp", "Inbound", "MRP Reorder", MRP_SUMMARY, MRP_PRIV, MRP_ACTIONS, MRP_MISTAKES, MRP_TIP)
add("/purchasing/mrp", "Inbound", "MRP Reorder", MRP_SUMMARY, MRP_PRIV, MRP_ACTIONS, MRP_MISTAKES, MRP_TIP)

RCV_SUMMARY = (
    "Inbound dock receiving bay. Scan the Purchase Order, verify physical quantities, capture GS1 / lot data, "
    "and put stock away. Over-receipts and damaged cartons must not be typed away — use Quarantine or RTV."
)
RCV_PRIV = "Floor Pickers and Warehouse Managers receive. Only a Warehouse Manager can approve an over-receipt outside tolerance."
RCV_ACTIONS = [
    "Scan the PO / ASN so expected lines appear.",
    "Scan each product (lot, expiry, or serial when prompted).",
    "Confirm quantity, then scan the putaway bin.",
    "Damaged boxes: move to a Quarantine location — do not receive as sellable.",
]
RCV_MISTAKES = [
    (
        "I over-received (typed 100 instead of 10, or the truck sent extras).",
        "If still in the undo window, undo the scan. After commit, a manager posts a Reverse Receipt or sends extras back on RTV. Over-receipt tolerance exceptions need a Warehouse Manager.",
        MGR,
    ),
    (
        "I scanned the wrong GS1 barcode / wrong product.",
        "Undo immediately if the flash is still up. After commit, stop and tell a manager — they reverse the receive and you scan the correct label. Never 'fix' it with a second opposite scan.",
        MGR,
    ),
    (
        "A carton arrived damaged.",
        "Receive into Quarantine (or Skip & Flag if your site uses that at the dock), photograph if asked, and let a manager disposition restock vs scrap. Do not put damaged goods on a pick face.",
        PICKER,
    ),
]
RCV_TIP = "If the badge says Offline - Caching Scans, stay extra precise. Parked receives wait in Exceptions → Sync Conflicts for a manager."

add("/inbound/receive", "Inbound", "Inbound Receiving", RCV_SUMMARY, RCV_PRIV, RCV_ACTIONS, RCV_MISTAKES, RCV_TIP)
add("/purchasing/receive", "Inbound", "Inbound Receiving", RCV_SUMMARY, RCV_PRIV, RCV_ACTIONS, RCV_MISTAKES, RCV_TIP)

RTV_SUMMARY = (
    "Return to Vendor chargebacks. Send defective, over-shipped, or refused inbound goods back to the supplier "
    "and record the credit memo against the original PO."
)
RTV_PRIV = "Warehouse Managers and Administrators create RTVs. Floor operators stage the freight."
RTV_ACTIONS = [
    "Open the original PO or the RTV workspace.",
    "Select lines and quantities actually going back.",
    "Confirm the vendor credit memo amount matches the paperwork.",
    "Hand the staged pallet to outbound shipping.",
]
RTV_MISTAKES = [
    (
        "I entered an incorrect vendor credit memo amount.",
        "Do not edit the posted RTV silently. Issue a correcting credit (or ask finance to void and re-issue). Stock already returned stays on the RTV ledger; money is a separate reversing document.",
        ADMIN,
    ),
    (
        "I returned the wrong quantity.",
        "If the truck has not left, adjust the RTV before ship. After ship, create a follow-up receive or a second RTV — never delete the first.",
        MGR,
    ),
]
RTV_TIP = "Match three numbers: PO line, physical cartons on the dock, and the supplier credit. If one disagrees, stop."

add("/purchasing/rtv", "Inbound", "Return to Vendor", RTV_SUMMARY, RTV_PRIV, RTV_ACTIONS, RTV_MISTAKES, RTV_TIP)

RET_SUMMARY = (
    "Customer returns (RMA) office. Authorize returns, then the floor receives them with condition photos. "
    "Restock increases sellable stock; scrap does not."
)
RET_PRIV = "Warehouse Managers and Administrators approve or deny RMAs. Floor operators receive on Returns receive."
RET_ACTIONS = [
    "Click New RMA / Create RMA and review the request.",
    "Choose Approve & Buy Label, Approve without Label, or Deny & Close.",
    "Use Receive terminal so the floor can scan the return.",
]
RET_MISTAKES = [
    (
        "I approved a return that should have been denied.",
        "After receive you cannot Deny. Quarantine the stock and let finance decide the credit. History stays.",
        MGR,
    ),
    (
        "I received damaged goods as pristine.",
        "The condition photo protects you. Ask a manager to move the units to Quarantine and post a correction before anyone picks them.",
        MGR,
    ),
]
RET_TIP = "Photos exist so nobody can later claim damaged goods were received as sellable."

add("/returns", "Inbound", "Returns / RMA", RET_SUMMARY, RET_PRIV, RET_ACTIONS, RET_MISTAKES, RET_TIP)
add(
    "/returns/receive",
    "Inbound",
    "Returns Receive (Floor)",
    "Scan returned goods against an approved RMA. Capture a Condition photo, then Confirm +1 per unit.",
    "Floor Pickers receive. Managers disposition restock vs scrap.",
    [
        "Scan the RMA barcode.",
        "Photograph the item's real condition.",
        "Tap Confirm +1 per unit, then Scan next RMA.",
    ],
    [
        (
            "I tapped Confirm +1 too many times.",
            "Tell a manager. A correction fixes the received quantity; the extra tap stays in the log.",
            MGR,
        )
    ],
    "Never receive a sealed mystery box as good stock — open it and photograph first.",
)

# --- Outbound ---
SO_SUMMARY = (
    "B2B and office sales orders. Confirm demand, Allocate stock (FEFO), then release a picking wave. "
    "Status chips include DRAFT, CONFIRMED, ALLOCATED, BACKORDERED, PARTIALLY_SHIPPED, SHIPPED, and CANCELLED. "
    "Un-allocate / Cancel releases reserved stock without erasing history."
)
SO_PRIV = "Owners, Administrators, and Warehouse Managers confirm, allocate, and invoice. Pickers fulfill released waves on Fulfillment."
SO_ACTIONS = [
    "Confirm a DRAFT order.",
    "Click Allocate to reserve on-hand (or leave BACKORDERED if short).",
    "Generate / release a picking wave for ALLOCATED orders.",
    "Use Un-allocate or Cancel to release reserved stock before pick.",
]
SO_MISTAKES = [
    (
        "I allocated the wrong order or the wrong quantity.",
        "Click Un-allocate to release reservations, fix the lines, then Allocate again. After picks, reverse via shipment void + stock correction — never erase shipped history.",
        MGR,
    ),
    (
        "I typed the wrong price or discount.",
        "Edit before Confirm. After invoice, void or credit-memo the invoice (Void Invoices permission) and re-bill. Stock does not change when you fix a price.",
        ADMIN,
    ),
    (
        "I created a duplicate sales order.",
        "Cancel the unused twin before allocation. If both allocated, Un-allocate then Cancel the extra. If one already shipped, use Returns — do not delete the shipment.",
        MGR,
    ),
]
SO_TIP = "If Allocate is greyed out, check Credit Hold and on-hand first. Forcing a pick around a hold is how the ledger and the cash desk disagree."

add("/sales-orders", "Sales", "Sales Orders", SO_SUMMARY, SO_PRIV, SO_ACTIONS, SO_MISTAKES, SO_TIP)
add("/sales/orders", "Sales", "Sales Orders", SO_SUMMARY, SO_PRIV, SO_ACTIONS, SO_MISTAKES, SO_TIP)
add("/fulfillment/orders", "Fulfillment", "Sales Orders Fulfillment Queue", SO_SUMMARY, SO_PRIV, SO_ACTIONS, SO_MISTAKES, SO_TIP)

FUL_SUMMARY = (
    "Fulfillment & picking. Group orders into waves, claim a wave to your scanner, pick items, then pack and print labels. "
    "Skip & Flag keeps the wave moving when a label is torn or an item is damaged."
)
FUL_PRIV = "Floor Pickers, Warehouse Managers, Administrators, and Owners. Viewers cannot pick."
FUL_ACTIONS = [
    "Unlock the shift PIN if prompted.",
    "Claim the wave (device lock) so two people cannot pick the same lines.",
    "Scan the directed location and SKU.",
    "At pack, cartonize, capture scale weight, and print the carrier label.",
]
FUL_MISTAKES = [
    (
        "I picked the wrong SKU into a tote.",
        "Do not finish pack. Flag a tote-swap exception or ask a manager to reverse the pick with a stock correction and put the unit back. Then pick the correct SKU.",
        MGR,
    ),
    (
        "I clicked Shipped before the freight was in the truck.",
        "Tell a manager immediately. They void or reverse the ship event and you reprint when the truck is actually loaded. The early ship stays in history.",
        MGR,
    ),
    (
        "I dropped or broke an item.",
        "Skip & Flag, quarantine the unit, and pick a replacement. A manager posts the loss. Do not complete pack on damaged goods.",
        PICKER,
    ),
]
FUL_TIP = "Mis-scan? Use the short undo window before the scan is saved offline. After that, only a manager can post an offset entry."

add("/fulfillment", "Fulfillment", "Fulfillment & Picking", FUL_SUMMARY, FUL_PRIV, FUL_ACTIONS, FUL_MISTAKES, FUL_TIP)

CLUS_SUMMARY = "Multi-tote cluster picking — pick several orders in one walk, each into its own tote."
CLUS_PRIV = "Floor Pickers and Warehouse Managers (Advanced Fulfillment module)."
CLUS_ACTIONS = [
    "Claim the cluster and confirm tote count.",
    "Scan location, SKU, then the destination tote.",
    "Stage completed totes for pack.",
]
CLUS_MISTAKES = [
    (
        "I put the wrong SKU into a tote.",
        "Raise a tote-swap exception. Do not silently swap units between totes without scanning — the ledger still thinks the first tote is correct.",
        MGR,
    )
]
CLUS_TIP = "If you lose track of which tote is which, stop and ask a manager to rebuild the cluster rather than guessing."

add("/cluster-pick", "Fulfillment", "Cluster Pick", CLUS_SUMMARY, CLUS_PRIV, CLUS_ACTIONS, CLUS_MISTAKES, CLUS_TIP)
add("/fulfillment/cluster", "Fulfillment", "Cluster Pick", CLUS_SUMMARY, CLUS_PRIV, CLUS_ACTIONS, CLUS_MISTAKES, CLUS_TIP)

WAVE_SUMMARY = "Picking wave orchestration — generate, optimize path, release, and unstick waves."
WAVE_PRIV = "Warehouse Managers release waves. Pickers claim released work."
WAVE_ACTIONS = [
    "Generate a wave from ALLOCATED orders.",
    "Optimize the walk path if offered.",
    "Release to floor devices.",
]
WAVE_MISTAKES = [
    (
        "The wave is stuck and nobody can claim it.",
        "A manager can force-release or rebuild the wave after clearing Exceptions. Do not invent picks on unreleased lines.",
        MGR,
    )
]
WAVE_TIP = "A stuck wave is usually an open exception or a device lock — not missing stock."

add("/fulfillment/waves", "Fulfillment", "Picking Waves", WAVE_SUMMARY, WAVE_PRIV, WAVE_ACTIONS, WAVE_MISTAKES, WAVE_TIP)

SHIP_SUMMARY = "Packing, cartonization, and carrier labels. Wrong box dimensions produce wrong rates and crushed freight."
SHIP_PRIV = "Floor Pickers pack. Managers void a premature ship."
SHIP_ACTIONS = [
    "Confirm cartonization suggestions.",
    "Capture scale weight.",
    "Print the carrier label only when the box is closed and on the dock.",
]
SHIP_MISTAKES = [
    (
        "I entered the wrong box dimensions.",
        "Recalculate the rate before you click Shipped. After ship, a manager voids the label and you reprint — do not toss the first label without voiding.",
        MGR,
    )
]
SHIP_TIP = "If the scale will not connect, retry Connect packing scale. Manual weight is a last resort — read it twice."

add("/fulfillment/shipments", "Fulfillment", "Pack & Ship", SHIP_SUMMARY, SHIP_PRIV, SHIP_ACTIONS, SHIP_MISTAKES, SHIP_TIP)

DOCK_SUMMARY = "Dock door scheduling — appointments for inbound and outbound carriers."
DOCK_PRIV = "Warehouse Managers and Administrators book doors. Floor staff check the calendar."
DOCK_ACTIONS = [
    "Create or confirm an appointment window.",
    "Assign a door and a carrier.",
    "Mark arrived / completed when the truck is actually there.",
]
DOCK_MISTAKES = [
    (
        "The carrier missed the appointment window.",
        "Reschedule the appointment. Do not receive or ship against the old slot. If freight was already scanned, keep those ledger rows and just fix the calendar.",
        MGR,
    )
]
DOCK_TIP = "A calendar lie (marking arrived early) is how two trucks get assigned the same door."

add("/fulfillment/dock", "Fulfillment", "Dock Schedule", DOCK_SUMMARY, DOCK_PRIV, DOCK_ACTIONS, DOCK_MISTAKES, DOCK_TIP)

PAL_SUMMARY = "License Plate Number (LPN) building and pallet manifests. One plate moves every carton on the pallet."
PAL_PRIV = "Floor Pickers and Warehouse Managers mint and move LPNs (Advanced Fulfillment)."
PAL_ACTIONS = [
    "Mint New LPN and stack cartons onto the plate.",
    "Use LPN Move: scan the plate, then the destination.",
    "Print the pallet manifest for the carrier.",
]
PAL_MISTAKES = [
    (
        "I lost the physical pallet tag.",
        "Ask a manager to reprint the LPN or decompose the LPN back to cartons. Do not borrow another pallet's plate.",
        MGR,
    ),
    (
        "I moved the pallet but forgot to scan.",
        "Do the LPN Move now. The next picker will otherwise walk to an empty location.",
        PICKER,
    ),
]
PAL_TIP = "A missing plate is shrink until a manager writes it off — search first, then cycle-count the last location."

add("/pallet-manifests", "Inventory", "Pallet Manifests / LPN", PAL_SUMMARY, PAL_PRIV, PAL_ACTIONS, PAL_MISTAKES, PAL_TIP)
add("/inventory/lpn", "Inventory", "License Plate Numbers", PAL_SUMMARY, PAL_PRIV, PAL_ACTIONS, PAL_MISTAKES, PAL_TIP)

# --- Inventory ---
LEDGER_SUMMARY = (
    "Double-entry inventory ledger history. Every receive, pick, count, and correction is an append-only row. "
    "weGrowStock never deletes the past — you post an offset entry (Reverse transaction) so the math becomes correct and the mistake stays visible."
)
LEDGER_PRIV = "Everyone can read. Reversing a movement requires a Warehouse Manager (or above) with Adjust Inventory."
LEDGER_ACTIONS = [
    "Open a product peek → Ledger History, or this dedicated ledger view.",
    "Filter by SKU, location, date, or movement type.",
    "On a reversible row, click Reverse transaction → Confirm Reversal.",
]
LEDGER_MISTAKES = [
    (
        "I want to delete a bad row.",
        "You cannot, by design. Click Reverse transaction to post an equal-and-opposite entry attributed to you. If Reverse is greyed out, run a cycle count or a manager stock correction instead.",
        MGR,
    ),
    (
        "I reversed the wrong movement.",
        "Reverse the reversal (or post another correction). Two honest offsets are better than hiding history.",
        MGR,
    ),
]
LEDGER_TIP = "If someone asks 'why does the shelf disagree with the computer?', the answer is always in this diary."

add("/inventory/ledger", "Inventory", "Inventory Ledger", LEDGER_SUMMARY, LEDGER_PRIV, LEDGER_ACTIONS, LEDGER_MISTAKES, LEDGER_TIP)

PROD_SUMMARY = (
    "Product catalog — SKUs, barcodes, UoM, imagery, on-hand / allocated / available-to-promise. "
    "Catalog edits do not change stock quantities."
)
PROD_PRIV = "Owners, Administrators, and Warehouse Managers maintain the catalog. Viewers read. Pickers rarely need this desktop page."
PROD_ACTIONS = [
    "Search the catalog (server-side, debounced).",
    "Click Add product or Import for a new SKU.",
    "Open Ledger History on a product to see movements.",
]
PROD_MISTAKES = [
    (
        "I misspelled the product name.",
        "Edit and Save. Name typos do not need a ledger reversal.",
        ADMIN,
    ),
    (
        "I created a duplicate SKU.",
        "Retire the twin and point future work at the correct SKU. Movement history on both remains.",
        ADMIN,
    ),
]
PROD_TIP = "Never type on-hand as a free-text field. Quantity truth only enters through receive, count, pick, and corrections."

add("/products", "Inventory", "Product Catalog", PROD_SUMMARY, PROD_PRIV, PROD_ACTIONS, PROD_MISTAKES, PROD_TIP)

CC_SUMMARY = (
    "Blind cycle counting. The scanner hides the expected quantity so you count what your eyes see. "
    "Small variances may auto-approve; large fat-fingered counts (1000 instead of 10) park as PENDING MANAGER REVIEW."
)
CC_PRIV = "Floor Pickers perform counts. Only a Warehouse Manager can Approve Ledger Adjustment or Request Recount."
CC_ACTIONS = [
    "Scan the bin, then the product.",
    "Type the physical count and confirm.",
    "Managers approve or request a recount on large variances.",
]
CC_MISTAKES = [
    (
        "I fat-fingered the count (typed 1000 instead of 10).",
        "Do not panic — massive variances do not silently change stock. Tell your manager it was a typo. They click Request Recount. You count again. The typo stays in the log.",
        MGR,
    ),
    (
        "I counted the wrong bin.",
        "Tell the manager before approval. If already approved, they post a correction after a recount of the right bin.",
        MGR,
    ),
]
CC_TIP = "A wrong honest count is fixable. A fake count that matches 'what the system usually says' poisons every order that trusts it."

add("/cycle-counts", "Inventory", "Cycle Counts", CC_SUMMARY, CC_PRIV, CC_ACTIONS, CC_MISTAKES, CC_TIP)
add("/inventory/cycle-counts", "Inventory", "Cycle Counts", CC_SUMMARY, CC_PRIV, CC_ACTIONS, CC_MISTAKES, CC_TIP)

REPL_SUMMARY = "Min-max and wave replenishment — move stock from reserve/bulk into pick faces so waves do not starve."
REPL_PRIV = "Floor Pickers and Warehouse Managers."
REPL_ACTIONS = [
    "Take a replenishment task.",
    "Scan the from (reserve) bin, move the stock, scan the to (pick face) bin.",
    "Return to Fulfillment and keep picking.",
]
REPL_MISTAKES = [
    (
        "The pick face overflowed / I put too much in the bin.",
        "Ask a manager to reassign the extra to bulk overstock with a documented TRANSFER. Do not delete the original replenishment row.",
        MGR,
    ),
    (
        "I moved stock to the wrong bin.",
        "Ask for a corrective TRANSFER. Two honest moves are fine.",
        MGR,
    ),
]
REPL_TIP = "Empty pick face and no tasks usually means reserve is empty too — that is a PO problem, not a workaround."

add("/replenishments", "Inventory", "Replenishments", REPL_SUMMARY, REPL_PRIV, REPL_ACTIONS, REPL_MISTAKES, REPL_TIP)
add("/inventory/replenishments", "Inventory", "Replenishments", REPL_SUMMARY, REPL_PRIV, REPL_ACTIONS, REPL_MISTAKES, REPL_TIP)

EXC_SUMMARY = (
    "Exceptions desk — Fulfillment Holds (Skip & Flag, damaged goods) and Sync Conflicts (offline parked scans). "
    "An exception means weGrowStock refused to guess, not that you broke the system."
)
EXC_PRIV = "Warehouse Managers decide Approve & Re-process vs Discard Transaction. Pickers can read their parked scans."
EXC_ACTIONS = [
    "Read the card: who, bin, quantity, scan type.",
    "Walk to the physical bin before you click.",
    "Approve & Re-process if the action really happened; Discard Transaction if it did not.",
]
EXC_MISTAKES = [
    (
        "Two workers scanned the same pallet while offline.",
        "The second replay parks here. Look at the shelf, then Approve the real move and Discard the duplicate. If unsure, cycle-count the bin first.",
        MGR,
    ),
    (
        "I approved something that did not happen.",
        "Cycle-count the bin. The variance approval writes the offset entry. Nothing is lost.",
        MGR,
    ),
]
EXC_TIP = "Ninety percent of conflict resolution is looking at the shelf."

add("/exceptions", "Inventory", "Exceptions Desk", EXC_SUMMARY, EXC_PRIV, EXC_ACTIONS, EXC_MISTAKES, EXC_TIP)
add("/inventory/quarantine", "Inventory", "Quarantine / QC Hold", EXC_SUMMARY, EXC_PRIV, EXC_ACTIONS, EXC_MISTAKES, EXC_TIP)

LOT_SUMMARY = "Lot / serial trace for recalls. Follow a batch from supplier receive through assembly to the customer."
LOT_PRIV = "Warehouse Managers, Administrators, Owners, and Viewers (read-only). Pickers report expired lots."
LOT_ACTIONS = [
    "Enter the lot number and click Trace.",
    "Review on-hand bins and affected customers.",
    "Export affected customers when outreach is required.",
]
LOT_MISTAKES = [
    (
        "I found expired lots on the shelf.",
        "Do not pick them. Quarantine, run Lot Trace, then a manager posts the disposal as an attributed correction. FEFO exists to prevent this — reporting one is doing your job.",
        MGR,
    ),
    (
        "A lot failed inspection.",
        "Keep it in Quarantine. Release to salvage/scrap only after a manager dispositions it. Do not quietly return it to a pick face.",
        MGR,
    ),
]
LOT_TIP = "Trace is investigative — it never edits history."

add("/compliance/lot-trace", "Inventory", "Lot Trace", LOT_SUMMARY, LOT_PRIV, LOT_ACTIONS, LOT_MISTAKES, LOT_TIP)

SPATIAL_SUMMARY = "Digital twin warehouse map — live picker positions, congestion heat, and walkable edges for wayfinding."
SPATIAL_PRIV = "Warehouse Managers and Administrators (RTLS module). Coordinate edits do not change stock."
SPATIAL_ACTIONS = [
    "Open the map and watch live positions.",
    "Inspect the heatmap of recent movement.",
    "Mark a blocked aisle as an unwalkable edge when a pallet is down.",
]
SPATIAL_MISTAKES = [
    (
        "I marked the wrong aisle unwalkable.",
        "Edit the edge again. Map edits are layout, not inventory. Stock still sits where the last scan said it sits.",
        MGR,
    )
]
SPATIAL_TIP = "Sample telemetry is for training — it is not a stock correction."

add("/rtls", "Platform", "RTLS Map", SPATIAL_SUMMARY, SPATIAL_PRIV, SPATIAL_ACTIONS, SPATIAL_MISTAKES, SPATIAL_TIP)
add("/inventory/spatial", "Inventory", "Spatial Map", SPATIAL_SUMMARY, SPATIAL_PRIV, SPATIAL_ACTIONS, SPATIAL_MISTAKES, SPATIAL_TIP)

# --- Manufacturing & Field ---
BOM_SUMMARY = "Multi-level Bills of Materials — the recipe that says which components, and how many, make one finished good."
BOM_PRIV = "Warehouse Managers, Administrators, and Owners (Manufacturing module). Viewers read. Operators do not edit recipes."
BOM_ACTIONS = [
    "Create a BOM for the finished SKU.",
    "Add component lines with quantity per one finished unit — not per batch.",
    "Save before anyone starts a production order.",
]
BOM_MISTAKES = [
    (
        "I entered a circular assembly or the wrong component ratio.",
        "If nothing is built yet, edit the BOM. After builds completed, Disassemble the kits, fix the recipe, then rebuild on a new order. Do not delete completed production history.",
        MGR,
    )
]
BOM_TIP = "A classic beginner error is entering totals for the whole batch instead of the quantity per one finished unit."

add("/manufacturing/boms", "Manufacturing", "Bills of Materials", BOM_SUMMARY, BOM_PRIV, BOM_ACTIONS, BOM_MISTAKES, BOM_TIP)

MO_SUMMARY = "Assembly and disassembly work orders. Statuses: DRAFT, COMPONENTS ALLOCATED, WIP, COMPLETED, CANCELLED."
MO_PRIV = "Warehouse Managers, Administrators, and Owners create orders. Operators run them on the terminal."
MO_ACTIONS = [
    "Click Create order, pick the BOM and quantity.",
    "Allocate components, then hand the order to the Manufacturing terminal.",
    "Use Disassemble to split finished goods back into components.",
]
MO_MISTAKES = [
    (
        "I logged too much scrap.",
        "A manager posts a scrap ledger reversal (offset entry) and, if the parts are still good, receives them back. Do not edit the completed order's history.",
        MGR,
    ),
    (
        "I built kits with the wrong BOM.",
        "Fix the BOM, click Disassemble, put components away, then create a new production order.",
        MGR,
    ),
]
MO_TIP = "Cancel while still DRAFT — components release automatically. After COMPLETED, only Disassemble or a correction undoes the trade."

add("/manufacturing/orders", "Manufacturing", "Production Orders", MO_SUMMARY, MO_PRIV, MO_ACTIONS, MO_MISTAKES, MO_TIP)

TERM_SUMMARY = (
    "Manufacturing terminal — floor punch clock for the build timesheet plus Complete build. "
    "The header Clock in / Clock out is your shift; Start/Stop timesheet is this order."
)
TERM_PRIV = "Operators (Picker-type roles) run the terminal. Managers correct labor and completions."
TERM_ACTIONS = [
    "Select the production order.",
    "Start timesheet, scan components, Stop timesheet for breaks.",
    "Click Complete build only when units are actually finished.",
]
TERM_MISTAKES = [
    (
        "I forgot to clock out / typed the wrong hours.",
        "You cannot edit your own past time. Tell a supervisor the real times the same day. They post a manual adjustment next to the original entry.",
        MGR,
    ),
    (
        "I clicked Complete build too early.",
        "Tell a manager immediately. They reverse with an attributed correction or Disassemble if goods were only partially real.",
        MGR,
    ),
]
TERM_TIP = "Never 'balance' a forgotten clock-out by clocking weird hours tomorrow — two wrong entries are harder to fix than one."

add("/manufacturing/terminal", "Manufacturing", "Manufacturing Terminal", TERM_SUMMARY, TERM_PRIV, TERM_ACTIONS, TERM_MISTAKES, TERM_TIP)

TRUCK_SUMMARY = "Technician van stock — Assign to me, Transfer to van, Consume from van."
TRUCK_PRIV = "Field technicians and Warehouse Managers."
TRUCK_ACTIONS = [
    "Click Assign to me to claim the truck.",
    "Transfer to van with scans when loading.",
    "Consume from van on site for each part used.",
]
TRUCK_MISTAKES = [
    (
        "I replenished or consumed against an unassigned truck.",
        "Assign the truck to yourself first. If parts already moved, a manager posts a reverse transfer. Do not share another tech's session.",
        MGR,
    )
]
TRUCK_TIP = "Offline field scans park in Exceptions → Sync Conflicts when you reconnect."

add("/field/truck", "Field", "Technician Truck", TRUCK_SUMMARY, TRUCK_PRIV, TRUCK_ACTIONS, TRUCK_MISTAKES, TRUCK_TIP)

SUPPLY_SUMMARY = "Internal supplies checkout against a cost center — not a customer shipment."
SUPPLY_PRIV = "Warehouse Managers and permitted Pickers. Administrators configure cost centers."
SUPPLY_ACTIONS = [
    "Select the cost center / requisition.",
    "Scan items and submit with Issue Fact.",
]
SUPPLY_MISTAKES = [
    (
        "I checked out supplies to the wrong cost center.",
        "Ask a manager for a return-to-stock correction, then re-issue to the right center. Do not delete the Issue Fact.",
        MGR,
    )
]
SUPPLY_TIP = "No cost centers listed? An Admin must open Settings → Cost Centers & Requisitions first."

add("/issue-supplies", "Field", "Issue Supplies", SUPPLY_SUMMARY, SUPPLY_PRIV, SUPPLY_ACTIONS, SUPPLY_MISTAKES, SUPPLY_TIP)
add("/field/supplies", "Field", "Issue Supplies", SUPPLY_SUMMARY, SUPPLY_PRIV, SUPPLY_ACTIONS, SUPPLY_MISTAKES, SUPPLY_TIP)

# --- Sales / B2B ---
INV_SUMMARY = (
    "Commercial invoicing and factoring. Posted invoices are ledger documents — void or credit-memo, never delete. "
    "Statuses include DRAFT, OPEN, PAID, VOID."
)
INV_PRIV = "Owners and Administrators invoice. Voiding requires the Void Invoices permission."
INV_ACTIONS = [
    "Open a shipped sales order and click Invoice or Invoice remaining.",
    "Check customer, line prices, and total out loud.",
    "Watch Dashboard Open AR and the buyer Showroom Billing tab.",
]
INV_MISTAKES = [
    (
        "I issued an invoice with the wrong price or created a duplicate.",
        "Void the bad invoice (or issue a credit memo) and re-invoice correctly. If a payment landed on the duplicate, finance applies it to the correct document. This is a reversing finance entry — not a stock edit.",
        ADMIN,
    ),
    (
        "I need to reverse a refunded invoice.",
        "Issue the credit note / void (money), receive goods through the RMA flow (stock), and refund through payment rails. Three signed entries, zero deletions.",
        ADMIN,
    ),
]
INV_TIP = "Money fixes and stock fixes are separate entries. Do both; delete neither."

add("/invoices", "Sales", "Invoices", INV_SUMMARY, INV_PRIV, INV_ACTIONS, INV_MISTAKES, INV_TIP)
add("/sales/invoices", "Sales", "Invoices", INV_SUMMARY, INV_PRIV, INV_ACTIONS, INV_MISTAKES, INV_TIP)

CUS_SUMMARY = "Customer accounts, ship-to addresses, and credit lines that gate Allocate and Showroom checkout."
CUS_PRIV = "Owners, Administrators, and Warehouse Managers (commercial policy varies)."
CUS_ACTIONS = [
    "Create or edit the customer profile and ship-to.",
    "Set the credit limit.",
    "Review Wholesale Applications on this page.",
]
CUS_MISTAKES = [
    (
        "The customer exceeded their credit limit.",
        "Do not force Allocate. Request a credit override from an Owner (raise the limit visibly) or wait for payment. Working around a hold is how AR and the warehouse disagree.",
        OWNER,
    ),
    (
        "I misspelled the customer name or ship-to address.",
        "Edit before the first shipment. After ship, fix the master record and arrange a carrier intercept or a return — do not silently rewrite the shipped order.",
        ADMIN,
    ),
]
CUS_TIP = "Search before adding. Duplicate customers split credit exposure and invoice history."

add("/customers", "Sales", "Customers", CUS_SUMMARY, CUS_PRIV, CUS_ACTIONS, CUS_MISTAKES, CUS_TIP)
add("/sales/customers", "Sales", "Customers", CUS_SUMMARY, CUS_PRIV, CUS_ACTIONS, CUS_MISTAKES, CUS_TIP)

WS_SUMMARY = "Wholesale B2B application approvals submitted from the Showroom."
WS_PRIV = "Owners and Administrators."
WS_ACTIONS = [
    "Open the Wholesale Applications panel on Customers.",
    "Review the business name, tax ID, and requested terms.",
    "Approve to create the customer, or reject per policy.",
]
WS_MISTAKES = [
    (
        "I rejected an application by accident.",
        "The business can re-apply, or an Admin creates the customer manually. There is no secret 'undelete' — approval is a new action.",
        ADMIN,
    ),
    (
        "I approved with the wrong credit terms.",
        "Edit the customer record. Already-posted invoices keep the terms they were issued under.",
        ADMIN,
    ),
]
WS_TIP = "Approving is a credit decision. Treat it like opening a tab at your warehouse."

add("/sales/wholesale", "Sales", "Wholesale Applications", WS_SUMMARY, WS_PRIV, WS_ACTIONS, WS_MISTAKES, WS_TIP)

SHOW_SUMMARY = (
    "B2B digital showroom catalog. Buyers see sellable items and negotiated prices — never warehouse bin maps. "
    "If a customer sees a restricted price tier, an Admin maps the correct tier on the customer record."
)
SHOW_PRIV = "B2B Customers shop here. Seller staff support commercially but do not pick from this screen."
SHOW_ACTIONS = [
    "Browse Catalog and add quantities to the cart.",
    "Review Checkout (ship-to and terms) before Place order.",
    "Track status under Orders; start returns with Return Items.",
]
SHOW_MISTAKES = [
    (
        "The customer sees a restricted or wrong price tier.",
        "An Administrator assigns the correct price list on the customer record. Do not ask pickers for a bin workaround or a silent discount on the warehouse floor.",
        ADMIN,
    ),
    (
        "The buyer placed the wrong order.",
        "Cancel before the seller allocates/ships, or use Return Items after ship. Warehouse history is not rewritten for buyer regret.",
        ADMIN,
    ),
]
SHOW_TIP = "Checkout blocked by credit is automatic, not personal — pay down Billing or ask the seller Owner for a visible limit change."

add("/showroom", "Showroom", "B2B Showroom", SHOW_SUMMARY, SHOW_PRIV, SHOW_ACTIONS, SHOW_MISTAKES, SHOW_TIP)
add("/showroom/catalog", "Showroom", "Showroom Catalog", SHOW_SUMMARY, SHOW_PRIV, SHOW_ACTIONS, SHOW_MISTAKES, SHOW_TIP)
add(
    "/showroom/orders",
    "Showroom",
    "Showroom Orders",
    "Track wholesale order progress and start returns with Return Items → Submit return.",
    "B2B Customers only.",
    ["Open Orders", "Click Return Items when goods must come back", "Use Browse catalog to reorder"],
    [
        (
            "Return Items is missing.",
            "The order may still be shipping. Wait or call your rep. Unauthorized freight without an approved RMA will be refused.",
            ANY,
        )
    ],
    "Denied returns (Deny & Close) mean keep or dispose per your agreement — do not ship unauthorized freight.",
)
add(
    "/showroom/checkout",
    "Showroom",
    "Showroom Checkout",
    "Submit a wholesale order against catalog availability and account terms.",
    "B2B Customers.",
    ["Review quantities and ship-to", "Click Place order"],
    [
        (
            "Place order is disabled.",
            "Cart empty, item unavailable, or Credit Hold. Check Billing or contact your rep.",
            ANY,
        )
    ],
    "Edit the cart freely until Place order. After that, cancellations go through the seller office.",
)
add(
    "/showroom/billing",
    "Showroom",
    "Showroom Billing",
    "Open invoices and balances that decide whether new checkout is allowed.",
    "B2B Customers see their own billing only.",
    ["Review open invoices", "Coordinate payment so Credit Hold clears"],
    [
        (
            "I dispute a charge.",
            "Ask your rep for a credit memo. Do not ask the warehouse to erase a shipment.",
            ADMIN,
        )
    ],
    "Paying down Billing is the fastest way to unlock Allocate and Place order.",
)

# --- Settings ---
SET_SUMMARY = (
    "Organization settings overview and system health. Tabs cover profile, users, warehouses, inventory rules, "
    "documents, Retail POS, security, reconciliation, accounting, integrations, mesh, operations, automations, "
    "sync conflicts, and cost centers."
)
SET_PRIV = "Owners and Administrators. Warehouse Managers may see Operations/Sync Conflicts depending on policy. Pickers cannot open Organization settings."
SET_ACTIONS = [
    "Open the tab that matches the change you need.",
    "Save — the audit log records who changed what.",
    "Confirm floor devices pick up the new rule on the next action.",
]
SET_MISTAKES = [
    (
        "I toggled the wrong floor rule.",
        "Toggle it back. Every change is audited. Raising an adjustment limit does not auto-approve old pending counts.",
        ADMIN,
    )
]
SET_TIP = "Billing and Cash Flow & Financing are Owner-only hubs outside these tabs."

add("/settings", "Settings", "Tenant Settings", SET_SUMMARY, SET_PRIV, SET_ACTIONS, SET_MISTAKES, SET_TIP)
add("/organization", "Settings", "Organization", SET_SUMMARY, SET_PRIV, SET_ACTIONS, SET_MISTAKES, SET_TIP)

settings_tabs = [
    (
        "profile",
        "Settings — Profile",
        "Your user profile and the default organization name shown across weGrowStock.",
        "Every signed-in user edits their own profile. Role changes live on the Users tab.",
        ["Update display name and locale", "Save — changes apply immediately"],
        [("I changed the wrong language.", "Change it back and save. Profile edits never touch inventory.", ANY)],
        "Org legal name on documents is configured under the Documents tab.",
    ),
    (
        "users",
        "Settings — Users",
        "Invite company users and assign roles (Owner, Admin, Warehouse Manager, Picker, Viewer, B2B Customer) plus warehouse access. OWNER cannot be casually demoted.",
        "Owners and Administrators. Warehouse Managers do not invite Owners.",
        ["Invite a user", "Assign roles and warehouse checkboxes", "Save — next login enforces capabilities"],
        [
            (
                "I locked a user out of every screen.",
                "An Administrator (or Super Admin via control plane) restores a role on this tab. Deactivate rather than delete users tied to ledger history.",
                ADMIN,
            )
        ],
        "Pickers only see assigned buildings. That is why a new hire's Fulfillment queue can look empty.",
    ),
    (
        "warehouses",
        "Settings — Warehouses",
        "Facility management — buildings, zones, bins, and aisle setup used by putaway, RTLS, and pick pathing.",
        "Owners and Administrators.",
        ["Add or edit a warehouse", "Maintain zones/bins in the visualizer", "Assign users on the Users tab"],
        [
            (
                "I deleted or renamed a warehouse that still has stock.",
                "Deactivate instead of deleting buildings with ledger history. Move stock with transfers — renaming does not move inventory.",
                ADMIN,
            )
        ],
        "Wrong active warehouse in the header is the usual reason screens look empty after login.",
    ),
    (
        "inventory",
        "Settings — Inventory Rules",
        "Reorder points, UoM defaults, and policy knobs that feed ATP and replenishment.",
        "Owners and Administrators.",
        ["Adjust reorder / safety-stock defaults", "Save — Low Stock KPIs pick up the new thresholds"],
        [
            (
                "I set reorder points so low that MRP over-ordered.",
                "Restore prior thresholds and save again. Cancel unreceived POs created from the bad run.",
                ADMIN,
            )
        ],
        "Dashboard Low Stock Count uses these thresholds.",
    ),
    (
        "documents",
        "Settings — Documents",
        "Templates and numbering for POs, packing slips, invoices, and other printable documents.",
        "Owners and Administrators (Documents module).",
        ["Pick a document type", "Update logo, footer, or number series", "Save"],
        [
            (
                "I saved a typo on the invoice footer.",
                "Edit and save again. Already-printed PDFs are not rewritten.",
                ADMIN,
            )
        ],
        "Ship and PO submit flows render from these templates.",
    ),
    (
        "retailPos",
        "Settings — Retail POS",
        "Point of Sale registers — receipt branding, USD/MXN, Mexican CFDI, and blind closeout. Cashiers count cash without seeing the expected drawer total when blind closeout is on.",
        "Owners and Administrators, and only when the tenant includes Retail POS.",
        ["Set currency and CFDI", "Edit receipt header/footer", "Toggle Require Blind Closeout at Shift End", "Save POS settings"],
        [
            (
                "The register cash variance at end of shift does not match.",
                "Do not edit historical WMS invoices. Recount the drawer. A manager posts the POS variance per store policy. Blind closeout exists so cashiers cannot aim at the expected total.",
                MGR,
            )
        ],
        "Unsupported currencies other than USD/MXN are rejected. Warehouse managers cannot change these settings from the register.",
    ),
    (
        "security",
        "Settings — Security & SSO",
        "Tenant security, password / SSO rules, MFA, and Desktop Idle Timeout. Idle lock protects shared office PCs.",
        "Owners and Administrators.",
        ["Configure SSO if your IdP is ready", "Review session and Desktop Idle Timeout", "Save"],
        [
            (
                "I enabled SSO and locked everyone out.",
                "Use the break-glass Owner local login (or Super Admin) to disable SSO, then fix the IdP mapping. Every security change is audited.",
                OWNER,
            )
        ],
        "Disable SSO carefully — confirm local login still works for an Owner before cutover.",
    ),
    (
        "reconciliation",
        "Settings — Reconciliation",
        "Compare weGrowStock levels to connected storefronts and books. Jobs report drift; they do not delete ledger rows.",
        "Owners and Administrators.",
        ["Review the last run", "Trigger a reconcile when finance asks", "Investigate mismatches on operational pages"],
        [
            (
                "Numbers disagree with QuickBooks.",
                "Fix the operational document (void invoice, reverse receive) then re-sync. Do not force the warehouse number to match dollars.",
                ADMIN,
            )
        ],
        "Pair with Accounting Sync and Cycle Counts when the three-way match fails.",
    ),
    (
        "accounting",
        "Settings — Accounting Sync",
        "Connect QuickBooks/Xero so invoices and journals flow through finance sync.",
        "Owners and Administrators (Accounting module).",
        ["Connect or refresh the adapter", "Map tax schemes", "Retry FAILED rows"],
        [
            (
                "A journal posted twice.",
                "Void the duplicate in the accounting system. Disconnecting weGrowStock stops new syncs; it does not erase external journals.",
                ADMIN,
            )
        ],
        "Paid invoices and COGS depend on this bridge.",
    ),
    (
        "integrations",
        "Settings — Integrations",
        "Integration Hub for e-commerce storefronts (Shopify and similar) and accounting connections so orders and payments land without double entry.",
        "Owners and Administrators.",
        ["Choose a connector", "Paste connection keys", "Enable and verify a test order"],
        [
            (
                "Webhook sync failed and orders stopped landing.",
                "Open the connector, replay the outbox / retry the failed delivery, then confirm secrets. Already-imported sales orders stay in the outbound pipeline.",
                ADMIN,
            )
        ],
        "Storefront orders still allocate and ship like office-entered orders.",
    ),
    (
        "mesh",
        "Settings — Partner Catalog",
        "Cross-tenant mesh SKU mappings so seller items resolve to buyer products on multi-party POs/SOs.",
        "Owners and Administrators (Mesh Network module).",
        ["Open Partner Catalog Mapping", "Map partner SKUs to local variants", "Save"],
        [
            (
                "I mapped the wrong SKU.",
                "Remap. Historical documents keep the snapshot they were created with — fix the next PO, do not rewrite the last one.",
                ADMIN,
            )
        ],
        "Unmapped mesh lines may create DRAFT exception sales orders for review.",
    ),
    (
        "operations",
        "Settings — Operations",
        "Floor rules — blind receiving, adjustment limits, scanner options — plus the Audit Log of who changed what.",
        "Owners and Administrators. Managers follow the limits; they do not always edit them.",
        ["Toggle blind receiving or max adjust qty", "Save", "Use Audit Log / Activity Timeline to see who changed a limit"],
        [
            (
                "I raised adjustment limits and old counts auto-approved.",
                "They do not. Pending manager review counts still need a human. Toggle the rule back if the change was a mistake — the audit log keeps both saves.",
                ADMIN,
            )
        ],
        "Blind receiving and variance thresholds change what pickers may post without manager review.",
    ),
    (
        "automations",
        "Settings — Automations",
        "Business rule triggers (reorder emails, status hops, notifications). A rogue rule can spam the floor.",
        "Owners and Administrators.",
        ["Review enabled rules", "Disable the toggle on any rule that misfires", "Save"],
        [
            (
                "A rogue automation rule created duplicate POs or emails.",
                "Disable the toggle immediately. Cancel the extra documents. Do not leave a half-tested rule LIVE overnight.",
                ADMIN,
            )
        ],
        "Automations never erase ledger rows — they only create new work. Turn the toggle off first, then clean up.",
    ),
    (
        "syncConflicts",
        "Settings — Sync Conflicts",
        "The same parked-offline-scan queue as Inventory → Exceptions → Sync Conflicts.",
        "Warehouse Managers and Administrators.",
        ["Open a PARKED conflict", "Approve & Re-process or Discard Transaction"],
        [
            (
                "I discarded a scan that really happened.",
                "Cycle-count the bin so the offset entry restores truth.",
                MGR,
            )
        ],
        "Pickers keep working while managers clear this quarantine.",
    ),
    (
        "costCenters",
        "Settings — Cost Centers & Requisitions",
        "Internal budgets that authorize Issue Supplies without a customer sales order.",
        "Administrators configure. Managers approve requisitions.",
        ["Create a cost center", "Approve DRAFT requisitions", "Floor Issue Supplies charges the center"],
        [
            (
                "I approved the wrong job / cost center.",
                "Cancel unused DRAFT requisitions. After issue, reverse stock with a manager correction referencing the original consumption.",
                MGR,
            )
        ],
        "Issue Supplies on the floor reads these centers for budget clearance.",
    ),
]

for tab, title, summary, priv, actions, mistakes, tip in settings_tabs:
    add(f"/settings?tab={tab}", "Settings", title, summary, priv, actions, mistakes, tip)

# Dedicated settings subroutes requested in the epic
add(
    "/settings/security",
    "Settings",
    "Tenant Security",
    "Dedicated security hub — password rules, SSO, and Desktop Idle Timeout for shared office PCs.",
    "Owners and Administrators.",
    ["Review idle timeout", "Confirm SSO and MFA policy", "Save"],
    [
        (
            "Desktop idle lock is too aggressive and people share PINs.",
            "Raise the timeout on this page. Never share passwords — disable a user instead. Sharing a session breaks attribution when something needs a reversal.",
            ADMIN,
        )
    ],
    "Idle lock protects the ledger: the next person must sign in as themselves.",
)
add(
    "/settings/integrations",
    "Settings",
    "Integrations Hub",
    "Hub that routes into e-commerce, accounting, and operations integration surfaces.",
    "Owners and Administrators.",
    ["Pick the connector category", "Follow the shortcut into the matching Settings tab"],
    [
        (
            "Webhook sync failure.",
            "Retry / outbox replay on the connector card, then confirm LIVE. Disable a connector to stop inbound events.",
            ADMIN,
        )
    ],
    "The hub itself does not mutate stock.",
)
add(
    "/settings/roles",
    "Settings",
    "Roles & Permissions",
    "Custom RBAC matrix — which role may approve POs, void invoices, override discounts, or adjust inventory.",
    "Owners and Administrators.",
    ["Open the permissions matrix", "Grant or revoke a capability", "Save"],
    [
        (
            "I locked out a user (or myself) from every screen.",
            "A remaining Administrator restores the role here. If every Admin is locked out, Super Admin restores access from the control plane. Do not share Owner passwords as a workaround.",
            OWNER,
        )
    ],
    "Least privilege first. A Picker with Adjust Inventory can silently rewrite the floor.",
)
add(
    "/settings/automations",
    "Settings",
    "Automations",
    "Business rule triggers. Same content as Settings → Automations tab.",
    "Owners and Administrators.",
    ["Review rules", "Disable a rogue toggle"],
    [
        (
            "Rogue automation created duplicate work.",
            "Disable the toggle, then cancel the extra POs/orders. Automations only create work; they never erase history.",
            ADMIN,
        )
    ],
    "Test one rule at a time during a quiet hour.",
)
add(
    "/settings/printers",
    "Settings",
    "Workstation Printers",
    "Thermal Zebra / QZ Tray workstation printers for labels and manifests.",
    "Administrators and Warehouse Managers.",
    ["Add a printer name and IP / QZ Tray target", "Mark the default for this workstation", "Print a test label"],
    [
        (
            "The printer is offline.",
            "Switch to the fallback IP or another workstation printer. Do not handwritten-guess a tracking number. Reprint after the device is LIVE.",
            MGR,
        )
    ],
    "A wrong default printer is how packing labels land in the office.",
)
add(
    "/settings/retail-pos",
    "Settings",
    "Retail POS",
    "Same register configuration as Settings → Retail POS (receipts, CFDI, blind closeout).",
    "Owners and Administrators with the Retail POS addon.",
    ["Set currency", "Edit receipt copy", "Toggle blind closeout", "Save POS settings"],
    [
        (
            "Register cash variance at end of shift.",
            "Recount. A manager posts the variance. Do not rewrite WMS invoices to hide a drawer miss.",
            MGR,
        )
    ],
    "Blind closeout is a register guardrail, separate from warehouse blind cycle counts.",
)
add(
    "/settings/warehouses",
    "Settings",
    "Warehouses",
    "Facility management, zones, bins, and aisle setup — same domain as Settings → Warehouses.",
    "Owners and Administrators.",
    ["Add a warehouse", "Edit bins and aisles", "Assign users"],
    [
        (
            "I set up bins in the wrong building.",
            "Create the bins on the correct warehouse and transfer stock. Do not rename a live warehouse to 'fix' it.",
            ADMIN,
        )
    ],
    "Pick pathing, RTLS, and replenishment all depend on this layout.",
)
add(
    "/settings/profile",
    "Settings",
    "Profile settings",
    "Dedicated profile page for the signed-in user (same domain as Settings → Profile).",
    "Every signed-in user.",
    ["Update personal details", "Save and return"],
    [("I saved the wrong display name.", "Edit again. No inventory impact.", ANY)],
    "Users tab remains the place for role and location access changes.",
)
add(
    "/settings/billing",
    "Settings",
    "Billing",
    "Owner-scoped subscription and plan management for the tenant.",
    "Owners (and sometimes Administrators).",
    ["Review the current plan and seats", "Change plan in the billing portal when needed"],
    [
        (
            "I changed plans by accident.",
            "Confirm in the billing portal — downgrades may wait until period end. Billing changes do not reverse warehouse transactions.",
            OWNER,
        )
    ],
    "Only Owners should open this hub.",
)
add(
    "/settings/fintech",
    "Settings",
    "Cash Flow & Financing",
    "Owner-scoped capital credit lines and financing insights tied to AR/AP. Changes when cash arrives — not what stock exists.",
    "Owners only.",
    ["Review offers and status", "Follow only the on-screen connect/confirm steps"],
    [
        (
            "I started a financing connect flow I did not mean to.",
            "Stop before the final confirm. If you completed something in error, contact the provider from this page. This is contractual, not a ledger edit.",
            OWNER,
        )
    ],
    "If this page is forbidden, you are not the Owner — that is the control working.",
)

# --- Platform ---
MESH_SUMMARY = "Cross-tenant mesh inventory sourcing network — discover partners, handshake, and pull smart-sourcing suggestions."
MESH_PRIV = "Owners and Administrators (Mesh Network module)."
MESH_ACTIONS = [
    "Open Mesh Network from the top-level nav.",
    "Discover or accept a partner handshake.",
    "Map SKUs under Settings → Partner Catalog.",
]
MESH_MISTAKES = [
    (
        "I connected the wrong partner tenant.",
        "Disconnect the handshake. Historical mesh POs stay. Do not ship another company's freight to 'make it right'.",
        OWNER,
    )
]
MESH_TIP = "Buyers never see your bin map through mesh — only catalog availability you chose to share."

add("/mesh-network", "Platform", "Mesh Network", MESH_SUMMARY, MESH_PRIV, MESH_ACTIONS, MESH_MISTAKES, MESH_TIP)
add("/mesh", "Platform", "Mesh Network", MESH_SUMMARY, MESH_PRIV, MESH_ACTIONS, MESH_MISTAKES, MESH_TIP)

REP_SUMMARY = (
    "Executive financial valuation, COGS, turnover, fulfillment, labor, and inventory audit analytics. "
    "Reports are read-only — reverse underlying transactions on operational pages."
)
REP_PRIV = "Owners and Administrators. Viewers as permitted."
REP_ACTIONS = [
    "Open the analysis board you need.",
    "Filter by date / warehouse.",
    "Export or screenshot for leadership packs.",
]
REP_MISTAKES = [
    (
        "Numbers look stale.",
        "Finish pending Approve Ledger Adjustment and Sync Conflict decisions first. Reports never rewrite counts.",
        MGR,
    )
]
REP_TIP = "Headline KPIs may refresh on a short delay rather than updating every second."

add("/reports", "Platform", "Reports", REP_SUMMARY, REP_PRIV, REP_ACTIONS, REP_MISTAKES, REP_TIP)

IMP_SUMMARY = "Bulk-load products via mapped CSV/Excel with preflight validation (READY TO IMPORT, MISSING PRODUCT, VALIDATION ERROR)."
IMP_PRIV = "Administrators and Owners."
IMP_ACTIONS = [
    "Download the template.",
    "Map columns and run preflight.",
    "Click Import N ready row(s) only when rows are green.",
]
IMP_MISTAKES = [
    (
        "I imported duplicates or typo'd names.",
        "Edit the product or retire the twin. Imports do not set stock levels — quantity still comes from receive, count, and corrections.",
        ADMIN,
    )
]
IMP_TIP = "Never force red preflight rows. Fix the file."

add("/import", "Settings", "Import wizard", IMP_SUMMARY, IMP_PRIV, IMP_ACTIONS, IMP_MISTAKES, IMP_TIP)
add("/settings/import", "Settings", "Import wizard", IMP_SUMMARY, IMP_PRIV, IMP_ACTIONS, IMP_MISTAKES, IMP_TIP)

header = """-- Seed weGrowStock Page Info ("i") knowledge for every operational route and settings subpage.
-- Brand: weGrowStock. Immutable-ledger recoveries are explicit on every common mistake.

"""

OUT.write_text(header + "\n".join(rows), encoding="utf-8")
print(f"Wrote {len(rows)} rows to {OUT}")
