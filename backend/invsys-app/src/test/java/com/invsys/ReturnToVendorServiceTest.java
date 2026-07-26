package com.invsys;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.User;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.fulfillment.domain.FulfillmentException;
import com.invsys.modules.purchasing.domain.ApInvoiceIngestion;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.fulfillment.repository.FulfillmentExceptionRepository;
import com.invsys.modules.purchasing.repository.ApInvoiceIngestionRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.ReturnToVendorService;
import com.invsys.modules.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnToVendorServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ReturnToVendorService returnToVendorService;
    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired FulfillmentExceptionRepository exceptionRepository;
    @Autowired ApInvoiceIngestionRepository apInvoiceIngestionRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void createsApprovesAndShipsRtvFromException() {
        UUID tenantId = testDataHelper.createTenant("RTV", "rtv-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        User user = saveUser(tenantId);
        TenantContext.setUserId(user.getId());

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Acme Supply");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("RTV");
        product.setName("RTV Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("RTV-1");
        variant.setAvgCost(new BigDecimal("12.50"));
        variant = variantRepository.save(variant);

        Location bin = new Location();
        bin.setTenantId(tenantId);
        bin.setType("BIN");
        bin.setCode("RTV-BIN");
        bin.setName("RTV Bin");
        bin.setPath("/WH/RTV-BIN");
        bin = locationRepository.save(bin);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-RTV-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setTenantId(tenantId);
        poLine.setPurchaseOrderId(po.getId());
        poLine.setVariantId(variant.getId());
        poLine.setQtyOrdered(new BigDecimal("10"));
        poLine.setUnitCost(new BigDecimal("12.50"));
        purchaseOrderLineRepository.save(poLine);

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("5"), null, null);

        Allocation allocation = new Allocation();
        allocation.setTenantId(tenantId);
        allocation.setVariantId(variant.getId());
        allocation.setLocationId(bin.getId());
        allocation.setQuantity(new BigDecimal("2"));
        allocation.setStatus("EXCEPTION_DAMAGED_BARCODE");
        allocation = allocationRepository.save(allocation);

        FulfillmentException exception = new FulfillmentException();
        exception.setTenantId(tenantId);
        exception.setAllocationId(allocation.getId());
        exception.setReportedBy(user.getId());
        exception.setWarehouseId(bin.getId());
        exception.setResolutionStatus("OPEN");
        exception = exceptionRepository.save(exception);

        ReturnToVendorService.RtvDetail created = returnToVendorService.createRtvFromException(
                exception.getId(), "DEFECTIVE", new BigDecimal("2"), null, po.getId());

        assertThat(created.order().getStatus()).isEqualTo("DRAFT");
        assertThat(created.order().getNumber()).startsWith("RTV-");
        assertThat(created.order().getSupplierId()).isEqualTo(supplier.getId());
        assertThat(created.lines()).hasSize(1);
        assertThat(created.lines().getFirst().getUnitCost()).isEqualByComparingTo("12.50");

        returnToVendorService.approve(created.order().getId());
        ReturnToVendorService.RtvDetail shipped = returnToVendorService.shipRtv(
                created.order().getId(), "UPS", "1Z999");

        assertThat(shipped.order().getStatus()).isEqualTo("SHIPPED");
        assertThat(shipped.order().getDebitMemoNumber()).isEqualTo("DM-" + shipped.order().getNumber());
        assertThat(shipped.order().getTotalChargebackAmount()).isEqualByComparingTo("25.00");

        ApInvoiceIngestion ingestion = apInvoiceIngestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .filter(i -> "STAGED".equals(i.getIngestionStatus()))
                .findFirst()
                .orElseThrow();
        assertThat(ingestion.getMatchedPurchaseOrderId()).isEqualTo(po.getId());
        assertThat(ingestion.getParsedMetadata().get("debitMemoNumber")).isEqualTo(shipped.order().getDebitMemoNumber());
        assertThat(new BigDecimal(ingestion.getParsedMetadata().get("chargebackAmount").toString()))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void rejectsInvalidReasonCode() {
        UUID tenantId = testDataHelper.createTenant("RTV Bad", "rtvb-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        assertThatThrownBy(() -> returnToVendorService.createRtvFromException(
                UUID.randomUUID(), "BAD_REASON", BigDecimal.ONE, UUID.randomUUID(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private User saveUser(UUID tenantId) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail("rtv@" + UUID.randomUUID() + ".test");
        user.setDisplayName("RTV User");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }
}
