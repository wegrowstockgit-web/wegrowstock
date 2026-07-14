package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.ScanService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductMediaHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ScanService scanService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void avatarMediaAttachScanAndOutbox() throws Exception {
        String slug = "media-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Media Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();

        mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://cdn.example.com/avatars/owner.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example.com/avatars/owner.png"));

        TokenResponse refreshed = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));
        assertThat(refreshed.avatarUrl()).isEqualTo("https://cdn.example.com/avatars/owner.png");

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("MED");
        product.setName("Media Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("MED-1");
        variant.setBarcode("9900333344441");
        variant = variantRepository.save(variant);
        UUID variantId = variant.getId();
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/products/variants/" + variantId + "/media")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://cdn.example.com/products/med-1.jpg","isPrimary":true,"sortOrder":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://cdn.example.com/products/med-1.jpg"))
                .andExpect(jsonPath("$.isPrimary").value(true));

        TenantContext.setTenantId(tenantId);
        boolean outboxQueued = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "VARIANT_MEDIA_UPDATED".equals(e.getEventType())
                        && variantId.equals(e.getAggregateId()));
        assertThat(outboxQueued).isTrue();

        var scan = scanService.lookup("9900333344441");
        assertThat(scan.primaryMediaUrl()).isEqualTo("https://cdn.example.com/products/med-1.jpg");
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/scan/9900333344441")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMediaUrl").value("https://cdn.example.com/products/med-1.jpg"));

        mockMvc.perform(get("/api/v1/variants")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.sku=='MED-1')].primaryMediaUrl")
                        .value(org.hamcrest.Matchers.hasItem("https://cdn.example.com/products/med-1.jpg")));
    }
}
