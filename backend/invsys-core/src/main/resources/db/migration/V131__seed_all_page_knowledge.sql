-- Seed weGrowStock Page Info ("i") knowledge for every operational route and settings subpage.
-- Brand: weGrowStock. Immutable-ledger recoveries are explicit on every common mistake.

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/dashboard',
    'Core',
    'Command Center',
    $pk$Your daily weGrowStock overview of warehouse operations, active tasks, and system health. Headline KPIs (Stock Value, Low Stock Count, Open Orders) sit above Work Queue cards such as Needs Allocation and Ready to Invoice. The dashboard itself never changes inventory.$pk$,
    $pk$Owners, Administrators, Warehouse Managers, Floor Pickers, and Viewers can open the dashboard. Pickers usually land on Fulfillment instead.$pk$,
    $pk$["Scan Headline KPIs (Stock Value, Low Stock, Open Orders) for red or amber signals.", "Work Needs Allocation and Ready to Invoice cards first.", "Open Sync Conflicts or Exceptions banners when they appear.", "Drill into Sales Orders, Purchase Orders, or Fulfillment from the quick links."]$pk$::jsonb,
    $pk$[{"mistake": "I dismissed a banner and thought the problem went away.", "solution": "Dismissing only hides the alert until the next refresh. Open Exceptions or Sync Conflicts and resolve the underlying item. The dashboard never posts a ledger entry.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "Numbers look wrong after a fat-fingered receive.", "solution": "Do not type a correction here. Open the product Ledger History and have a manager click Reverse transaction, or run a cycle count. weGrowStock never erases the original row.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Treat red KPI chips as a to-do list, not a report. Live warehouse totals come from floor and office activity — never from a hidden spreadsheet.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/purchase-orders',
    'Inbound',
    'Purchase Orders',
    $pk$Draft purchase orders to restock your warehouse. Purpose: create inbound supply contracts against approved suppliers so the dock can receive freight against expected lines. Search, sort, and page the list — weGrowStock loads one page from the server.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers create and submit POs. Floor Pickers receive against submitted POs on Inbound Receive.$pk$,
    $pk$["Click New PO, pick a supplier, and add SKU, quantity, unit cost, and UoM.", "Save as Draft, then Submit when the buy is firm.", "Mark In Transit when the vendor ships, then hand off to Floor receive.", "Never delete a PO that already has receipts — use RTV or a reversing receive."]$pk$::jsonb,
    $pk$[{"mistake": "I typed the wrong supplier price or quantity (100 instead of 10).", "solution": "If the PO is still Draft, edit the line. After Submit with no receipts, cancel the open lines and recreate. After receiving, a manager posts a Reverse Receipt / stock correction — never edit the posted ledger row.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I accidentally created a duplicate PO.", "solution": "Cancel the unused twin before anyone receives against it. If both were received, reverse the extra receipt and close the extra PO. History of both documents stays visible.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I picked the wrong supplier name because of a misspelling.", "solution": "Draft: change the supplier. Submitted with no receipts: cancel and recreate. Received: keep the PO, fix future buys on the supplier record, and use RTV if the freight must go back.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Confirm unit cost out loud before Submit. A wrong price on a submitted PO becomes a finance problem; a wrong receive becomes a ledger reversal.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/purchasing/orders',
    'Inbound',
    'Purchase Orders',
    $pk$Draft purchase orders to restock your warehouse. Purpose: create inbound supply contracts against approved suppliers so the dock can receive freight against expected lines. Search, sort, and page the list — weGrowStock loads one page from the server.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers create and submit POs. Floor Pickers receive against submitted POs on Inbound Receive.$pk$,
    $pk$["Click New PO, pick a supplier, and add SKU, quantity, unit cost, and UoM.", "Save as Draft, then Submit when the buy is firm.", "Mark In Transit when the vendor ships, then hand off to Floor receive.", "Never delete a PO that already has receipts — use RTV or a reversing receive."]$pk$::jsonb,
    $pk$[{"mistake": "I typed the wrong supplier price or quantity (100 instead of 10).", "solution": "If the PO is still Draft, edit the line. After Submit with no receipts, cancel the open lines and recreate. After receiving, a manager posts a Reverse Receipt / stock correction — never edit the posted ledger row.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I accidentally created a duplicate PO.", "solution": "Cancel the unused twin before anyone receives against it. If both were received, reverse the extra receipt and close the extra PO. History of both documents stays visible.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I picked the wrong supplier name because of a misspelling.", "solution": "Draft: change the supplier. Submitted with no receipts: cancel and recreate. Received: keep the PO, fix future buys on the supplier record, and use RTV if the freight must go back.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Confirm unit cost out loud before Submit. A wrong price on a submitted PO becomes a finance problem; a wrong receive becomes a ledger reversal.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/suppliers',
    'Inbound',
    'Suppliers',
    $pk$Vendor master data — legal name, payment terms, lead times, and masked banking details. Purchase orders require an approved supplier. Fix typos here; do not invent a second vendor for the same company.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers maintain suppliers. Viewers can read. Floor Pickers do not edit vendor master data.$pk$,
    $pk$["Click Add supplier and enter the legal name exactly as invoices will show it.", "Set lead times that feed MRP reorder suggestions.", "Save banking details — weGrowStock shows them masked after save.", "Deactivate unused vendors instead of deleting ones tied to historical POs."]$pk$::jsonb,
    $pk$[{"mistake": "I created a duplicate vendor because of a typo in the name.", "solution": "Keep the correctly spelled record. Point new POs at it and ask an Administrator to deactivate the twin. Historical POs stay on the original supplier — we do not merge ledger history.", "requiredRole": "ADMIN"}, {"mistake": "I misspelled the remittance address.", "solution": "Edit the supplier and save again. Address typos do not change stock. If a check already went out, finance issues a new payment — not a stock reversal.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Search before you add. A duplicate vendor is the most common purchasing master-data error and it silently splits spend reports.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/purchasing/suppliers',
    'Inbound',
    'Suppliers',
    $pk$Vendor master data — legal name, payment terms, lead times, and masked banking details. Purchase orders require an approved supplier. Fix typos here; do not invent a second vendor for the same company.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers maintain suppliers. Viewers can read. Floor Pickers do not edit vendor master data.$pk$,
    $pk$["Click Add supplier and enter the legal name exactly as invoices will show it.", "Set lead times that feed MRP reorder suggestions.", "Save banking details — weGrowStock shows them masked after save.", "Deactivate unused vendors instead of deleting ones tied to historical POs."]$pk$::jsonb,
    $pk$[{"mistake": "I created a duplicate vendor because of a typo in the name.", "solution": "Keep the correctly spelled record. Point new POs at it and ask an Administrator to deactivate the twin. Historical POs stay on the original supplier — we do not merge ledger history.", "requiredRole": "ADMIN"}, {"mistake": "I misspelled the remittance address.", "solution": "Edit the supplier and save again. Address typos do not change stock. If a check already went out, finance issues a new payment — not a stock reversal.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Search before you add. A duplicate vendor is the most common purchasing master-data error and it silently splits spend reports.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/mrp',
    'Inbound',
    'MRP Reorder',
    $pk$Material Requirements Planning reorder workspace. weGrowStock suggests buy quantities from demand, on-hand, and supplier lead times. This list can be long — it stays on virtualized scrolling, not page-by-page tables.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers run MRP (mrp:run). Pickers and Viewers do not consolidate suggestions.$pk$,
    $pk$["Review suggested lines and uncheck anything you do not want.", "Consolidate selected suggestions into draft purchase orders.", "Open the new POs and confirm prices before Submit."]$pk$::jsonb,
    $pk$[{"mistake": "I over-ordered because I accepted every calculated suggestion.", "solution": "Do not receive the extra. Cancel unreceived PO lines or create an RTV after goods arrive. Suggestions are advice, not a purchase.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I consolidated too early and created a PO for the wrong supplier.", "solution": "Cancel the draft/unreceived PO and re-run MRP after you fix the supplier on the product. Do not receive against the wrong vendor to 'make it match'.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Treat MRP like a shopping list you still proofread. Lead-time fat-fingers on the supplier record are a common reason suggestions look huge.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/purchasing/mrp',
    'Inbound',
    'MRP Reorder',
    $pk$Material Requirements Planning reorder workspace. weGrowStock suggests buy quantities from demand, on-hand, and supplier lead times. This list can be long — it stays on virtualized scrolling, not page-by-page tables.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers run MRP (mrp:run). Pickers and Viewers do not consolidate suggestions.$pk$,
    $pk$["Review suggested lines and uncheck anything you do not want.", "Consolidate selected suggestions into draft purchase orders.", "Open the new POs and confirm prices before Submit."]$pk$::jsonb,
    $pk$[{"mistake": "I over-ordered because I accepted every calculated suggestion.", "solution": "Do not receive the extra. Cancel unreceived PO lines or create an RTV after goods arrive. Suggestions are advice, not a purchase.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I consolidated too early and created a PO for the wrong supplier.", "solution": "Cancel the draft/unreceived PO and re-run MRP after you fix the supplier on the product. Do not receive against the wrong vendor to 'make it match'.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Treat MRP like a shopping list you still proofread. Lead-time fat-fingers on the supplier record are a common reason suggestions look huge.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inbound/receive',
    'Inbound',
    'Inbound Receiving',
    $pk$Inbound dock receiving bay. Scan the Purchase Order, verify physical quantities, capture GS1 / lot data, and put stock away. Over-receipts and damaged cartons must not be typed away — use Quarantine or RTV.$pk$,
    $pk$Floor Pickers and Warehouse Managers receive. Only a Warehouse Manager can approve an over-receipt outside tolerance.$pk$,
    $pk$["Scan the PO / ASN so expected lines appear.", "Scan each product (lot, expiry, or serial when prompted).", "Confirm quantity, then scan the putaway bin.", "Damaged boxes: move to a Quarantine location — do not receive as sellable."]$pk$::jsonb,
    $pk$[{"mistake": "I over-received (typed 100 instead of 10, or the truck sent extras).", "solution": "If still in the undo window, undo the scan. After commit, a manager posts a Reverse Receipt or sends extras back on RTV. Over-receipt tolerance exceptions need a Warehouse Manager.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I scanned the wrong GS1 barcode / wrong product.", "solution": "Undo immediately if the flash is still up. After commit, stop and tell a manager — they reverse the receive and you scan the correct label. Never 'fix' it with a second opposite scan.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "A carton arrived damaged.", "solution": "Receive into Quarantine (or Skip & Flag if your site uses that at the dock), photograph if asked, and let a manager disposition restock vs scrap. Do not put damaged goods on a pick face.", "requiredRole": "PICKER"}]$pk$::jsonb,
    $pk$If the badge says Offline - Caching Scans, stay extra precise. Parked receives wait in Exceptions → Sync Conflicts for a manager.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/purchasing/receive',
    'Inbound',
    'Inbound Receiving',
    $pk$Inbound dock receiving bay. Scan the Purchase Order, verify physical quantities, capture GS1 / lot data, and put stock away. Over-receipts and damaged cartons must not be typed away — use Quarantine or RTV.$pk$,
    $pk$Floor Pickers and Warehouse Managers receive. Only a Warehouse Manager can approve an over-receipt outside tolerance.$pk$,
    $pk$["Scan the PO / ASN so expected lines appear.", "Scan each product (lot, expiry, or serial when prompted).", "Confirm quantity, then scan the putaway bin.", "Damaged boxes: move to a Quarantine location — do not receive as sellable."]$pk$::jsonb,
    $pk$[{"mistake": "I over-received (typed 100 instead of 10, or the truck sent extras).", "solution": "If still in the undo window, undo the scan. After commit, a manager posts a Reverse Receipt or sends extras back on RTV. Over-receipt tolerance exceptions need a Warehouse Manager.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I scanned the wrong GS1 barcode / wrong product.", "solution": "Undo immediately if the flash is still up. After commit, stop and tell a manager — they reverse the receive and you scan the correct label. Never 'fix' it with a second opposite scan.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "A carton arrived damaged.", "solution": "Receive into Quarantine (or Skip & Flag if your site uses that at the dock), photograph if asked, and let a manager disposition restock vs scrap. Do not put damaged goods on a pick face.", "requiredRole": "PICKER"}]$pk$::jsonb,
    $pk$If the badge says Offline - Caching Scans, stay extra precise. Parked receives wait in Exceptions → Sync Conflicts for a manager.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/purchasing/rtv',
    'Inbound',
    'Return to Vendor',
    $pk$Return to Vendor chargebacks. Send defective, over-shipped, or refused inbound goods back to the supplier and record the credit memo against the original PO.$pk$,
    $pk$Warehouse Managers and Administrators create RTVs. Floor operators stage the freight.$pk$,
    $pk$["Open the original PO or the RTV workspace.", "Select lines and quantities actually going back.", "Confirm the vendor credit memo amount matches the paperwork.", "Hand the staged pallet to outbound shipping."]$pk$::jsonb,
    $pk$[{"mistake": "I entered an incorrect vendor credit memo amount.", "solution": "Do not edit the posted RTV silently. Issue a correcting credit (or ask finance to void and re-issue). Stock already returned stays on the RTV ledger; money is a separate reversing document.", "requiredRole": "ADMIN"}, {"mistake": "I returned the wrong quantity.", "solution": "If the truck has not left, adjust the RTV before ship. After ship, create a follow-up receive or a second RTV — never delete the first.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Match three numbers: PO line, physical cartons on the dock, and the supplier credit. If one disagrees, stop.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/returns',
    'Inbound',
    'Returns / RMA',
    $pk$Customer returns (RMA) office. Authorize returns, then the floor receives them with condition photos. Restock increases sellable stock; scrap does not.$pk$,
    $pk$Warehouse Managers and Administrators approve or deny RMAs. Floor operators receive on Returns receive.$pk$,
    $pk$["Click New RMA / Create RMA and review the request.", "Choose Approve & Buy Label, Approve without Label, or Deny & Close.", "Use Receive terminal so the floor can scan the return."]$pk$::jsonb,
    $pk$[{"mistake": "I approved a return that should have been denied.", "solution": "After receive you cannot Deny. Quarantine the stock and let finance decide the credit. History stays.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I received damaged goods as pristine.", "solution": "The condition photo protects you. Ask a manager to move the units to Quarantine and post a correction before anyone picks them.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Photos exist so nobody can later claim damaged goods were received as sellable.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/returns/receive',
    'Inbound',
    'Returns Receive (Floor)',
    $pk$Scan returned goods against an approved RMA. Capture a Condition photo, then Confirm +1 per unit.$pk$,
    $pk$Floor Pickers receive. Managers disposition restock vs scrap.$pk$,
    $pk$["Scan the RMA barcode.", "Photograph the item's real condition.", "Tap Confirm +1 per unit, then Scan next RMA."]$pk$::jsonb,
    $pk$[{"mistake": "I tapped Confirm +1 too many times.", "solution": "Tell a manager. A correction fixes the received quantity; the extra tap stays in the log.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Never receive a sealed mystery box as good stock — open it and photograph first.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/sales-orders',
    'Sales',
    'Sales Orders',
    $pk$B2B and office sales orders. Confirm demand, Allocate stock (FEFO), then release a picking wave. Status chips include DRAFT, CONFIRMED, ALLOCATED, BACKORDERED, PARTIALLY_SHIPPED, SHIPPED, and CANCELLED. Un-allocate / Cancel releases reserved stock without erasing history.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers confirm, allocate, and invoice. Pickers fulfill released waves on Fulfillment.$pk$,
    $pk$["Confirm a DRAFT order.", "Click Allocate to reserve on-hand (or leave BACKORDERED if short).", "Generate / release a picking wave for ALLOCATED orders.", "Use Un-allocate or Cancel to release reserved stock before pick."]$pk$::jsonb,
    $pk$[{"mistake": "I allocated the wrong order or the wrong quantity.", "solution": "Click Un-allocate to release reservations, fix the lines, then Allocate again. After picks, reverse via shipment void + stock correction — never erase shipped history.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I typed the wrong price or discount.", "solution": "Edit before Confirm. After invoice, void or credit-memo the invoice (Void Invoices permission) and re-bill. Stock does not change when you fix a price.", "requiredRole": "ADMIN"}, {"mistake": "I created a duplicate sales order.", "solution": "Cancel the unused twin before allocation. If both allocated, Un-allocate then Cancel the extra. If one already shipped, use Returns — do not delete the shipment.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If Allocate is greyed out, check Credit Hold and on-hand first. Forcing a pick around a hold is how the ledger and the cash desk disagree.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/sales/orders',
    'Sales',
    'Sales Orders',
    $pk$B2B and office sales orders. Confirm demand, Allocate stock (FEFO), then release a picking wave. Status chips include DRAFT, CONFIRMED, ALLOCATED, BACKORDERED, PARTIALLY_SHIPPED, SHIPPED, and CANCELLED. Un-allocate / Cancel releases reserved stock without erasing history.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers confirm, allocate, and invoice. Pickers fulfill released waves on Fulfillment.$pk$,
    $pk$["Confirm a DRAFT order.", "Click Allocate to reserve on-hand (or leave BACKORDERED if short).", "Generate / release a picking wave for ALLOCATED orders.", "Use Un-allocate or Cancel to release reserved stock before pick."]$pk$::jsonb,
    $pk$[{"mistake": "I allocated the wrong order or the wrong quantity.", "solution": "Click Un-allocate to release reservations, fix the lines, then Allocate again. After picks, reverse via shipment void + stock correction — never erase shipped history.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I typed the wrong price or discount.", "solution": "Edit before Confirm. After invoice, void or credit-memo the invoice (Void Invoices permission) and re-bill. Stock does not change when you fix a price.", "requiredRole": "ADMIN"}, {"mistake": "I created a duplicate sales order.", "solution": "Cancel the unused twin before allocation. If both allocated, Un-allocate then Cancel the extra. If one already shipped, use Returns — do not delete the shipment.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If Allocate is greyed out, check Credit Hold and on-hand first. Forcing a pick around a hold is how the ledger and the cash desk disagree.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/fulfillment/orders',
    'Fulfillment',
    'Sales Orders Fulfillment Queue',
    $pk$B2B and office sales orders. Confirm demand, Allocate stock (FEFO), then release a picking wave. Status chips include DRAFT, CONFIRMED, ALLOCATED, BACKORDERED, PARTIALLY_SHIPPED, SHIPPED, and CANCELLED. Un-allocate / Cancel releases reserved stock without erasing history.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers confirm, allocate, and invoice. Pickers fulfill released waves on Fulfillment.$pk$,
    $pk$["Confirm a DRAFT order.", "Click Allocate to reserve on-hand (or leave BACKORDERED if short).", "Generate / release a picking wave for ALLOCATED orders.", "Use Un-allocate or Cancel to release reserved stock before pick."]$pk$::jsonb,
    $pk$[{"mistake": "I allocated the wrong order or the wrong quantity.", "solution": "Click Un-allocate to release reservations, fix the lines, then Allocate again. After picks, reverse via shipment void + stock correction — never erase shipped history.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I typed the wrong price or discount.", "solution": "Edit before Confirm. After invoice, void or credit-memo the invoice (Void Invoices permission) and re-bill. Stock does not change when you fix a price.", "requiredRole": "ADMIN"}, {"mistake": "I created a duplicate sales order.", "solution": "Cancel the unused twin before allocation. If both allocated, Un-allocate then Cancel the extra. If one already shipped, use Returns — do not delete the shipment.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If Allocate is greyed out, check Credit Hold and on-hand first. Forcing a pick around a hold is how the ledger and the cash desk disagree.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/fulfillment',
    'Fulfillment',
    'Fulfillment & Picking',
    $pk$Fulfillment & picking. Group orders into waves, claim a wave to your scanner, pick items, then pack and print labels. Skip & Flag keeps the wave moving when a label is torn or an item is damaged.$pk$,
    $pk$Floor Pickers, Warehouse Managers, Administrators, and Owners. Viewers cannot pick.$pk$,
    $pk$["Unlock the shift PIN if prompted.", "Claim the wave (device lock) so two people cannot pick the same lines.", "Scan the directed location and SKU.", "At pack, cartonize, capture scale weight, and print the carrier label."]$pk$::jsonb,
    $pk$[{"mistake": "I picked the wrong SKU into a tote.", "solution": "Do not finish pack. Flag a tote-swap exception or ask a manager to reverse the pick with a stock correction and put the unit back. Then pick the correct SKU.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I clicked Shipped before the freight was in the truck.", "solution": "Tell a manager immediately. They void or reverse the ship event and you reprint when the truck is actually loaded. The early ship stays in history.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I dropped or broke an item.", "solution": "Skip & Flag, quarantine the unit, and pick a replacement. A manager posts the loss. Do not complete pack on damaged goods.", "requiredRole": "PICKER"}]$pk$::jsonb,
    $pk$Mis-scan? Use the short undo window before the scan is saved offline. After that, only a manager can post an offset entry.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/cluster-pick',
    'Fulfillment',
    'Cluster Pick',
    $pk$Multi-tote cluster picking — pick several orders in one walk, each into its own tote.$pk$,
    $pk$Floor Pickers and Warehouse Managers (Advanced Fulfillment module).$pk$,
    $pk$["Claim the cluster and confirm tote count.", "Scan location, SKU, then the destination tote.", "Stage completed totes for pack."]$pk$::jsonb,
    $pk$[{"mistake": "I put the wrong SKU into a tote.", "solution": "Raise a tote-swap exception. Do not silently swap units between totes without scanning — the ledger still thinks the first tote is correct.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If you lose track of which tote is which, stop and ask a manager to rebuild the cluster rather than guessing.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/fulfillment/cluster',
    'Fulfillment',
    'Cluster Pick',
    $pk$Multi-tote cluster picking — pick several orders in one walk, each into its own tote.$pk$,
    $pk$Floor Pickers and Warehouse Managers (Advanced Fulfillment module).$pk$,
    $pk$["Claim the cluster and confirm tote count.", "Scan location, SKU, then the destination tote.", "Stage completed totes for pack."]$pk$::jsonb,
    $pk$[{"mistake": "I put the wrong SKU into a tote.", "solution": "Raise a tote-swap exception. Do not silently swap units between totes without scanning — the ledger still thinks the first tote is correct.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If you lose track of which tote is which, stop and ask a manager to rebuild the cluster rather than guessing.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/fulfillment/waves',
    'Fulfillment',
    'Picking Waves',
    $pk$Picking wave orchestration — generate, optimize path, release, and unstick waves.$pk$,
    $pk$Warehouse Managers release waves. Pickers claim released work.$pk$,
    $pk$["Generate a wave from ALLOCATED orders.", "Optimize the walk path if offered.", "Release to floor devices."]$pk$::jsonb,
    $pk$[{"mistake": "The wave is stuck and nobody can claim it.", "solution": "A manager can force-release or rebuild the wave after clearing Exceptions. Do not invent picks on unreleased lines.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$A stuck wave is usually an open exception or a device lock — not missing stock.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/fulfillment/shipments',
    'Fulfillment',
    'Pack & Ship',
    $pk$Packing, cartonization, and carrier labels. Wrong box dimensions produce wrong rates and crushed freight.$pk$,
    $pk$Floor Pickers pack. Managers void a premature ship.$pk$,
    $pk$["Confirm cartonization suggestions.", "Capture scale weight.", "Print the carrier label only when the box is closed and on the dock."]$pk$::jsonb,
    $pk$[{"mistake": "I entered the wrong box dimensions.", "solution": "Recalculate the rate before you click Shipped. After ship, a manager voids the label and you reprint — do not toss the first label without voiding.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If the scale will not connect, retry Connect packing scale. Manual weight is a last resort — read it twice.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/fulfillment/dock',
    'Fulfillment',
    'Dock Schedule',
    $pk$Dock door scheduling — appointments for inbound and outbound carriers.$pk$,
    $pk$Warehouse Managers and Administrators book doors. Floor staff check the calendar.$pk$,
    $pk$["Create or confirm an appointment window.", "Assign a door and a carrier.", "Mark arrived / completed when the truck is actually there."]$pk$::jsonb,
    $pk$[{"mistake": "The carrier missed the appointment window.", "solution": "Reschedule the appointment. Do not receive or ship against the old slot. If freight was already scanned, keep those ledger rows and just fix the calendar.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$A calendar lie (marking arrived early) is how two trucks get assigned the same door.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/pallet-manifests',
    'Inventory',
    'Pallet Manifests / LPN',
    $pk$License Plate Number (LPN) building and pallet manifests. One plate moves every carton on the pallet.$pk$,
    $pk$Floor Pickers and Warehouse Managers mint and move LPNs (Advanced Fulfillment).$pk$,
    $pk$["Mint New LPN and stack cartons onto the plate.", "Use LPN Move: scan the plate, then the destination.", "Print the pallet manifest for the carrier."]$pk$::jsonb,
    $pk$[{"mistake": "I lost the physical pallet tag.", "solution": "Ask a manager to reprint the LPN or decompose the LPN back to cartons. Do not borrow another pallet's plate.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I moved the pallet but forgot to scan.", "solution": "Do the LPN Move now. The next picker will otherwise walk to an empty location.", "requiredRole": "PICKER"}]$pk$::jsonb,
    $pk$A missing plate is shrink until a manager writes it off — search first, then cycle-count the last location.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inventory/lpn',
    'Inventory',
    'License Plate Numbers',
    $pk$License Plate Number (LPN) building and pallet manifests. One plate moves every carton on the pallet.$pk$,
    $pk$Floor Pickers and Warehouse Managers mint and move LPNs (Advanced Fulfillment).$pk$,
    $pk$["Mint New LPN and stack cartons onto the plate.", "Use LPN Move: scan the plate, then the destination.", "Print the pallet manifest for the carrier."]$pk$::jsonb,
    $pk$[{"mistake": "I lost the physical pallet tag.", "solution": "Ask a manager to reprint the LPN or decompose the LPN back to cartons. Do not borrow another pallet's plate.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I moved the pallet but forgot to scan.", "solution": "Do the LPN Move now. The next picker will otherwise walk to an empty location.", "requiredRole": "PICKER"}]$pk$::jsonb,
    $pk$A missing plate is shrink until a manager writes it off — search first, then cycle-count the last location.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inventory/ledger',
    'Inventory',
    'Inventory Ledger',
    $pk$Double-entry inventory ledger history. Every receive, pick, count, and correction is an append-only row. weGrowStock never deletes the past — you post an offset entry (Reverse transaction) so the math becomes correct and the mistake stays visible.$pk$,
    $pk$Everyone can read. Reversing a movement requires a Warehouse Manager (or above) with Adjust Inventory.$pk$,
    $pk$["Open a product peek → Ledger History, or this dedicated ledger view.", "Filter by SKU, location, date, or movement type.", "On a reversible row, click Reverse transaction → Confirm Reversal."]$pk$::jsonb,
    $pk$[{"mistake": "I want to delete a bad row.", "solution": "You cannot, by design. Click Reverse transaction to post an equal-and-opposite entry attributed to you. If Reverse is greyed out, run a cycle count or a manager stock correction instead.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I reversed the wrong movement.", "solution": "Reverse the reversal (or post another correction). Two honest offsets are better than hiding history.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$If someone asks 'why does the shelf disagree with the computer?', the answer is always in this diary.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/products',
    'Inventory',
    'Product Catalog',
    $pk$Product catalog — SKUs, barcodes, UoM, imagery, on-hand / allocated / available-to-promise. Catalog edits do not change stock quantities.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers maintain the catalog. Viewers read. Pickers rarely need this desktop page.$pk$,
    $pk$["Search the catalog (server-side, debounced).", "Click Add product or Import for a new SKU.", "Open Ledger History on a product to see movements."]$pk$::jsonb,
    $pk$[{"mistake": "I misspelled the product name.", "solution": "Edit and Save. Name typos do not need a ledger reversal.", "requiredRole": "ADMIN"}, {"mistake": "I created a duplicate SKU.", "solution": "Retire the twin and point future work at the correct SKU. Movement history on both remains.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Never type on-hand as a free-text field. Quantity truth only enters through receive, count, pick, and corrections.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/cycle-counts',
    'Inventory',
    'Cycle Counts',
    $pk$Blind cycle counting. The scanner hides the expected quantity so you count what your eyes see. Small variances may auto-approve; large fat-fingered counts (1000 instead of 10) park as PENDING MANAGER REVIEW.$pk$,
    $pk$Floor Pickers perform counts. Only a Warehouse Manager can Approve Ledger Adjustment or Request Recount.$pk$,
    $pk$["Scan the bin, then the product.", "Type the physical count and confirm.", "Managers approve or request a recount on large variances."]$pk$::jsonb,
    $pk$[{"mistake": "I fat-fingered the count (typed 1000 instead of 10).", "solution": "Do not panic — massive variances do not silently change stock. Tell your manager it was a typo. They click Request Recount. You count again. The typo stays in the log.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I counted the wrong bin.", "solution": "Tell the manager before approval. If already approved, they post a correction after a recount of the right bin.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$A wrong honest count is fixable. A fake count that matches 'what the system usually says' poisons every order that trusts it.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inventory/cycle-counts',
    'Inventory',
    'Cycle Counts',
    $pk$Blind cycle counting. The scanner hides the expected quantity so you count what your eyes see. Small variances may auto-approve; large fat-fingered counts (1000 instead of 10) park as PENDING MANAGER REVIEW.$pk$,
    $pk$Floor Pickers perform counts. Only a Warehouse Manager can Approve Ledger Adjustment or Request Recount.$pk$,
    $pk$["Scan the bin, then the product.", "Type the physical count and confirm.", "Managers approve or request a recount on large variances."]$pk$::jsonb,
    $pk$[{"mistake": "I fat-fingered the count (typed 1000 instead of 10).", "solution": "Do not panic — massive variances do not silently change stock. Tell your manager it was a typo. They click Request Recount. You count again. The typo stays in the log.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I counted the wrong bin.", "solution": "Tell the manager before approval. If already approved, they post a correction after a recount of the right bin.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$A wrong honest count is fixable. A fake count that matches 'what the system usually says' poisons every order that trusts it.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/replenishments',
    'Inventory',
    'Replenishments',
    $pk$Min-max and wave replenishment — move stock from reserve/bulk into pick faces so waves do not starve.$pk$,
    $pk$Floor Pickers and Warehouse Managers.$pk$,
    $pk$["Take a replenishment task.", "Scan the from (reserve) bin, move the stock, scan the to (pick face) bin.", "Return to Fulfillment and keep picking."]$pk$::jsonb,
    $pk$[{"mistake": "The pick face overflowed / I put too much in the bin.", "solution": "Ask a manager to reassign the extra to bulk overstock with a documented TRANSFER. Do not delete the original replenishment row.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I moved stock to the wrong bin.", "solution": "Ask for a corrective TRANSFER. Two honest moves are fine.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Empty pick face and no tasks usually means reserve is empty too — that is a PO problem, not a workaround.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inventory/replenishments',
    'Inventory',
    'Replenishments',
    $pk$Min-max and wave replenishment — move stock from reserve/bulk into pick faces so waves do not starve.$pk$,
    $pk$Floor Pickers and Warehouse Managers.$pk$,
    $pk$["Take a replenishment task.", "Scan the from (reserve) bin, move the stock, scan the to (pick face) bin.", "Return to Fulfillment and keep picking."]$pk$::jsonb,
    $pk$[{"mistake": "The pick face overflowed / I put too much in the bin.", "solution": "Ask a manager to reassign the extra to bulk overstock with a documented TRANSFER. Do not delete the original replenishment row.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I moved stock to the wrong bin.", "solution": "Ask for a corrective TRANSFER. Two honest moves are fine.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Empty pick face and no tasks usually means reserve is empty too — that is a PO problem, not a workaround.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/exceptions',
    'Inventory',
    'Exceptions Desk',
    $pk$Exceptions desk — Fulfillment Holds (Skip & Flag, damaged goods) and Sync Conflicts (offline parked scans). An exception means weGrowStock refused to guess, not that you broke the system.$pk$,
    $pk$Warehouse Managers decide Approve & Re-process vs Discard Transaction. Pickers can read their parked scans.$pk$,
    $pk$["Read the card: who, bin, quantity, scan type.", "Walk to the physical bin before you click.", "Approve & Re-process if the action really happened; Discard Transaction if it did not."]$pk$::jsonb,
    $pk$[{"mistake": "Two workers scanned the same pallet while offline.", "solution": "The second replay parks here. Look at the shelf, then Approve the real move and Discard the duplicate. If unsure, cycle-count the bin first.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I approved something that did not happen.", "solution": "Cycle-count the bin. The variance approval writes the offset entry. Nothing is lost.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Ninety percent of conflict resolution is looking at the shelf.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inventory/quarantine',
    'Inventory',
    'Quarantine / QC Hold',
    $pk$Exceptions desk — Fulfillment Holds (Skip & Flag, damaged goods) and Sync Conflicts (offline parked scans). An exception means weGrowStock refused to guess, not that you broke the system.$pk$,
    $pk$Warehouse Managers decide Approve & Re-process vs Discard Transaction. Pickers can read their parked scans.$pk$,
    $pk$["Read the card: who, bin, quantity, scan type.", "Walk to the physical bin before you click.", "Approve & Re-process if the action really happened; Discard Transaction if it did not."]$pk$::jsonb,
    $pk$[{"mistake": "Two workers scanned the same pallet while offline.", "solution": "The second replay parks here. Look at the shelf, then Approve the real move and Discard the duplicate. If unsure, cycle-count the bin first.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I approved something that did not happen.", "solution": "Cycle-count the bin. The variance approval writes the offset entry. Nothing is lost.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Ninety percent of conflict resolution is looking at the shelf.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/compliance/lot-trace',
    'Inventory',
    'Lot Trace',
    $pk$Lot / serial trace for recalls. Follow a batch from supplier receive through assembly to the customer.$pk$,
    $pk$Warehouse Managers, Administrators, Owners, and Viewers (read-only). Pickers report expired lots.$pk$,
    $pk$["Enter the lot number and click Trace.", "Review on-hand bins and affected customers.", "Export affected customers when outreach is required."]$pk$::jsonb,
    $pk$[{"mistake": "I found expired lots on the shelf.", "solution": "Do not pick them. Quarantine, run Lot Trace, then a manager posts the disposal as an attributed correction. FEFO exists to prevent this — reporting one is doing your job.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "A lot failed inspection.", "solution": "Keep it in Quarantine. Release to salvage/scrap only after a manager dispositions it. Do not quietly return it to a pick face.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Trace is investigative — it never edits history.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/rtls',
    'Platform',
    'RTLS Map',
    $pk$Digital twin warehouse map — live picker positions, congestion heat, and walkable edges for wayfinding.$pk$,
    $pk$Warehouse Managers and Administrators (RTLS module). Coordinate edits do not change stock.$pk$,
    $pk$["Open the map and watch live positions.", "Inspect the heatmap of recent movement.", "Mark a blocked aisle as an unwalkable edge when a pallet is down."]$pk$::jsonb,
    $pk$[{"mistake": "I marked the wrong aisle unwalkable.", "solution": "Edit the edge again. Map edits are layout, not inventory. Stock still sits where the last scan said it sits.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Sample telemetry is for training — it is not a stock correction.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/inventory/spatial',
    'Inventory',
    'Spatial Map',
    $pk$Digital twin warehouse map — live picker positions, congestion heat, and walkable edges for wayfinding.$pk$,
    $pk$Warehouse Managers and Administrators (RTLS module). Coordinate edits do not change stock.$pk$,
    $pk$["Open the map and watch live positions.", "Inspect the heatmap of recent movement.", "Mark a blocked aisle as an unwalkable edge when a pallet is down."]$pk$::jsonb,
    $pk$[{"mistake": "I marked the wrong aisle unwalkable.", "solution": "Edit the edge again. Map edits are layout, not inventory. Stock still sits where the last scan said it sits.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Sample telemetry is for training — it is not a stock correction.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/manufacturing/boms',
    'Manufacturing',
    'Bills of Materials',
    $pk$Multi-level Bills of Materials — the recipe that says which components, and how many, make one finished good.$pk$,
    $pk$Warehouse Managers, Administrators, and Owners (Manufacturing module). Viewers read. Operators do not edit recipes.$pk$,
    $pk$["Create a BOM for the finished SKU.", "Add component lines with quantity per one finished unit — not per batch.", "Save before anyone starts a production order."]$pk$::jsonb,
    $pk$[{"mistake": "I entered a circular assembly or the wrong component ratio.", "solution": "If nothing is built yet, edit the BOM. After builds completed, Disassemble the kits, fix the recipe, then rebuild on a new order. Do not delete completed production history.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$A classic beginner error is entering totals for the whole batch instead of the quantity per one finished unit.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/manufacturing/orders',
    'Manufacturing',
    'Production Orders',
    $pk$Assembly and disassembly work orders. Statuses: DRAFT, COMPONENTS ALLOCATED, WIP, COMPLETED, CANCELLED.$pk$,
    $pk$Warehouse Managers, Administrators, and Owners create orders. Operators run them on the terminal.$pk$,
    $pk$["Click Create order, pick the BOM and quantity.", "Allocate components, then hand the order to the Manufacturing terminal.", "Use Disassemble to split finished goods back into components."]$pk$::jsonb,
    $pk$[{"mistake": "I logged too much scrap.", "solution": "A manager posts a scrap ledger reversal (offset entry) and, if the parts are still good, receives them back. Do not edit the completed order's history.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I built kits with the wrong BOM.", "solution": "Fix the BOM, click Disassemble, put components away, then create a new production order.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Cancel while still DRAFT — components release automatically. After COMPLETED, only Disassemble or a correction undoes the trade.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/manufacturing/terminal',
    'Manufacturing',
    'Manufacturing Terminal',
    $pk$Manufacturing terminal — floor punch clock for the build timesheet plus Complete build. The header Clock in / Clock out is your shift; Start/Stop timesheet is this order.$pk$,
    $pk$Operators (Picker-type roles) run the terminal. Managers correct labor and completions.$pk$,
    $pk$["Select the production order.", "Start timesheet, scan components, Stop timesheet for breaks.", "Click Complete build only when units are actually finished."]$pk$::jsonb,
    $pk$[{"mistake": "I forgot to clock out / typed the wrong hours.", "solution": "You cannot edit your own past time. Tell a supervisor the real times the same day. They post a manual adjustment next to the original entry.", "requiredRole": "WAREHOUSE_MANAGER"}, {"mistake": "I clicked Complete build too early.", "solution": "Tell a manager immediately. They reverse with an attributed correction or Disassemble if goods were only partially real.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Never 'balance' a forgotten clock-out by clocking weird hours tomorrow — two wrong entries are harder to fix than one.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/field/truck',
    'Field',
    'Technician Truck',
    $pk$Technician van stock — Assign to me, Transfer to van, Consume from van.$pk$,
    $pk$Field technicians and Warehouse Managers.$pk$,
    $pk$["Click Assign to me to claim the truck.", "Transfer to van with scans when loading.", "Consume from van on site for each part used."]$pk$::jsonb,
    $pk$[{"mistake": "I replenished or consumed against an unassigned truck.", "solution": "Assign the truck to yourself first. If parts already moved, a manager posts a reverse transfer. Do not share another tech's session.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Offline field scans park in Exceptions → Sync Conflicts when you reconnect.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/issue-supplies',
    'Field',
    'Issue Supplies',
    $pk$Internal supplies checkout against a cost center — not a customer shipment.$pk$,
    $pk$Warehouse Managers and permitted Pickers. Administrators configure cost centers.$pk$,
    $pk$["Select the cost center / requisition.", "Scan items and submit with Issue Fact."]$pk$::jsonb,
    $pk$[{"mistake": "I checked out supplies to the wrong cost center.", "solution": "Ask a manager for a return-to-stock correction, then re-issue to the right center. Do not delete the Issue Fact.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$No cost centers listed? An Admin must open Settings → Cost Centers & Requisitions first.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/field/supplies',
    'Field',
    'Issue Supplies',
    $pk$Internal supplies checkout against a cost center — not a customer shipment.$pk$,
    $pk$Warehouse Managers and permitted Pickers. Administrators configure cost centers.$pk$,
    $pk$["Select the cost center / requisition.", "Scan items and submit with Issue Fact."]$pk$::jsonb,
    $pk$[{"mistake": "I checked out supplies to the wrong cost center.", "solution": "Ask a manager for a return-to-stock correction, then re-issue to the right center. Do not delete the Issue Fact.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$No cost centers listed? An Admin must open Settings → Cost Centers & Requisitions first.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/invoices',
    'Sales',
    'Invoices',
    $pk$Commercial invoicing and factoring. Posted invoices are ledger documents — void or credit-memo, never delete. Statuses include DRAFT, OPEN, PAID, VOID.$pk$,
    $pk$Owners and Administrators invoice. Voiding requires the Void Invoices permission.$pk$,
    $pk$["Open a shipped sales order and click Invoice or Invoice remaining.", "Check customer, line prices, and total out loud.", "Watch Dashboard Open AR and the buyer Showroom Billing tab."]$pk$::jsonb,
    $pk$[{"mistake": "I issued an invoice with the wrong price or created a duplicate.", "solution": "Void the bad invoice (or issue a credit memo) and re-invoice correctly. If a payment landed on the duplicate, finance applies it to the correct document. This is a reversing finance entry — not a stock edit.", "requiredRole": "ADMIN"}, {"mistake": "I need to reverse a refunded invoice.", "solution": "Issue the credit note / void (money), receive goods through the RMA flow (stock), and refund through payment rails. Three signed entries, zero deletions.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Money fixes and stock fixes are separate entries. Do both; delete neither.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/sales/invoices',
    'Sales',
    'Invoices',
    $pk$Commercial invoicing and factoring. Posted invoices are ledger documents — void or credit-memo, never delete. Statuses include DRAFT, OPEN, PAID, VOID.$pk$,
    $pk$Owners and Administrators invoice. Voiding requires the Void Invoices permission.$pk$,
    $pk$["Open a shipped sales order and click Invoice or Invoice remaining.", "Check customer, line prices, and total out loud.", "Watch Dashboard Open AR and the buyer Showroom Billing tab."]$pk$::jsonb,
    $pk$[{"mistake": "I issued an invoice with the wrong price or created a duplicate.", "solution": "Void the bad invoice (or issue a credit memo) and re-invoice correctly. If a payment landed on the duplicate, finance applies it to the correct document. This is a reversing finance entry — not a stock edit.", "requiredRole": "ADMIN"}, {"mistake": "I need to reverse a refunded invoice.", "solution": "Issue the credit note / void (money), receive goods through the RMA flow (stock), and refund through payment rails. Three signed entries, zero deletions.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Money fixes and stock fixes are separate entries. Do both; delete neither.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/customers',
    'Sales',
    'Customers',
    $pk$Customer accounts, ship-to addresses, and credit lines that gate Allocate and Showroom checkout.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers (commercial policy varies).$pk$,
    $pk$["Create or edit the customer profile and ship-to.", "Set the credit limit.", "Review Wholesale Applications on this page."]$pk$::jsonb,
    $pk$[{"mistake": "The customer exceeded their credit limit.", "solution": "Do not force Allocate. Request a credit override from an Owner (raise the limit visibly) or wait for payment. Working around a hold is how AR and the warehouse disagree.", "requiredRole": "OWNER"}, {"mistake": "I misspelled the customer name or ship-to address.", "solution": "Edit before the first shipment. After ship, fix the master record and arrange a carrier intercept or a return — do not silently rewrite the shipped order.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Search before adding. Duplicate customers split credit exposure and invoice history.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/sales/customers',
    'Sales',
    'Customers',
    $pk$Customer accounts, ship-to addresses, and credit lines that gate Allocate and Showroom checkout.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers (commercial policy varies).$pk$,
    $pk$["Create or edit the customer profile and ship-to.", "Set the credit limit.", "Review Wholesale Applications on this page."]$pk$::jsonb,
    $pk$[{"mistake": "The customer exceeded their credit limit.", "solution": "Do not force Allocate. Request a credit override from an Owner (raise the limit visibly) or wait for payment. Working around a hold is how AR and the warehouse disagree.", "requiredRole": "OWNER"}, {"mistake": "I misspelled the customer name or ship-to address.", "solution": "Edit before the first shipment. After ship, fix the master record and arrange a carrier intercept or a return — do not silently rewrite the shipped order.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Search before adding. Duplicate customers split credit exposure and invoice history.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/sales/wholesale',
    'Sales',
    'Wholesale Applications',
    $pk$Wholesale B2B application approvals submitted from the Showroom.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Open the Wholesale Applications panel on Customers.", "Review the business name, tax ID, and requested terms.", "Approve to create the customer, or reject per policy."]$pk$::jsonb,
    $pk$[{"mistake": "I rejected an application by accident.", "solution": "The business can re-apply, or an Admin creates the customer manually. There is no secret 'undelete' — approval is a new action.", "requiredRole": "ADMIN"}, {"mistake": "I approved with the wrong credit terms.", "solution": "Edit the customer record. Already-posted invoices keep the terms they were issued under.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Approving is a credit decision. Treat it like opening a tab at your warehouse.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/showroom',
    'Showroom',
    'B2B Showroom',
    $pk$B2B digital showroom catalog. Buyers see sellable items and negotiated prices — never warehouse bin maps. If a customer sees a restricted price tier, an Admin maps the correct tier on the customer record.$pk$,
    $pk$B2B Customers shop here. Seller staff support commercially but do not pick from this screen.$pk$,
    $pk$["Browse Catalog and add quantities to the cart.", "Review Checkout (ship-to and terms) before Place order.", "Track status under Orders; start returns with Return Items."]$pk$::jsonb,
    $pk$[{"mistake": "The customer sees a restricted or wrong price tier.", "solution": "An Administrator assigns the correct price list on the customer record. Do not ask pickers for a bin workaround or a silent discount on the warehouse floor.", "requiredRole": "ADMIN"}, {"mistake": "The buyer placed the wrong order.", "solution": "Cancel before the seller allocates/ships, or use Return Items after ship. Warehouse history is not rewritten for buyer regret.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Checkout blocked by credit is automatic, not personal — pay down Billing or ask the seller Owner for a visible limit change.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/showroom/catalog',
    'Showroom',
    'Showroom Catalog',
    $pk$B2B digital showroom catalog. Buyers see sellable items and negotiated prices — never warehouse bin maps. If a customer sees a restricted price tier, an Admin maps the correct tier on the customer record.$pk$,
    $pk$B2B Customers shop here. Seller staff support commercially but do not pick from this screen.$pk$,
    $pk$["Browse Catalog and add quantities to the cart.", "Review Checkout (ship-to and terms) before Place order.", "Track status under Orders; start returns with Return Items."]$pk$::jsonb,
    $pk$[{"mistake": "The customer sees a restricted or wrong price tier.", "solution": "An Administrator assigns the correct price list on the customer record. Do not ask pickers for a bin workaround or a silent discount on the warehouse floor.", "requiredRole": "ADMIN"}, {"mistake": "The buyer placed the wrong order.", "solution": "Cancel before the seller allocates/ships, or use Return Items after ship. Warehouse history is not rewritten for buyer regret.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Checkout blocked by credit is automatic, not personal — pay down Billing or ask the seller Owner for a visible limit change.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/showroom/orders',
    'Showroom',
    'Showroom Orders',
    $pk$Track wholesale order progress and start returns with Return Items → Submit return.$pk$,
    $pk$B2B Customers only.$pk$,
    $pk$["Open Orders", "Click Return Items when goods must come back", "Use Browse catalog to reorder"]$pk$::jsonb,
    $pk$[{"mistake": "Return Items is missing.", "solution": "The order may still be shipping. Wait or call your rep. Unauthorized freight without an approved RMA will be refused.", "requiredRole": "ANY"}]$pk$::jsonb,
    $pk$Denied returns (Deny & Close) mean keep or dispose per your agreement — do not ship unauthorized freight.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/showroom/checkout',
    'Showroom',
    'Showroom Checkout',
    $pk$Submit a wholesale order against catalog availability and account terms.$pk$,
    $pk$B2B Customers.$pk$,
    $pk$["Review quantities and ship-to", "Click Place order"]$pk$::jsonb,
    $pk$[{"mistake": "Place order is disabled.", "solution": "Cart empty, item unavailable, or Credit Hold. Check Billing or contact your rep.", "requiredRole": "ANY"}]$pk$::jsonb,
    $pk$Edit the cart freely until Place order. After that, cancellations go through the seller office.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/showroom/billing',
    'Showroom',
    'Showroom Billing',
    $pk$Open invoices and balances that decide whether new checkout is allowed.$pk$,
    $pk$B2B Customers see their own billing only.$pk$,
    $pk$["Review open invoices", "Coordinate payment so Credit Hold clears"]$pk$::jsonb,
    $pk$[{"mistake": "I dispute a charge.", "solution": "Ask your rep for a credit memo. Do not ask the warehouse to erase a shipment.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Paying down Billing is the fastest way to unlock Allocate and Place order.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings',
    'Settings',
    'Tenant Settings',
    $pk$Organization settings overview and system health. Tabs cover profile, users, warehouses, inventory rules, documents, Retail POS, security, reconciliation, accounting, integrations, mesh, operations, automations, sync conflicts, and cost centers.$pk$,
    $pk$Owners and Administrators. Warehouse Managers may see Operations/Sync Conflicts depending on policy. Pickers cannot open Organization settings.$pk$,
    $pk$["Open the tab that matches the change you need.", "Save — the audit log records who changed what.", "Confirm floor devices pick up the new rule on the next action."]$pk$::jsonb,
    $pk$[{"mistake": "I toggled the wrong floor rule.", "solution": "Toggle it back. Every change is audited. Raising an adjustment limit does not auto-approve old pending counts.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Billing and Cash Flow & Financing are Owner-only hubs outside these tabs.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/organization',
    'Settings',
    'Organization',
    $pk$Organization settings overview and system health. Tabs cover profile, users, warehouses, inventory rules, documents, Retail POS, security, reconciliation, accounting, integrations, mesh, operations, automations, sync conflicts, and cost centers.$pk$,
    $pk$Owners and Administrators. Warehouse Managers may see Operations/Sync Conflicts depending on policy. Pickers cannot open Organization settings.$pk$,
    $pk$["Open the tab that matches the change you need.", "Save — the audit log records who changed what.", "Confirm floor devices pick up the new rule on the next action."]$pk$::jsonb,
    $pk$[{"mistake": "I toggled the wrong floor rule.", "solution": "Toggle it back. Every change is audited. Raising an adjustment limit does not auto-approve old pending counts.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Billing and Cash Flow & Financing are Owner-only hubs outside these tabs.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=profile',
    'Settings',
    'Settings — Profile',
    $pk$Your user profile and the default organization name shown across weGrowStock.$pk$,
    $pk$Every signed-in user edits their own profile. Role changes live on the Users tab.$pk$,
    $pk$["Update display name and locale", "Save — changes apply immediately"]$pk$::jsonb,
    $pk$[{"mistake": "I changed the wrong language.", "solution": "Change it back and save. Profile edits never touch inventory.", "requiredRole": "ANY"}]$pk$::jsonb,
    $pk$Org legal name on documents is configured under the Documents tab.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=users',
    'Settings',
    'Settings — Users',
    $pk$Invite company users and assign roles (Owner, Admin, Warehouse Manager, Picker, Viewer, B2B Customer) plus warehouse access. OWNER cannot be casually demoted.$pk$,
    $pk$Owners and Administrators. Warehouse Managers do not invite Owners.$pk$,
    $pk$["Invite a user", "Assign roles and warehouse checkboxes", "Save — next login enforces capabilities"]$pk$::jsonb,
    $pk$[{"mistake": "I locked a user out of every screen.", "solution": "An Administrator (or Super Admin via control plane) restores a role on this tab. Deactivate rather than delete users tied to ledger history.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Pickers only see assigned buildings. That is why a new hire's Fulfillment queue can look empty.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=warehouses',
    'Settings',
    'Settings — Warehouses',
    $pk$Facility management — buildings, zones, bins, and aisle setup used by putaway, RTLS, and pick pathing.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Add or edit a warehouse", "Maintain zones/bins in the visualizer", "Assign users on the Users tab"]$pk$::jsonb,
    $pk$[{"mistake": "I deleted or renamed a warehouse that still has stock.", "solution": "Deactivate instead of deleting buildings with ledger history. Move stock with transfers — renaming does not move inventory.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Wrong active warehouse in the header is the usual reason screens look empty after login.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=inventory',
    'Settings',
    'Settings — Inventory Rules',
    $pk$Reorder points, UoM defaults, and policy knobs that feed ATP and replenishment.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Adjust reorder / safety-stock defaults", "Save — Low Stock KPIs pick up the new thresholds"]$pk$::jsonb,
    $pk$[{"mistake": "I set reorder points so low that MRP over-ordered.", "solution": "Restore prior thresholds and save again. Cancel unreceived POs created from the bad run.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Dashboard Low Stock Count uses these thresholds.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=documents',
    'Settings',
    'Settings — Documents',
    $pk$Templates and numbering for POs, packing slips, invoices, and other printable documents.$pk$,
    $pk$Owners and Administrators (Documents module).$pk$,
    $pk$["Pick a document type", "Update logo, footer, or number series", "Save"]$pk$::jsonb,
    $pk$[{"mistake": "I saved a typo on the invoice footer.", "solution": "Edit and save again. Already-printed PDFs are not rewritten.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Ship and PO submit flows render from these templates.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=retailPos',
    'Settings',
    'Settings — Retail POS',
    $pk$Point of Sale registers — receipt branding, USD/MXN, Mexican CFDI, and blind closeout. Cashiers count cash without seeing the expected drawer total when blind closeout is on.$pk$,
    $pk$Owners and Administrators, and only when the tenant includes Retail POS.$pk$,
    $pk$["Set currency and CFDI", "Edit receipt header/footer", "Toggle Require Blind Closeout at Shift End", "Save POS settings"]$pk$::jsonb,
    $pk$[{"mistake": "The register cash variance at end of shift does not match.", "solution": "Do not edit historical WMS invoices. Recount the drawer. A manager posts the POS variance per store policy. Blind closeout exists so cashiers cannot aim at the expected total.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Unsupported currencies other than USD/MXN are rejected. Warehouse managers cannot change these settings from the register.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=security',
    'Settings',
    'Settings — Security & SSO',
    $pk$Tenant security, password / SSO rules, MFA, and Desktop Idle Timeout. Idle lock protects shared office PCs.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Configure SSO if your IdP is ready", "Review session and Desktop Idle Timeout", "Save"]$pk$::jsonb,
    $pk$[{"mistake": "I enabled SSO and locked everyone out.", "solution": "Use the break-glass Owner local login (or Super Admin) to disable SSO, then fix the IdP mapping. Every security change is audited.", "requiredRole": "OWNER"}]$pk$::jsonb,
    $pk$Disable SSO carefully — confirm local login still works for an Owner before cutover.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=reconciliation',
    'Settings',
    'Settings — Reconciliation',
    $pk$Compare weGrowStock levels to connected storefronts and books. Jobs report drift; they do not delete ledger rows.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Review the last run", "Trigger a reconcile when finance asks", "Investigate mismatches on operational pages"]$pk$::jsonb,
    $pk$[{"mistake": "Numbers disagree with QuickBooks.", "solution": "Fix the operational document (void invoice, reverse receive) then re-sync. Do not force the warehouse number to match dollars.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Pair with Accounting Sync and Cycle Counts when the three-way match fails.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=accounting',
    'Settings',
    'Settings — Accounting Sync',
    $pk$Connect QuickBooks/Xero so invoices and journals flow through finance sync.$pk$,
    $pk$Owners and Administrators (Accounting module).$pk$,
    $pk$["Connect or refresh the adapter", "Map tax schemes", "Retry FAILED rows"]$pk$::jsonb,
    $pk$[{"mistake": "A journal posted twice.", "solution": "Void the duplicate in the accounting system. Disconnecting weGrowStock stops new syncs; it does not erase external journals.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Paid invoices and COGS depend on this bridge.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=integrations',
    'Settings',
    'Settings — Integrations',
    $pk$Integration Hub for e-commerce storefronts (Shopify and similar) and accounting connections so orders and payments land without double entry.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Choose a connector", "Paste connection keys", "Enable and verify a test order"]$pk$::jsonb,
    $pk$[{"mistake": "Webhook sync failed and orders stopped landing.", "solution": "Open the connector, replay the outbox / retry the failed delivery, then confirm secrets. Already-imported sales orders stay in the outbound pipeline.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Storefront orders still allocate and ship like office-entered orders.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=mesh',
    'Settings',
    'Settings — Partner Catalog',
    $pk$Cross-tenant mesh SKU mappings so seller items resolve to buyer products on multi-party POs/SOs.$pk$,
    $pk$Owners and Administrators (Mesh Network module).$pk$,
    $pk$["Open Partner Catalog Mapping", "Map partner SKUs to local variants", "Save"]$pk$::jsonb,
    $pk$[{"mistake": "I mapped the wrong SKU.", "solution": "Remap. Historical documents keep the snapshot they were created with — fix the next PO, do not rewrite the last one.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Unmapped mesh lines may create DRAFT exception sales orders for review.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=operations',
    'Settings',
    'Settings — Operations',
    $pk$Floor rules — blind receiving, adjustment limits, scanner options — plus the Audit Log of who changed what.$pk$,
    $pk$Owners and Administrators. Managers follow the limits; they do not always edit them.$pk$,
    $pk$["Toggle blind receiving or max adjust qty", "Save", "Use Audit Log / Activity Timeline to see who changed a limit"]$pk$::jsonb,
    $pk$[{"mistake": "I raised adjustment limits and old counts auto-approved.", "solution": "They do not. Pending manager review counts still need a human. Toggle the rule back if the change was a mistake — the audit log keeps both saves.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Blind receiving and variance thresholds change what pickers may post without manager review.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=automations',
    'Settings',
    'Settings — Automations',
    $pk$Business rule triggers (reorder emails, status hops, notifications). A rogue rule can spam the floor.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Review enabled rules", "Disable the toggle on any rule that misfires", "Save"]$pk$::jsonb,
    $pk$[{"mistake": "A rogue automation rule created duplicate POs or emails.", "solution": "Disable the toggle immediately. Cancel the extra documents. Do not leave a half-tested rule LIVE overnight.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Automations never erase ledger rows — they only create new work. Turn the toggle off first, then clean up.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=syncConflicts',
    'Settings',
    'Settings — Sync Conflicts',
    $pk$The same parked-offline-scan queue as Inventory → Exceptions → Sync Conflicts.$pk$,
    $pk$Warehouse Managers and Administrators.$pk$,
    $pk$["Open a PARKED conflict", "Approve & Re-process or Discard Transaction"]$pk$::jsonb,
    $pk$[{"mistake": "I discarded a scan that really happened.", "solution": "Cycle-count the bin so the offset entry restores truth.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Pickers keep working while managers clear this quarantine.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings?tab=costCenters',
    'Settings',
    'Settings — Cost Centers & Requisitions',
    $pk$Internal budgets that authorize Issue Supplies without a customer sales order.$pk$,
    $pk$Administrators configure. Managers approve requisitions.$pk$,
    $pk$["Create a cost center", "Approve DRAFT requisitions", "Floor Issue Supplies charges the center"]$pk$::jsonb,
    $pk$[{"mistake": "I approved the wrong job / cost center.", "solution": "Cancel unused DRAFT requisitions. After issue, reverse stock with a manager correction referencing the original consumption.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Issue Supplies on the floor reads these centers for budget clearance.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/security',
    'Settings',
    'Tenant Security',
    $pk$Dedicated security hub — password rules, SSO, and Desktop Idle Timeout for shared office PCs.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Review idle timeout", "Confirm SSO and MFA policy", "Save"]$pk$::jsonb,
    $pk$[{"mistake": "Desktop idle lock is too aggressive and people share PINs.", "solution": "Raise the timeout on this page. Never share passwords — disable a user instead. Sharing a session breaks attribution when something needs a reversal.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Idle lock protects the ledger: the next person must sign in as themselves.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/integrations',
    'Settings',
    'Integrations Hub',
    $pk$Hub that routes into e-commerce, accounting, and operations integration surfaces.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Pick the connector category", "Follow the shortcut into the matching Settings tab"]$pk$::jsonb,
    $pk$[{"mistake": "Webhook sync failure.", "solution": "Retry / outbox replay on the connector card, then confirm LIVE. Disable a connector to stop inbound events.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$The hub itself does not mutate stock.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/roles',
    'Settings',
    'Roles & Permissions',
    $pk$Custom RBAC matrix — which role may approve POs, void invoices, override discounts, or adjust inventory.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Open the permissions matrix", "Grant or revoke a capability", "Save"]$pk$::jsonb,
    $pk$[{"mistake": "I locked out a user (or myself) from every screen.", "solution": "A remaining Administrator restores the role here. If every Admin is locked out, Super Admin restores access from the control plane. Do not share Owner passwords as a workaround.", "requiredRole": "OWNER"}]$pk$::jsonb,
    $pk$Least privilege first. A Picker with Adjust Inventory can silently rewrite the floor.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/automations',
    'Settings',
    'Automations',
    $pk$Business rule triggers. Same content as Settings → Automations tab.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Review rules", "Disable a rogue toggle"]$pk$::jsonb,
    $pk$[{"mistake": "Rogue automation created duplicate work.", "solution": "Disable the toggle, then cancel the extra POs/orders. Automations only create work; they never erase history.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Test one rule at a time during a quiet hour.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/printers',
    'Settings',
    'Workstation Printers',
    $pk$Thermal Zebra / QZ Tray workstation printers for labels and manifests.$pk$,
    $pk$Administrators and Warehouse Managers.$pk$,
    $pk$["Add a printer name and IP / QZ Tray target", "Mark the default for this workstation", "Print a test label"]$pk$::jsonb,
    $pk$[{"mistake": "The printer is offline.", "solution": "Switch to the fallback IP or another workstation printer. Do not handwritten-guess a tracking number. Reprint after the device is LIVE.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$A wrong default printer is how packing labels land in the office.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/retail-pos',
    'Settings',
    'Retail POS',
    $pk$Same register configuration as Settings → Retail POS (receipts, CFDI, blind closeout).$pk$,
    $pk$Owners and Administrators with the Retail POS addon.$pk$,
    $pk$["Set currency", "Edit receipt copy", "Toggle blind closeout", "Save POS settings"]$pk$::jsonb,
    $pk$[{"mistake": "Register cash variance at end of shift.", "solution": "Recount. A manager posts the variance. Do not rewrite WMS invoices to hide a drawer miss.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Blind closeout is a register guardrail, separate from warehouse blind cycle counts.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/warehouses',
    'Settings',
    'Warehouses',
    $pk$Facility management, zones, bins, and aisle setup — same domain as Settings → Warehouses.$pk$,
    $pk$Owners and Administrators.$pk$,
    $pk$["Add a warehouse", "Edit bins and aisles", "Assign users"]$pk$::jsonb,
    $pk$[{"mistake": "I set up bins in the wrong building.", "solution": "Create the bins on the correct warehouse and transfer stock. Do not rename a live warehouse to 'fix' it.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Pick pathing, RTLS, and replenishment all depend on this layout.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/profile',
    'Settings',
    'Profile settings',
    $pk$Dedicated profile page for the signed-in user (same domain as Settings → Profile).$pk$,
    $pk$Every signed-in user.$pk$,
    $pk$["Update personal details", "Save and return"]$pk$::jsonb,
    $pk$[{"mistake": "I saved the wrong display name.", "solution": "Edit again. No inventory impact.", "requiredRole": "ANY"}]$pk$::jsonb,
    $pk$Users tab remains the place for role and location access changes.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/billing',
    'Settings',
    'Billing',
    $pk$Owner-scoped subscription and plan management for the tenant.$pk$,
    $pk$Owners (and sometimes Administrators).$pk$,
    $pk$["Review the current plan and seats", "Change plan in the billing portal when needed"]$pk$::jsonb,
    $pk$[{"mistake": "I changed plans by accident.", "solution": "Confirm in the billing portal — downgrades may wait until period end. Billing changes do not reverse warehouse transactions.", "requiredRole": "OWNER"}]$pk$::jsonb,
    $pk$Only Owners should open this hub.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/fintech',
    'Settings',
    'Cash Flow & Financing',
    $pk$Owner-scoped capital credit lines and financing insights tied to AR/AP. Changes when cash arrives — not what stock exists.$pk$,
    $pk$Owners only.$pk$,
    $pk$["Review offers and status", "Follow only the on-screen connect/confirm steps"]$pk$::jsonb,
    $pk$[{"mistake": "I started a financing connect flow I did not mean to.", "solution": "Stop before the final confirm. If you completed something in error, contact the provider from this page. This is contractual, not a ledger edit.", "requiredRole": "OWNER"}]$pk$::jsonb,
    $pk$If this page is forbidden, you are not the Owner — that is the control working.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/mesh-network',
    'Platform',
    'Mesh Network',
    $pk$Cross-tenant mesh inventory sourcing network — discover partners, handshake, and pull smart-sourcing suggestions.$pk$,
    $pk$Owners and Administrators (Mesh Network module).$pk$,
    $pk$["Open Mesh Network from the top-level nav.", "Discover or accept a partner handshake.", "Map SKUs under Settings → Partner Catalog."]$pk$::jsonb,
    $pk$[{"mistake": "I connected the wrong partner tenant.", "solution": "Disconnect the handshake. Historical mesh POs stay. Do not ship another company's freight to 'make it right'.", "requiredRole": "OWNER"}]$pk$::jsonb,
    $pk$Buyers never see your bin map through mesh — only catalog availability you chose to share.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/mesh',
    'Platform',
    'Mesh Network',
    $pk$Cross-tenant mesh inventory sourcing network — discover partners, handshake, and pull smart-sourcing suggestions.$pk$,
    $pk$Owners and Administrators (Mesh Network module).$pk$,
    $pk$["Open Mesh Network from the top-level nav.", "Discover or accept a partner handshake.", "Map SKUs under Settings → Partner Catalog."]$pk$::jsonb,
    $pk$[{"mistake": "I connected the wrong partner tenant.", "solution": "Disconnect the handshake. Historical mesh POs stay. Do not ship another company's freight to 'make it right'.", "requiredRole": "OWNER"}]$pk$::jsonb,
    $pk$Buyers never see your bin map through mesh — only catalog availability you chose to share.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/reports',
    'Platform',
    'Reports',
    $pk$Executive financial valuation, COGS, turnover, fulfillment, labor, and inventory audit analytics. Reports are read-only — reverse underlying transactions on operational pages.$pk$,
    $pk$Owners and Administrators. Viewers as permitted.$pk$,
    $pk$["Open the analysis board you need.", "Filter by date / warehouse.", "Export or screenshot for leadership packs."]$pk$::jsonb,
    $pk$[{"mistake": "Numbers look stale.", "solution": "Finish pending Approve Ledger Adjustment and Sync Conflict decisions first. Reports never rewrite counts.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$Headline KPIs may refresh on a short delay rather than updating every second.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/import',
    'Settings',
    'Import wizard',
    $pk$Bulk-load products via mapped CSV/Excel with preflight validation (READY TO IMPORT, MISSING PRODUCT, VALIDATION ERROR).$pk$,
    $pk$Administrators and Owners.$pk$,
    $pk$["Download the template.", "Map columns and run preflight.", "Click Import N ready row(s) only when rows are green."]$pk$::jsonb,
    $pk$[{"mistake": "I imported duplicates or typo'd names.", "solution": "Edit the product or retire the twin. Imports do not set stock levels — quantity still comes from receive, count, and corrections.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Never force red preflight rows. Fix the file.$pk$,
    'flyway-seed'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
) VALUES (
    '/settings/import',
    'Settings',
    'Import wizard',
    $pk$Bulk-load products via mapped CSV/Excel with preflight validation (READY TO IMPORT, MISSING PRODUCT, VALIDATION ERROR).$pk$,
    $pk$Administrators and Owners.$pk$,
    $pk$["Download the template.", "Map columns and run preflight.", "Click Import N ready row(s) only when rows are green."]$pk$::jsonb,
    $pk$[{"mistake": "I imported duplicates or typo'd names.", "solution": "Edit the product or retire the twin. Imports do not set stock levels — quantity still comes from receive, count, and corrections.", "requiredRole": "ADMIN"}]$pk$::jsonb,
    $pk$Never force red preflight rows. Fix the file.$pk$,
    'flyway-seed'
);
