package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.domain.Lot;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.LotRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InternalLotMintHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired LotRepository lotRepository;
    @Autowired TenantSettingsRepository tenantSettingsRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mintThenReceiveBindsInternalLotOnLedger() throws Exception {
        String slug = "mint-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Mint Lot", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSettingsRepository.save(TenantSettings.withDefaults(tenantId)));
        Map<String, Object> updated = new HashMap<>(settings.getSettings());
        updated.put("allow_blind_receiving", true);
        settings.setSettings(updated);
        tenantSettingsRepository.save(settings);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("MINT");
        product.setName("Mint Lot Product");
        product = productRepository.save(product);

        ProductVariant variantDraft = new ProductVariant();
        variantDraft.setTenantId(tenantId);
        variantDraft.setProductId(product.getId());
        variantDraft.setSku("MINT-1");
        variantDraft.setBarcode("01999999999999");
        variantDraft.setLotTracked(true);
        final ProductVariant variant = variantRepository.save(variantDraft);

        Location whDraft = new Location();
        whDraft.setTenantId(tenantId);
        whDraft.setType("WAREHOUSE");
        whDraft.setCode("WH-MINT");
        whDraft.setName("Mint WH");
        whDraft.setPath("/WH-MINT");
        final Location wh = locationRepository.save(whDraft);
        TenantContext.clear();

        MvcResult mintResult = mockMvc.perform(post("/api/v1/inventory/lots/mint")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variant.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value(org.hamcrest.Matchers.startsWith("INT-")))
                .andExpect(jsonPath("$.zpl").value(org.hamcrest.Matchers.containsString("^XA")))
                .andExpect(jsonPath("$.variantId").value(variant.getId().toString()))
                .andReturn();

        String body = mintResult.getResponse().getContentAsString();
        final String lotNumber = body.replaceAll("(?s).*\"lotNumber\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        final String lotId = body.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode":"01999999999999",
                                  "warehouseId":"%s",
                                  "mode":"receive",
                                  "lotNumber":"%s",
                                  "quantity":3
                                }
                                """.formatted(wh.getId(), lotNumber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLotTracked").value(true));

        TenantContext.setTenantId(tenantId);
        final Lot lot = lotRepository.findById(UUID.fromString(lotId)).orElseThrow();
        assertThat(lot.getLotNumber()).isEqualTo(lotNumber);
        assertThat(lot.getVariantId()).isEqualTo(variant.getId());

        boolean bound = ledgerRepository.findAll().stream()
                .anyMatch(e -> tenantId.equals(e.getTenantId())
                        && variant.getId().equals(e.getVariantId())
                        && lot.getId().equals(e.getLotId())
                        && "RECEIVE".equals(e.getMovementType()));
        assertThat(bound).isTrue();
    }

    @Test
    void mintRejectsWhenVariantNotLotTracked() throws Exception {
        String slug = "nmint-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "No Mint", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("NM");
        product.setName("No Mint");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("NM-1");
        variant.setBarcode("01888888888888");
        variant.setLotTracked(false);
        variant = variantRepository.save(variant);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/inventory/lots/mint")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variant.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_LOT_TRACKED"));
    }
}
