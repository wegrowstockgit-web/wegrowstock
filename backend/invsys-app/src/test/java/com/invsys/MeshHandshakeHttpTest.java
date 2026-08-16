package com.invsys;

import com.invsys.core.security.AuthCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeshHandshakeHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapJdbc bootstrapJdbc;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void requestApproveCreatesSupplierAndCustomerAndDiscoverHidesPrice() throws Exception {
        String buyerSlug = "meshb-" + UUID.randomUUID().toString().substring(0, 8);
        String sellerSlug = "meshs-" + UUID.randomUUID().toString().substring(0, 8);
        String buyerEmail = "owner@" + buyerSlug + ".test";
        String sellerEmail = "owner@" + sellerSlug + ".test";

        TokenResponse buyerTokens = authService.signup(new SignupRequest(
                "Mesh Buyer Hub", buyerSlug, buyerEmail, "password123", "Owner"));
        TokenResponse sellerTokens = authService.signup(new SignupRequest(
                "Mesh Seller Hub", sellerSlug, sellerEmail, "password123", "Owner"));
        UUID buyerTenant = buyerTokens.tenantId();
        UUID sellerTenant = sellerTokens.tenantId();

        TenantContext.setTenantId(sellerTenant);
        Product sellerProduct = new Product();
        sellerProduct.setTenantId(sellerTenant);
        sellerProduct.setSkuRoot("MESHSELL");
        sellerProduct.setName("Partner Widget");
        sellerProduct = productRepository.save(sellerProduct);
        ProductVariant published = new ProductVariant();
        published.setTenantId(sellerTenant);
        published.setProductId(sellerProduct.getId());
        published.setSku("MESH-PUB-1");
        published.setBarcode("BC-MESH-1");
        published = productVariantRepository.save(published);
        ProductVariant hidden = new ProductVariant();
        hidden.setTenantId(sellerTenant);
        hidden.setProductId(sellerProduct.getId());
        hidden.setSku("MESH-HID-1");
        hidden = productVariantRepository.save(hidden);
        UUID publishedId = published.getId();
        UUID hiddenId = hidden.getId();
        TenantContext.clear();

        var sellerCookie = loginCookie(sellerEmail);
        mockMvc.perform(put("/api/v1/mesh/catalog/" + publishedId)
                        .cookie(sellerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "published", true,
                                "meshWholesalePrice", new BigDecimal("18.50")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
        mockMvc.perform(put("/api/v1/mesh/catalog/" + hiddenId)
                        .cookie(sellerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("published", false))))
                .andExpect(status().isOk());

        var buyerCookie = loginCookie(buyerEmail);
        mockMvc.perform(get("/api/v1/mesh/discover").cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.variantId=='" + publishedId + "')].productName")
                        .value("Partner Widget"))
                .andExpect(jsonPath("$[?(@.variantId=='" + publishedId + "')].sellerName")
                        .value("Mesh Seller Hub"))
                .andExpect(jsonPath("$[?(@.variantId=='" + publishedId + "')].meshWholesalePrice")
                        .doesNotExist())
                .andExpect(jsonPath("$[?(@.variantId=='" + publishedId + "')].price").doesNotExist())
                .andExpect(jsonPath("$[?(@.variantId=='" + publishedId + "')].stock").doesNotExist())
                .andExpect(jsonPath("$[?(@.variantId=='" + hiddenId + "')]").doesNotExist());

        mockMvc.perform(get("/api/v1/mesh/discover").cookie(sellerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.variantId=='" + publishedId + "')]").doesNotExist());

        var requestResult = mockMvc.perform(post("/api/v1/mesh/connections/request")
                        .cookie(buyerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("variantId", publishedId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("REQUESTED"))
                .andReturn();
        JsonNode requested = objectMapper.readTree(requestResult.getResponse().getContentAsString());
        UUID connectionId = UUID.fromString(requested.get("id").asText());
        assertThat(requested.get("supplierId") == null || requested.get("supplierId").isNull()).isTrue();
        assertThat(requested.get("customerId") == null || requested.get("customerId").isNull()).isTrue();

        mockMvc.perform(get("/api/v1/mesh/network").cookie(buyerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayStatus").value("REQUESTED"))
                .andExpect(jsonPath("$[0].canApprove").value(false));
        mockMvc.perform(get("/api/v1/mesh/network").cookie(sellerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].canApprove").value(true));

        mockMvc.perform(post("/api/v1/mesh/connections/" + connectionId + "/approve")
                        .cookie(buyerCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/mesh/connections/" + connectionId + "/approve")
                        .cookie(sellerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.supplierId").isNotEmpty())
                .andExpect(jsonPath("$.customerId").isNotEmpty());

        BootstrapJdbc.MeshPartnerRow row = bootstrapJdbc.findMeshPartnerById(connectionId).orElseThrow();
        assertThat(row.connectionStatus()).isEqualTo("CONNECTED");
        assertThat(row.supplierId()).isNotNull();
        assertThat(row.customerId()).isNotNull();

        TenantContext.setTenantId(buyerTenant);
        assertThat(supplierRepository.findById(row.supplierId())).isPresent()
                .get()
                .extracting(s -> s.getName())
                .isEqualTo("Mesh Seller Hub");
        TenantContext.setTenantId(sellerTenant);
        assertThat(customerRepository.findById(row.customerId())).isPresent()
                .get()
                .extracting(c -> c.getName())
                .isEqualTo("Mesh Buyer Hub");
    }

    private jakarta.servlet.http.Cookie loginCookie(String email) throws Exception {
        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
    }
}
