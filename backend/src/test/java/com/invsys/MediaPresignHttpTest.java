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
import com.invsys.repository.TransactionMediaRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MediaPresignHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired TransactionMediaRepository transactionMediaRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void presignPutCompleteAvatarProductAndTransactionMedia() throws Exception {
        String slug = "psn-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Presign Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();
        String auth = "Bearer " + tokens.accessToken();

        JsonNode avatarPresign = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/media/presign-upload")
                                .param("type", "USER_AVATAR")
                                .param("filename", "me.png")
                                .param("contentType", "image/png")
                                .header("Authorization", auth))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.uploadUrl").exists())
                        .andExpect(jsonPath("$.objectKey").value(org.hamcrest.Matchers.startsWith(tenantId + "/")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());

        putBytes(avatarPresign.path("uploadUrl").asString(),
                avatarPresign.path("contentType").asString(),
                TestImages.PNG_1X1);

        String avatarContentUrl = objectMapper.readTree(
                        mockMvc.perform(post("/api/v1/media/complete")
                                        .header("Authorization", auth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"objectKey":"%s","contentType":"image/png"}
                                                """.formatted(avatarPresign.path("objectKey").asString())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.contentUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/media/")))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("contentUrl").asString();

        mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"" + avatarContentUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(avatarContentUrl));

        TokenResponse login = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));
        assertThat(login.avatarUrl()).isEqualTo(avatarContentUrl);

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PSN");
        product.setName("Presign Product");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PSN-1");
        variant.setBarcode("9900555566661");
        variant = variantRepository.save(variant);
        UUID variantId = variant.getId();
        TenantContext.clear();

        JsonNode productPresign = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/media/presign-upload")
                                .param("type", "PRODUCT")
                                .param("filename", "sku.png")
                                .param("contentType", "image/png")
                                .header("Authorization", auth))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        putBytes(productPresign.path("uploadUrl").asString(),
                productPresign.path("contentType").asString(),
                TestImages.PNG_1X1);

        String productUrl = objectMapper.readTree(
                        mockMvc.perform(post("/api/v1/media/complete")
                                        .header("Authorization", auth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"objectKey":"%s","contentType":"image/png"}
                                                """.formatted(productPresign.path("objectKey").asString())))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("contentUrl").asString();

        mockMvc.perform(post("/api/v1/products/variants/" + variantId + "/media")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"%s","isPrimary":true}
                                """.formatted(productUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));

        TenantContext.setTenantId(tenantId);
        assertThat(outboxEventRepository.findAll()).anyMatch(e ->
                "PRODUCT_MEDIA_UPDATED".equals(e.getEventType()) && variantId.equals(e.getAggregateId()));
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/scan/9900555566661")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMediaUrl").value(productUrl));

        JsonNode txnPresign = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/media/presign-upload")
                                .param("type", "TRANSACTION")
                                .param("filename", "qc.png")
                                .param("contentType", "image/png")
                                .header("Authorization", auth))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        putBytes(txnPresign.path("uploadUrl").asString(),
                txnPresign.path("contentType").asString(),
                TestImages.PNG_1X1);

        String txnUrl = objectMapper.readTree(
                        mockMvc.perform(post("/api/v1/media/complete")
                                        .header("Authorization", auth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"objectKey":"%s","contentType":"image/png"}
                                                """.formatted(txnPresign.path("objectKey").asString())))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("contentUrl").asString();

        mockMvc.perform(post("/api/v1/media/transactions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"RECEIPT","entityId":"%s","url":"%s"}
                                """.formatted(variantId, txnUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("RECEIPT"))
                .andExpect(jsonPath("$.url").value(txnUrl));

        TenantContext.setTenantId(tenantId);
        assertThat(transactionMediaRepository
                .findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId, "RECEIPT", variantId))
                .hasSize(1);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/media/presign-upload")
                        .param("type", "NOT_A_TYPE")
                        .param("filename", "x.png")
                        .param("contentType", "image/png")
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    private void putBytes(String uploadUrl, String contentType, byte[] bytes) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(uploadUrl))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }
}
