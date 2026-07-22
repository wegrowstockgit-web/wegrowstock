package com.invsys;

import com.invsys.core.security.AuthCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.service.MeshCatalogService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeshCatalogHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired BootstrapJdbc bootstrapJdbc;
    @Autowired MeshCatalogService meshCatalogService;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void partnerCatalogMappingsRoundTrip() throws Exception {
        String buyerSlug = "meshbuy-" + UUID.randomUUID().toString().substring(0, 8);
        String buyerEmail = "owner@" + buyerSlug + ".test";
        TokenResponse buyerTokens = authService.signup(new SignupRequest(
                "Mesh Buy Co", buyerSlug, buyerEmail, "password123", "Owner"));
        UUID buyerTenant = buyerTokens.tenantId();

        String sellerSlug = "meshsell-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse sellerTokens = authService.signup(new SignupRequest(
                "Mesh Sell Co", sellerSlug, "owner@" + sellerSlug + ".test", "password123", "Owner"));
        UUID sellerTenant = sellerTokens.tenantId();

        TenantContext.setTenantId(buyerTenant);
        Supplier supplier = new Supplier();
        supplier.setTenantId(buyerTenant);
        supplier.setName("Seller Mirror");
        supplier = supplierRepository.save(supplier);
        Product buyerProduct = new Product();
        buyerProduct.setTenantId(buyerTenant);
        buyerProduct.setSkuRoot("BUY");
        buyerProduct.setName("Buyer Item");
        buyerProduct = productRepository.save(buyerProduct);
        ProductVariant buyerVariant = new ProductVariant();
        buyerVariant.setTenantId(buyerTenant);
        buyerVariant.setProductId(buyerProduct.getId());
        buyerVariant.setSku("BUY-1");
        buyerVariant = productVariantRepository.save(buyerVariant);

        TenantContext.setTenantId(sellerTenant);
        Customer customer = new Customer();
        customer.setTenantId(sellerTenant);
        customer.setName("Buyer Cust");
        customer = customerRepository.save(customer);
        Product sellerProduct = new Product();
        sellerProduct.setTenantId(sellerTenant);
        sellerProduct.setSkuRoot("SELL");
        sellerProduct.setName("Seller Item");
        sellerProduct = productRepository.save(sellerProduct);
        ProductVariant sellerVariant = new ProductVariant();
        sellerVariant.setTenantId(sellerTenant);
        sellerVariant.setProductId(sellerProduct.getId());
        sellerVariant.setSku("SELL-1");
        UUID sellerVariantId = productVariantRepository.save(sellerVariant).getId();

        bootstrapJdbc.upsertMeshPartner(buyerTenant, sellerTenant, supplier.getId(), customer.getId(), "CONNECTED");

        TenantContext.setTenantId(buyerTenant);
        List<MeshCatalogService.CatalogMappingRow> mapped = meshCatalogService.upsertMappings(
                sellerTenant,
                List.of(new MeshCatalogService.MappingUpsert(buyerVariant.getId(), sellerVariantId)));
        assertThat(mapped).anySatisfy(row -> {
            assertThat(row.localSku()).isEqualTo("BUY-1");
            assertThat(row.partnerSku()).isEqualTo("SELL-1");
            assertThat(row.partnerVariantId()).isEqualTo(sellerVariantId);
        });
        TenantContext.clear();

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", buyerEmail,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = login.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);

        mockMvc.perform(get("/api/v1/settings/mesh/partners").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].partnerTenantId").value(sellerTenant.toString()));

        mockMvc.perform(get("/api/v1/settings/mesh/partners/" + sellerTenant + "/mappings").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partnerSku").value("SELL-1"));
    }
}
