package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Product;
import com.invsys.repository.ProductRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductVariantEnterpriseHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void createVariantRequiresPositiveDimensions() throws Exception {
        String slug = "ent-dims-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Ent Dims Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        TenantContext.setTenantId(tokens.tenantId());

        Product product = new Product();
        product.setTenantId(tokens.tenantId());
        product.setSkuRoot("ENT");
        product.setName("Enterprise");
        product = productRepository.save(product);

        mockMvc.perform(post("/api/v1/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","sku":"ENT-NODIM","price":1.00,"currency":"USD"}
                                """.formatted(product.getId()))
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createVariantPersistsEnterpriseFields() throws Exception {
        String slug = "ent-full-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Ent Full Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        TenantContext.setTenantId(tokens.tenantId());

        Product product = new Product();
        product.setTenantId(tokens.tenantId());
        product.setSkuRoot("ENTF");
        product.setName("Enterprise Full");
        product = productRepository.save(product);

        String body = """
                {
                  "productId":"%s",
                  "sku":"ENTF-1",
                  "price":9.99,
                  "currency":"USD",
                  "weight":2.5,
                  "weightUnit":"lb",
                  "length":12,
                  "width":8,
                  "height":4,
                  "dimUnit":"in",
                  "hsTariffCode":"8471.30",
                  "countryOfOrigin":"US",
                  "isHazmat":true,
                  "palletTie":10,
                  "palletHigh":4,
                  "storageTempZone":"REFRIGERATED",
                  "isFragile":true,
                  "abcClassification":"A",
                  "lifecycleStatus":"ACTIVE"
                }
                """.formatted(product.getId());

        String response = mockMvc.perform(post("/api/v1/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hsTariffCode").value("8471.30"))
                .andExpect(jsonPath("$.hazmat").value(true))
                .andExpect(jsonPath("$.fragile").value(true))
                .andExpect(jsonPath("$.storageTempZone").value("REFRIGERATED"))
                .andExpect(jsonPath("$.abcClassification").value("A"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var node = objectMapper.readTree(response);
        assertThat(node.path("palletTie").asInt()).isEqualTo(10);
        assertThat(node.path("palletHigh").asInt()).isEqualTo(4);
        assertThat(node.path("countryOfOrigin").asString()).isEqualTo("US");
    }
}
