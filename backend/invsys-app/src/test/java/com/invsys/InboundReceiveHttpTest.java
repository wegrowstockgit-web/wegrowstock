package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InboundReceiveHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository lineRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mobileReceiveFlowWritesPoReceiptLedger() throws Exception {
        String slug = "ibr-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Inbound Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Dock Supplier");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("IBR");
        product.setName("Inbound Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("IBR-1");
        variant.setBarcode("8901999000001");
        variant.setAbcClassification("C");
        variant.setStorageTempZone("AMBIENT");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH");
        Location bin = loc(tenantId, wh.getId(), "BIN", "B-IN", "/WH/B-IN");

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-IBR-" + slug);
        po.setStatus("IN_TRANSIT");
        po.setDestinationLocationId(wh.getId());
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyReceived(BigDecimal.ZERO);
        line.setUnitCost(new BigDecimal("2.50"));
        line = lineRepository.save(line);

        mockMvc.perform(get("/api/v1/inbound/receive/po")
                        .param("barcode", po.getNumber())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(po.getNumber()))
                .andExpect(jsonPath("$.lines[0].sku").value("IBR-1"));

        mockMvc.perform(post("/api/v1/inbound/receive/resolve-item")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poId":"%s","barcode":"8901999000001"}
                                """.formatted(po.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineId").value(line.getId().toString()))
                .andExpect(jsonPath("$.qtyRemaining").value(10));

        MvcResult suggestion = mockMvc.perform(get("/api/v1/inbound/receive/putaway-suggestion")
                        .param("variantId", variant.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").exists())
                .andReturn();
        String binCode = objectMapper.readTree(suggestion.getResponse().getContentAsString())
                .get("code").asString();

        mockMvc.perform(post("/api/v1/inbound/receive/confirm")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineId":"%s","quantity":4,"scannedLocationBarcode":"%s"}
                                """.formatted(line.getId(), binCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("PO_RECEIPT"))
                .andExpect(jsonPath("$.quantityChange").value(4))
                .andExpect(jsonPath("$.poNumber").value(po.getNumber()));

        TenantContext.setTenantId(tenantId);
        List<InventoryLedger> ledgers = ledgerRepository.findByTenantIdAndReferenceTypeAndReferenceId(
                tenantId, "PURCHASE_ORDER", po.getId());
        assertThat(ledgers).isNotEmpty();
        assertThat(ledgers.getFirst().getMovementType()).isEqualTo("RECEIVE");
        assertThat(ledgers.getFirst().getReasonCode()).isEqualTo("PO_RECEIPT");
        assertThat(ledgers.getFirst().getQuantityDelta()).isEqualByComparingTo("4");

        PurchaseOrderLine refreshed = lineRepository.findById(line.getId()).orElseThrow();
        assertThat(refreshed.getQtyReceived()).isEqualByComparingTo("4");
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        location.setStorageTempZone("AMBIENT");
        return locationRepository.save(location);
    }
}
