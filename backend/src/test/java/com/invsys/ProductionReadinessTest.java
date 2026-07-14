package com.invsys;

import com.invsys.api.ForecastingController;
import com.invsys.api.PublicSupplierPortalController;
import com.invsys.auth.JwtService;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.Supplier;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.ForecastingService;
import com.invsys.service.ForecastingWorker;
import com.invsys.service.SupplierPortalService;
import com.invsys.service.TaxService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionReadinessTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired TaxService taxService;
    @Autowired ForecastingWorker forecastingWorker;
    @Autowired ForecastingController forecastingController;
    @Autowired SupplierPortalService supplierPortalService;
    @Autowired PublicSupplierPortalController publicSupplierPortalController;
    @Autowired JwtService jwtService;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired SupplierRepository supplierRepository;

    @BeforeEach
    void auth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner@test",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void taxCrudAndDefaultPayload() {
        UUID tenantId = testDataHelper.createTenant("Tax Co", "tax-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        taxService.create("State Tax", new BigDecimal("0.0700"), true);
        assertThat(taxService.list()).hasSize(1);
        assertThat(taxService.defaultTaxPayload()).containsEntry("name", "State Tax");
    }

    @Test
    void forecastingWorkerCalculatesWithoutNPlusOneFailure() {
        UUID tenantId = testDataHelper.createTenant("Forecast Co", "fc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        forecastingWorker.calculateForTenant(tenantId);

        TenantContext.setTenantId(tenantId);
        List<ForecastingService.ForecastAlert> alerts = forecastingController.alerts();
        assertThat(alerts).isNotNull();
    }

    @Test
    void supplierPortalMagicLinkIsJwtGated() {
        UUID tenantId = testDataHelper.createTenant("Portal Co", "portal-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Portal Supplier");
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-PORTAL-001");
        po.setStatus("DRAFT");
        po = purchaseOrderRepository.save(po);

        SupplierPortalService.MagicLinkResponse link = supplierPortalService.sendMagicLink(po.getId());
        assertThat(link.token()).isNotBlank();

        JwtService.SupplierPortalClaims claims = jwtService.validateSupplierPortalToken(link.token());
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.purchaseOrderId()).isEqualTo(po.getId());

        SupplierPortalService.PortalPurchaseOrderView view =
                publicSupplierPortalController.getPo(link.token());
        assertThat(view.number()).isEqualTo("PO-PORTAL-001");
    }
}
