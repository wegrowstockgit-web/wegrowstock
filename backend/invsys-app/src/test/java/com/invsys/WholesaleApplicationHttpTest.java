package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.WholesaleApplication;
import com.invsys.modules.sales.domain.WholesaleApplicationStatus;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.CustomerUserMappingRepository;
import com.invsys.modules.sales.repository.WholesaleApplicationRepository;
import com.invsys.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WholesaleApplicationHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired WholesaleApplicationRepository applicationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerUserMappingRepository mappingRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publicApplyThenAdminApproveCreatesBuyerAndWelcomeLink() throws Exception {
        String slug = "ws-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Wholesale Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String buyerEmail = "buyer@" + slug + ".test";

        MvcResult applyResult = mockMvc.perform(post("/api/v1/showroom/apply")
                        .header("X-Tenant-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Northwind Growers",
                                  "taxId": "12-3456789",
                                  "contactName": "Ada Buyer",
                                  "email": "%s",
                                  "phone": "555-0100"
                                }
                                """.formatted(buyerEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.companyName").value("Northwind Growers"))
                .andExpect(jsonPath("$.email").value(buyerEmail))
                .andReturn();

        JsonNode applied = objectMapper.readTree(applyResult.getResponse().getContentAsString());
        String applicationId = applied.get("id").asString();

        mockMvc.perform(post("/api/v1/showroom/apply")
                        .header("X-Tenant-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Northwind Growers",
                                  "taxId": "12-3456789",
                                  "contactName": "Ada Buyer",
                                  "email": "%s"
                                }
                                """.formatted(buyerEmail)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/customers/applications").param("status", "PENDING")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(applicationId))
                .andExpect(jsonPath("$[0].taxId").value("12-3456789"));

        MvcResult approveResult = mockMvc.perform(post("/api/v1/customers/applications/" + applicationId + "/approve")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.customerId").isNotEmpty())
                .andExpect(jsonPath("$.magicToken").isNotEmpty())
                .andReturn();

        JsonNode approved = objectMapper.readTree(approveResult.getResponse().getContentAsString());
        UUID customerId = UUID.fromString(approved.get("customerId").asString());
        String magicToken = approved.get("magicToken").asString();

        TenantContext.setTenantId(owner.tenantId());
        WholesaleApplication stored = applicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(WholesaleApplicationStatus.APPROVED.name());

        Customer customer = customerRepository.findById(customerId).orElseThrow();
        assertThat(customer.getName()).isEqualTo("Northwind Growers");
        assertThat(customer.getTaxId()).isEqualTo("12-3456789");
        assertThat(customer.getPriceTierId()).isNotNull();

        var user = userRepository.findByTenantIdAndEmail(owner.tenantId(), buyerEmail).orElseThrow();
        CustomerUserMapping mapping = mappingRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(mapping.getCustomerId()).isEqualTo(customerId);

        mockMvc.perform(post("/api/v1/auth/magic-login/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + magicToken + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void guestCatalogHidesNothingButIsPublicAndApproveRequiresAuth() throws Exception {
        String slug = "cat-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Catalog Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("CAT");
        product.setName("Guest Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(owner.tenantId());
        variant.setProductId(product.getId());
        variant.setSku("CAT-1");
        variant.setPrice(new BigDecimal("19.50"));
        variantRepository.save(variant);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/showroom/catalog").header("X-Tenant-Slug", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("CAT-1"))
                .andExpect(jsonPath("$[0].unitPrice").value(19.50));

        mockMvc.perform(post("/api/v1/customers/applications/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applyRequiresTenantWhenMultipleExist() throws Exception {
        authService.signup(new SignupRequest(
                "A Co", "a-" + UUID.randomUUID().toString().substring(0, 8),
                "owner-a-" + UUID.randomUUID() + "@ex.test", "password123", "Owner"));
        authService.signup(new SignupRequest(
                "B Co", "b-" + UUID.randomUUID().toString().substring(0, 8),
                "owner-b-" + UUID.randomUUID() + "@ex.test", "password123", "Owner"));
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/showroom/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Orphan Co",
                                  "taxId": "99-9999999",
                                  "contactName": "No Slug",
                                  "email": "orphan-%s@ex.test"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}
