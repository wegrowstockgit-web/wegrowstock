package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.media.TestImages;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MediaUploadHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void avatarUploadProductUploadContentAndSsrfBlocked() throws Exception {
        String slug = "upl-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Upload Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();

        MockMultipartFile avatar = new MockMultipartFile(
                "file", "avatar.png", "image/png", TestImages.PNG_1X1);

        MvcResult avatarResult = mockMvc.perform(multipart("/api/v1/users/me/avatar/upload")
                        .file(avatar)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/media/")))
                .andReturn();

        String avatarUrl = objectMapper.readTree(avatarResult.getResponse().getContentAsString())
                .path("avatarUrl").asString();
        mockMvc.perform(get(avatarUrl)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("UPL");
        product.setName("Upload Product");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("UPL-1");
        variant.setBarcode("9900444455551");
        variant = variantRepository.save(variant);
        UUID variantId = variant.getId();
        TenantContext.clear();

        MockMultipartFile productImage = new MockMultipartFile(
                "file", "product.png", "image/png", TestImages.PNG_1X1);
        mockMvc.perform(multipart("/api/v1/products/variants/" + variantId + "/media/upload")
                        .file(productImage)
                        .param("primary", "true")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/v1/media/")))
                .andExpect(jsonPath("$.isPrimary").value(true));

        MockMultipartFile evidence = new MockMultipartFile(
                "file", "qc.png", "image/png", TestImages.PNG_1X1);
        MvcResult evidenceResult = mockMvc.perform(multipart("/api/v1/media/uploads")
                        .file(evidence)
                        .param("kind", "EVIDENCE")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        String mediaId = objectMapper.readTree(evidenceResult.getResponse().getContentAsString())
                .path("id").asString();

        mockMvc.perform(post("/api/v1/media/attachments")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaObjectId":"%s","entityType":"PRODUCT_VARIANT","entityId":"%s","purpose":"QC_DAMAGE"}
                                """.formatted(mediaId, variantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("QC_DAMAGE"));

        // SSRF: loopback URL rejected on URL-attach path
        mockMvc.perform(post("/api/v1/products/variants/" + variantId + "/media")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://127.0.0.1/evil.png\",\"isPrimary\":false}"))
                .andExpect(status().isBadRequest());

        byte[] elf = new byte[64];
        elf[0] = 0x7F;
        elf[1] = 'E';
        elf[2] = 'L';
        elf[3] = 'F';
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "innocent.png", "image/png", elf);
        mockMvc.perform(multipart("/api/v1/media/uploads")
                        .file(disguised)
                        .param("kind", "EVIDENCE")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest());

        MockMultipartFile exe = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", "MZ-not-image".getBytes());
        mockMvc.perform(multipart("/api/v1/media/uploads")
                        .file(exe)
                        .param("kind", "EVIDENCE")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest());

        TokenResponse loggedIn = authService.login(
                new com.invsys.core.security.dto.LoginRequest("owner@" + slug + ".test", "password123"));
        assertThat(loggedIn.avatarUrl()).startsWith("/api/v1/media/");
    }
}
