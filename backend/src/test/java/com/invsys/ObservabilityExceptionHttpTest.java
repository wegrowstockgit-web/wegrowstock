package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level verification of RFC 7807 Problem Details + X-Request-Id enrichment.
 */
@AutoConfigureMockMvc
class ObservabilityExceptionHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void unauthenticatedRequestStillGetsRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Request-Id", "e2e-obs-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "e2e-obs-1"));
    }

    @Test
    void insufficientStockReturnsProblemDetailWithoutStackLeak() throws Exception {
        String slug = "obs-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Obs Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("OBS");
        product.setName("Obs Item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("OBS-1");
        variant.setBarcode("8901999000001");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-OBS");
        wh.setName("Obs WH");
        wh.setPath("/WH-OBS");
        wh = locationRepository.save(wh);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/inventory/adjust")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .header("X-Request-Id", "obs-stock-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variantId": "%s",
                                  "locationId": "%s",
                                  "delta": -999999,
                                  "reasonCode": "OBS_TEST"
                                }
                                """.formatted(variant.getId(), wh.getId())))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "obs-stock-1"))
                .andExpect(jsonPath("$.title").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString("Insufficient")))
                .andExpect(jsonPath("$.detail").value(not(containsString("at com.invsys"))))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void unknownApiPathReturnsProblemDetailsWithoutStackLeak() throws Exception {
        String slug = "obs404-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Obs 404 Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/this-route-does-not-exist-obs")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Request-Id", "obs-404-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "obs-404-1"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").value(not(containsString("at com.invsys"))));
    }
}
