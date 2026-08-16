package com.invsys.core.security;

import java.util.List;

public final class PermissionKeys {

    public static final String INVENTORY_COST_VIEW = "inventory:cost:view";
    public static final String INVENTORY_ADJUST = "inventory:adjust";
    public static final String PURCHASING_PO_APPROVE = "purchasing:po:approve";
    public static final String SALES_INVOICE_VOID = "sales:invoice:void";
    public static final String SETTINGS_USERS_MANAGE = "settings:users:manage";
    public static final String FULFILLMENT_OVERRIDE = "fulfillment:override";
    public static final String RETURNS_QC_PROCESS = "returns:qc:process";
    public static final String MRP_RUN = "mrp:run";
    public static final String PRINTING_THERMAL = "printing:thermal";
    public static final String EDI_OUTBOUND = "edi:outbound";
    public static final String SO_DISCOUNT_OVERRIDE = "so:discount:override";
    public static final String MANAGE_CUSTOMERS = "customers:manage";

    public static final List<String> CATALOG = List.of(
            INVENTORY_COST_VIEW,
            INVENTORY_ADJUST,
            PURCHASING_PO_APPROVE,
            SALES_INVOICE_VOID,
            SETTINGS_USERS_MANAGE,
            FULFILLMENT_OVERRIDE,
            RETURNS_QC_PROCESS,
            MRP_RUN,
            PRINTING_THERMAL,
            EDI_OUTBOUND,
            SO_DISCOUNT_OVERRIDE,
            MANAGE_CUSTOMERS
    );

    private PermissionKeys() {
    }
}
