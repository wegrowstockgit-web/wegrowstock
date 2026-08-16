package com.invsys.service;

import com.invsys.api.dto.SearchResultDto;
import com.invsys.core.security.PermissionKeys;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.repository.UserRoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-isolated global search. Domain queries are gated by commercial modules
 * and RBAC; EntityManager is not thread-safe, so authorized domains run on the
 * request thread against the RLS-bound connection.
 */
@Service
public class GlobalSearchService {

    private static final int PER_DOMAIN = 8;
    private static final int MIN_QUERY_LEN = 2;

    @PersistenceContext
    private EntityManager entityManager;

    private final TenantSubscriptionService tenantSubscriptionService;
    private final RolePermissionService rolePermissionService;
    private final UserRoleRepository userRoleRepository;

    public GlobalSearchService(TenantSubscriptionService tenantSubscriptionService,
                               RolePermissionService rolePermissionService,
                               UserRoleRepository userRoleRepository) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.rolePermissionService = rolePermissionService;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public List<SearchResultDto> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY_LEN) {
            return List.of();
        }

        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = resolveRoles(authentication, userId);
        List<String> granted = rolePermissionService.resolveGrantedPermissions(tenantId, List.copyOf(roles));
        Set<String> permissions = new LinkedHashSet<>(granted);
        List<AppModule> modules = tenantSubscriptionService.getEnabledModules(tenantId);

        boolean ownerOrAdmin = hasRole(roles, "OWNER", "ADMIN");
        boolean warehouseOps = hasRole(roles, "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER");
        boolean pickerOnly = hasRole(roles, "PICKER")
                && !hasRole(roles, "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "VIEWER");
        boolean b2bOnly = hasRole(roles, "B2B_CUSTOMER")
                && !hasRole(roles, "OWNER", "ADMIN");

        boolean canReadSales = !pickerOnly && !b2bOnly;
        boolean canReadPartners = !pickerOnly && !b2bOnly;
        boolean canReadProcurement = ownerOrAdmin
                || hasRole(roles, "WAREHOUSE_MANAGER")
                || permissions.contains(PermissionKeys.PURCHASING_PO_APPROVE);
        boolean canReadFinance = ownerOrAdmin
                || permissions.contains(PermissionKeys.INVENTORY_COST_VIEW)
                || permissions.contains(PermissionKeys.SALES_INVOICE_VOID);
        boolean b2bEnabled = modules.contains(AppModule.B2B_SHOWROOM);
        boolean fintechEnabled = modules.contains(AppModule.FINTECH);

        String like = likePattern(query);
        List<SearchResultDto> results = new ArrayList<>();

        searchCatalog(results, tenantId, like);

        if (canReadSales) {
            searchSalesOrders(results, tenantId, like, b2bEnabled);
        }
        if (canReadProcurement) {
            searchPurchaseOrders(results, tenantId, like);
        }
        if (canReadPartners) {
            searchCustomers(results, tenantId, like);
            searchSuppliers(results, tenantId, like);
        }
        if (canReadFinance) {
            searchInvoices(results, tenantId, like);
            if (fintechEnabled) {
                searchFactoredInvoices(results, tenantId, like);
            }
        }
        if (b2bEnabled && (canReadSales || b2bOnly)) {
            searchB2bOrders(results, tenantId, like);
        }
        if (warehouseOps && !b2bOnly) {
            searchLots(results, tenantId, like);
            searchSerials(results, tenantId, like);
            searchLpns(results, tenantId, like);
            searchLocations(results, tenantId, like);
        }

        return List.copyOf(results);
    }

    private void searchCatalog(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(pv.id AS text), pv.sku, p.name, COALESCE(pv.barcode, '')
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                WHERE pv.tenant_id = :tenantId
                  AND p.deleted_at IS NULL
                  AND (pv.sku ILIKE :q ESCAPE '\\'
                       OR p.name ILIKE :q ESCAPE '\\'
                       OR COALESCE(pv.barcode, '') ILIKE :q ESCAPE '\\'
                       OR EXISTS (
                            SELECT 1 FROM variant_barcodes vb
                            WHERE vb.variant_id = pv.id AND vb.barcode ILIKE :q ESCAPE '\\'
                       ))
                ORDER BY pv.sku
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            String sku = str(row[1]);
            String name = str(row[2]);
            String barcode = str(row[3]);
            results.add(new SearchResultDto(
                    "Catalog",
                    blankTo(name, sku),
                    joinSubtitle(sku, barcode),
                    "/products",
                    null));
        }
    }

    private void searchSalesOrders(List<SearchResultDto> results, UUID tenantId, String like, boolean b2bEnabled) {
        String channelFilter = b2bEnabled ? "" : "AND so.channel <> 'PORTAL'";
        String sql = """
                SELECT CAST(so.id AS text), so.number, COALESCE(c.name, ''), so.status
                FROM sales_orders so
                LEFT JOIN customers c ON c.id = so.customer_id
                WHERE so.tenant_id = :tenantId
                  %s
                  AND (so.number ILIKE :q ESCAPE '\\'
                       OR COALESCE(c.name, '') ILIKE :q ESCAPE '\\'
                       OR COALESCE(so.customer_po_number, '') ILIKE :q ESCAPE '\\')
                ORDER BY so.number DESC
                LIMIT %d
                """.formatted(channelFilter, PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Sales Order",
                    str(row[1]),
                    joinSubtitle(str(row[2]), str(row[3])),
                    "/sales-orders",
                    null));
        }
    }

    private void searchB2bOrders(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(so.id AS text), so.number, COALESCE(c.name, ''), so.status
                FROM sales_orders so
                LEFT JOIN customers c ON c.id = so.customer_id
                WHERE so.tenant_id = :tenantId
                  AND so.channel = 'PORTAL'
                  AND (so.number ILIKE :q ESCAPE '\\' OR COALESCE(c.name, '') ILIKE :q ESCAPE '\\')
                ORDER BY so.number DESC
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "B2B Order",
                    str(row[1]),
                    joinSubtitle(str(row[2]), str(row[3])),
                    "/sales-orders",
                    null));
        }
    }

    private void searchPurchaseOrders(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(po.id AS text), po.number, COALESCE(s.name, ''), po.status
                FROM purchase_orders po
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                WHERE po.tenant_id = :tenantId
                  AND (po.number ILIKE :q ESCAPE '\\' OR COALESCE(s.name, '') ILIKE :q ESCAPE '\\')
                ORDER BY po.number DESC
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Purchase Order",
                    str(row[1]),
                    joinSubtitle(str(row[2]), str(row[3])),
                    "/purchase-orders",
                    PermissionKeys.PURCHASING_PO_APPROVE));
        }
    }

    private void searchCustomers(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(c.id AS text), c.name, COALESCE(c.email, '')
                FROM customers c
                WHERE c.tenant_id = :tenantId
                  AND (c.name ILIKE :q ESCAPE '\\' OR COALESCE(c.email, '') ILIKE :q ESCAPE '\\')
                ORDER BY c.name
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Customer",
                    str(row[1]),
                    str(row[2]),
                    "/customers",
                    null));
        }
    }

    private void searchSuppliers(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(s.id AS text), s.name, COALESCE(s.contact->>'email', '')
                FROM suppliers s
                WHERE s.tenant_id = :tenantId
                  AND (s.name ILIKE :q ESCAPE '\\'
                       OR COALESCE(s.contact->>'email', '') ILIKE :q ESCAPE '\\')
                ORDER BY s.name
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Supplier",
                    str(row[1]),
                    str(row[2]),
                    "/suppliers",
                    null));
        }
    }

    private void searchInvoices(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(i.id AS text), i.number, COALESCE(c.name, ''), i.status
                FROM invoices i
                LEFT JOIN customers c ON c.id = i.customer_id
                WHERE i.tenant_id = :tenantId
                  AND (i.number ILIKE :q ESCAPE '\\' OR COALESCE(c.name, '') ILIKE :q ESCAPE '\\')
                ORDER BY i.number DESC
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Invoice",
                    str(row[1]),
                    joinSubtitle(str(row[2]), str(row[3])),
                    "/invoices",
                    PermissionKeys.SALES_INVOICE_VOID));
        }
    }

    private void searchFactoredInvoices(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(fi.id AS text), i.number, fi.funding_status
                FROM factored_invoices fi
                JOIN invoices i ON i.id = fi.invoice_id
                WHERE fi.tenant_id = :tenantId
                  AND (i.number ILIKE :q ESCAPE '\\'
                       OR COALESCE(fi.escrow_payout_ref, '') ILIKE :q ESCAPE '\\')
                ORDER BY i.number DESC
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Factored Invoice",
                    str(row[1]),
                    str(row[2]),
                    "/settings/fintech",
                    PermissionKeys.SALES_INVOICE_VOID));
        }
    }

    private void searchLots(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(l.id AS text), l.lot_number, COALESCE(pv.sku, '')
                FROM lots l
                LEFT JOIN product_variants pv ON pv.id = l.variant_id
                WHERE l.tenant_id = :tenantId
                  AND (l.lot_number ILIKE :q ESCAPE '\\' OR COALESCE(pv.sku, '') ILIKE :q ESCAPE '\\')
                ORDER BY l.lot_number
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Lot",
                    str(row[1]),
                    str(row[2]),
                    "/compliance/lot-trace",
                    null));
        }
    }

    private void searchSerials(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(sn.id AS text), sn.serial_number, COALESCE(pv.sku, '')
                FROM serial_numbers sn
                LEFT JOIN product_variants pv ON pv.id = sn.variant_id
                WHERE sn.tenant_id = :tenantId
                  AND sn.serial_number ILIKE :q ESCAPE '\\'
                ORDER BY sn.serial_number
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "Serial",
                    str(row[1]),
                    str(row[2]),
                    "/products",
                    null));
        }
    }

    private void searchLpns(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(lp.id AS text), lp.lpn_barcode, lp.status
                FROM license_plates lp
                WHERE lp.tenant_id = :tenantId
                  AND lp.lpn_barcode ILIKE :q ESCAPE '\\'
                ORDER BY lp.lpn_barcode
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            results.add(new SearchResultDto(
                    "LPN",
                    str(row[1]),
                    str(row[2]),
                    "/fulfillment",
                    null));
        }
    }

    private void searchLocations(List<SearchResultDto> results, UUID tenantId, String like) {
        String sql = """
                SELECT CAST(loc.id AS text), loc.name, loc.code, loc.type
                FROM locations loc
                WHERE loc.tenant_id = :tenantId
                  AND (loc.name ILIKE :q ESCAPE '\\'
                       OR loc.code ILIKE :q ESCAPE '\\'
                       OR loc.path ILIKE :q ESCAPE '\\')
                ORDER BY loc.path
                LIMIT %d
                """.formatted(PER_DOMAIN);
        for (Object[] row : nativeRows(sql, tenantId, like)) {
            String type = str(row[3]);
            String category = "ZONE".equalsIgnoreCase(type) ? "Zone" : "Location";
            results.add(new SearchResultDto(
                    category,
                    str(row[1]),
                    joinSubtitle(str(row[2]), type),
                    "/settings",
                    null));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> nativeRows(String sql, UUID tenantId, String like) {
        List<?> raw = entityManager.createNativeQuery(sql)
                .setParameter("tenantId", tenantId)
                .setParameter("q", like)
                .getResultList();
        List<Object[]> rows = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Object[] array) {
                rows.add(array);
            } else {
                rows.add(new Object[]{item});
            }
        }
        return rows;
    }

    private Set<String> resolveRoles(Authentication authentication, UUID userId) {
        Set<String> roles = new LinkedHashSet<>();
        if (authentication != null) {
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            if (authorities != null) {
                for (GrantedAuthority authority : authorities) {
                    String value = authority.getAuthority();
                    if (value != null && value.startsWith("ROLE_")) {
                        roles.add(value.substring("ROLE_".length()));
                    } else if (value != null && !value.isBlank()) {
                        roles.add(value);
                    }
                }
            }
        }
        roles.addAll(userRoleRepository.findRoleCodesByUserId(userId));
        return roles;
    }

    private static boolean hasRole(Set<String> roles, String... needed) {
        for (String role : needed) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    static String likePattern(String query) {
        String escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankTo(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static String joinSubtitle(String left, String right) {
        boolean leftBlank = left == null || left.isBlank();
        boolean rightBlank = right == null || right.isBlank();
        if (leftBlank && rightBlank) {
            return "";
        }
        if (leftBlank) {
            return right;
        }
        if (rightBlank) {
            return left;
        }
        return left + " · " + right;
    }
}
