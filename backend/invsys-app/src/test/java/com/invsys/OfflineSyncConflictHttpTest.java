package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.OfflineSyncConflict;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.repository.AllocationRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.OfflineSyncConflictRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.invsys.domain.ConflictActionType;

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

        mockMvc.perform(get("/api/v1/offline-sync-conflicts")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schemaMetadata").isArray())
                .andExpect(jsonPath("$[0].humanSummary").exists())
                .andExpect(jsonPath("$[0].actionType").value("OUTBOUND_PICK"));

        mockMvc.perform(post("/api/v1/offline-sync-conflicts/" + conflict.getId() + "/dismiss")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
    }

    @Test
    void resolveConflictReplaysAsManagerOverride() throws Exception {
        String slug = "osc-r-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "OSC Resolve", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(owner.userId());

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("OSCR");
        product.setName("OSCR");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("OSCR-1");
        variant.setBarcode("9900000222223");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-OSCR");
        wh.setName("OSCR WH");
        wh.setPath("/WH-OSCR");
        wh = locationRepository.save(wh);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("mode", "receive");
        body.put("barcode", "9900000222223");
        body.put("quantity", 2);
        body.put("warehouseId", wh.getId().toString());

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("method", "POST");
        payload.put("url", "/api/v1/fulfillment/scan");
        payload.put("body", body);

        OfflineSyncConflict parked = new OfflineSyncConflict();
        parked.setTenantId(tenantId);
        parked.setPickerUserId(owner.userId());
        parked.setActionType(com.invsys.domain.ConflictActionType.INBOUND_RECEIVE);
        parked.setRequestUrl("/api/v1/fulfillment/scan");
        parked.setErrorMessage("BIN_FULL: allocated bin location is full");
        parked.setStatus(OfflineSyncConflict.STATUS_PENDING);
        parked.setPayload(payload);
        parked.setSchemaMetadata(List.of(
                Map.of(
                        "key", "quantity",
                        "label", "Corrected Quantity Count",
                        "type", "number",
                        "mutable", true,
                        "constraints", Map.of("min", 1)),
                Map.of(
                        "key", "barcode",
                        "label", "Scanned Item Master GTIN",
                        "type", "string",
                        "mutable", false,
                        "constraints", Map.of())));
        parked = conflictRepository.save(parked);
        UUID conflictId = parked.getId();
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/offline-sync-conflicts/" + conflictId + "/resolve")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "corrections": { "quantity": 3 } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_AND_REPLAYED"))
                .andExpect(jsonPath("$.resolvedByUserId").value(owner.userId().toString()))
                .andExpect(jsonPath("$.humanSummary").exists());

        TenantContext.setTenantId(tenantId);
        OfflineSyncConflict resolved = conflictRepository.findByTenantIdAndId(tenantId, conflictId).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(OfflineSyncConflict.STATUS_RESOLVED_AND_REPLAYED);
        assertThat(resolved.getResolvedByUserId()).isEqualTo(owner.userId());
        assertThat(resolved.getResolvedAt()).isNotNull();
    }
}
