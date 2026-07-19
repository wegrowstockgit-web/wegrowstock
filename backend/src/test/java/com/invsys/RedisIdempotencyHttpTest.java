package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.idempotency.RedisIdempotencyFilter;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RedisIdempotencyHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository lineRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void duplicateIdempotencyKeyReturnsCachedResponseWithoutDoubleReceive() throws Exception {
        String slug = "idem-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Idem Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Dock");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("IDEM");
        product.setName("Idem Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("IDEM-1");
        variant.setBarcode("8901999000099");
        variant.setAbcClassification("C");
        variant.setStorageTempZone("AMBIENT");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH");
        Location bin = loc(tenantId, wh.getId(), "BIN", "B-IDEM", "/WH/B-IDEM");

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-IDEM-" + slug);
        po.setStatus("IN_TRANSIT");
        po.setDestinationLocationId(wh.getId());
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyReceived(BigDecimal.ZERO);
        line.setUnitCost(new BigDecimal("1.00"));
        line = lineRepository.save(line);

        String idemKey = UUID.randomUUID().toString();
        String body = """
                {"lineId":"%s","quantity":3,"scannedLocationBarcode":"%s"}
                """.formatted(line.getId(), bin.getCode());

        mockMvc.perform(post("/api/v1/inbound/receive/confirm")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityChange").value(3))
                .andExpect(jsonPath("$.action").value("PO_RECEIPT"));

        MvcResult replay = mockMvc.perform(post("/api/v1/inbound/receive/confirm")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(RedisIdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.quantityChange").value(3))
                .andReturn();

        assertThat(replay.getResponse().getContentAsString()).contains("PO_RECEIPT");

        TenantContext.setTenantId(tenantId);
        PurchaseOrderLine refreshed = lineRepository.findById(line.getId()).orElseThrow();
        assertThat(refreshed.getQtyReceived()).isEqualByComparingTo("3");
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
