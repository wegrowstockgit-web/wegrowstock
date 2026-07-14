package com.invsys;

import com.invsys.common.ApiException;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.service.PurchaseOrderService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverReceiptToleranceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired TenantSettingsRepository tenantSettingsRepository;
    @Autowired PurchaseOrderService purchaseOrderService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void allowsOverReceiptWithinToleranceAndRejectsBeyond() {
        UUID tenantId = testDataHelper.createTenant("Tolerance Co", "tol-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSettingsRepository.save(TenantSettings.withDefaults(tenantId)));
        java.util.Map<String, Object> updated = new java.util.LinkedHashMap<>(settings.getSettings());
        updated.put("over_receipt_tolerance_percent", 10);
        settings.setSettings(updated);
        tenantSettingsRepository.save(settings);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("TOL");
        product.setName("Tolerance Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("TOL-1");
        variant = variantRepository.save(variant);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("BIN");
        location.setCode("BIN-TOL");
        location.setName("Tolerance Bin");
        location.setPath("/BIN-TOL");
        location = locationRepository.save(location);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Tolerance Supplier");
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-TOL-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("100"));
        line.setQtyReceived(BigDecimal.ZERO);
        line.setUnitCost(new BigDecimal("2.00"));
        line = purchaseOrderLineRepository.save(line);

        PurchaseOrderLine within = purchaseOrderService.receiveLine(
                line.getId(), location.getId(), null, new BigDecimal("50"));
        assertThat(within.getQtyReceived()).isEqualByComparingTo("50");

        UUID lineId = line.getId();
        UUID locationId = location.getId();
        // remaining=50, maxAllowed=55 with 10% tolerance
        assertThatThrownBy(() -> purchaseOrderService.receiveLine(
                lineId, locationId, null, new BigDecimal("60")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(api.getCode()).isEqualTo("OVER_RECEIPT_TOLERANCE");
                });

        PurchaseOrderLine overWithinTolerance = purchaseOrderService.receiveLine(
                lineId, locationId, null, new BigDecimal("55"));
        assertThat(overWithinTolerance.getQtyReceived()).isEqualByComparingTo("105");
        assertThat(overWithinTolerance.getQtyOrdered()).isEqualByComparingTo("100");
    }
}
