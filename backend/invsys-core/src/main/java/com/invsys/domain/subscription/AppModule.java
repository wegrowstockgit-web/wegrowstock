package com.invsys.domain.subscription;

/**
 * Commercially gated application modules. Deployed code is always present;
 * access is toggled per tenant via {@code tenant_subscriptions.enabled_modules}.
 *
 * <p>Tier mapping:
 * <ul>
 *   <li>BASIC (Tier 1): {@link #CORE}</li>
 *   <li>INTERMEDIATE (Tier 2): CORE + Shopify, Accounting, Advanced Fulfillment,
 *       Manufacturing, Documents, MRP</li>
 *   <li>ENTERPRISE (Tier 3): all modules including B2B Showroom, Fintech, Mesh,
 *       RTLS, and AI Copilot</li>
 * </ul>
 */
public enum AppModule {
    /** Catalog, Inventory, Basic Orders, Basic Purchasing */
    CORE,

    /** {@code com.invsys.integration.shopify} */
    SHOPIFY,
    /** {@code com.invsys.integration.accounting} — QuickBooks / Xero */
    ACCOUNTING,
    /** Wave picking, cluster totes, cartonization */
    ADVANCED_FULFILLMENT,
    /** {@code com.invsys.domain.Bom} / manufacturing orders */
    MANUFACTURING,
    /** {@code com.invsys.documents} — PDF invoices / packing slips */
    DOCUMENTS,
    /** {@code com.invsys.service.MrpCalculationEngine} */
    MRP,

    /** {@code com.invsys.showroom} / B2B portal */
    B2B_SHOWROOM,
    /** {@code com.invsys.modules.fintech} */
    FINTECH,
    /** {@code com.invsys.mesh} */
    MESH_NETWORK,
    /** {@code com.invsys.rtls} */
    RTLS_TELEMETRY,
    /** {@code com.invsys.chatbot} / Support Co-Pilot */
    AI_COPILOT;

    public static AppModule fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("AppModule required");
        }
        return AppModule.valueOf(raw.trim().toUpperCase());
    }
}
