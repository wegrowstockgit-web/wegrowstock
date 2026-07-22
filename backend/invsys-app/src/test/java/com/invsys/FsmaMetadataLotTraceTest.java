package com.invsys;

import com.invsys.api.dto.ComplianceLotTraceResponse;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.InventoryGenealogyService;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.invsys.modules.catalog.domain.Lot;

/**
 * FSMA §204: when lot tracking is disabled, AI 10 / lot strings sink to
 * vendor_lot_captured metadata and remain recall-searchable.
 */
@AutoConfigureMockMvc
class FsmaMetadataLotTraceTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryGenealogyService genealogyService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void metadataCapturedLotRemainsTraceableViaComplianceApi() throws Exception {
        String slug = "fsma-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Fsma Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("FSMA");
        product.setName("Untracked Lot SKU");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("FSMA-1");
        variant.setLotTracked(false);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-FSMA");
        wh.setName("FSMA WH");
        wh.setPath("/WH-FSMA");
        wh = locationRepository.save(wh);

        Location bin = new Location();
        bin.setTenantId(tenantId);
        bin.setParentLocationId(wh.getId());
        bin.setType("BIN");
        bin.setCode("B1");
        bin.setName("B1");
        bin.setPath("/WH-FSMA/B1");
        bin = locationRepository.save(bin);

        inventoryService.receive(
                variant.getId(),
                bin.getId(),
                null,
                "VENDOR-LOT-FSMA-99",
                new BigDecimal("8"),
                "SEED",
                null,
                null,
                null,
                null);

        ComplianceLotTraceResponse trace = genealogyService.complianceTrace(null, "VENDOR-LOT-FSMA-99");
        assertThat(trace.lotId()).isNull();
        assertThat(trace.lotNumber()).isEqualTo("VENDOR-LOT-FSMA-99");
        assertThat(trace.variantId()).isEqualTo(variant.getId());
        assertThat(trace.origin()).isNotNull();
        assertThat(trace.origin().quantity()).isEqualByComparingTo("8");

        TenantContext.clear();
        mockMvc.perform(get("/api/v1/compliance/lot-trace")
                        .param("lotNumber", "VENDOR-LOT-FSMA-99")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value("VENDOR-LOT-FSMA-99"))
                .andExpect(jsonPath("$.sku").value("FSMA-1"))
                .andExpect(jsonPath("$.origin.quantity").value(8));
    }
}
