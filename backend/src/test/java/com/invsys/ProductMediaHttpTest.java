package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.media.TestImages;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.ScanService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    @Autowired ObjectMapper objectMapper;

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

        MockMultipartFile avatar = new MockMultipartFile(
                "file", "avatar.png", "image/png", TestImages.PNG_1X1);
        String avatarUrl = objectMapper.readTree(
                        mockMvc.perform(multipart("/api/v1/users/me/avatar/upload")
                                        .file(avatar)
                                        .header("Authorization", "Bearer " + tokens.accessToken()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/media/")))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("avatarUrl").asString();

        TokenResponse refreshed = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));
        assertThat(refreshed.avatarUrl()).isEqualTo(avatarUrl);

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

        MockMultipartFile productImage = new MockMultipartFile(
                "file", "med-1.png", "image/png", TestImages.PNG_1X1);
        String mediaUrl = objectMapper.readTree(
                        mockMvc.perform(multipart("/api/v1/products/variants/" + variantId + "/media/upload")
                                        .file(productImage)
                                        .param("primary", "true")
                                        .header("Authorization", "Bearer " + tokens.accessToken()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isPrimary").value(true))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("url").asString();
        assertThat(mediaUrl).startsWith("/api/v1/media/");

        TenantContext.setTenantId(tenantId);
        boolean outboxQueued = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "PRODUCT_MEDIA_UPDATED".equals(e.getEventType())
                        && variantId.equals(e.getAggregateId()));
        assertThat(outboxQueued).isTrue();

        var scan = scanService.lookup("9900333344441");
        assertThat(scan.primaryMediaUrl()).isEqualTo(mediaUrl);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/scan/9900333344441")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMediaUrl").value(mediaUrl));

        mockMvc.perform(get("/api/v1/variants")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.sku=='MED-1')].primaryMediaUrl")
                        .value(org.hamcrest.Matchers.hasItem(mediaUrl)));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(avatarUrl))
                .andExpect(jsonPath("$.displayName").value("Owner"))
                .andExpect(jsonPath("$.roles").isArray());

        String mediaId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/products/variants/" + variantId + "/media")
                                        .header("Authorization", "Bearer " + tokens.accessToken()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get(0).path("id").asString();

        mockMvc.perform(put("/api/v1/products/variants/" + variantId + "/media/" + mediaId + "/primary")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));

        String mediaObjectId = mediaUrl.replace("/api/v1/media/", "").replace("/content", "");
        mockMvc.perform(delete("/api/v1/media/" + mediaObjectId)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/scan/9900333344441")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMediaUrl").value(org.hamcrest.Matchers.nullValue()));
    }
}
