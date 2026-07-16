package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Allocation;
import com.invsys.domain.Location;
import com.invsys.domain.OfflineSyncConflict;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.OfflineSyncConflictRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OfflineSyncConflictHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired OfflineSyncConflictRepository conflictRepository;
    @Autowired AllocationRepository allocationRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void offlineReplayBusinessFailureReturns202AndSinksConflict() throws Exception {
        String slug = "osc-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "OSC Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("OSC");
        product.setName("OSC");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("OSC-1");
        variant.setBarcode("9900000111112");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-OSC");
        wh.setName("OSC WH");
        wh.setPath("/WH-OSC");
        wh = locationRepository.save(wh);
        Allocation consumed = new Allocation();
        consumed.setTenantId(tenantId);
        consumed.setVariantId(variant.getId());
        consumed.setLocationId(wh.getId());
        consumed.setQuantity(BigDecimal.ONE);
        consumed.setStatus("CONSUMED");
        consumed = allocationRepository.save(consumed);
        TenantContext.clear();

        String body = """
                {
                  "barcode": "9900000111112",
                  "warehouseId": "%s",
                  "mode": "pick",
                  "quantity": 1,
                  "allocationId": "%s"
                }
                """.formatted(wh.getId(), consumed.getId());

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", "osc-" + UUID.randomUUID())
                        .header("X-Offline-Replay", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.conflictId").exists());

        TenantContext.setTenantId(tenantId);
        assertThat(conflictRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).isNotEmpty();
        OfflineSyncConflict conflict = conflictRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).getFirst();
        assertThat(conflict.getStatus()).isEqualTo("PENDING");

        mockMvc.perform(get("/api/v1/offline-sync-conflicts")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(conflict.getId().toString()));

        mockMvc.perform(post("/api/v1/offline-sync-conflicts/" + conflict.getId() + "/dismiss")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }
}
