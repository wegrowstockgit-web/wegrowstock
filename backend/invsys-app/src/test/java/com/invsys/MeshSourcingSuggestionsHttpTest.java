package com.invsys;

import com.invsys.core.security.AuthCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.BinReplenishmentRule;
import com.invsys.domain.MeshCatalogListing;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.repository.BinReplenishmentRuleRepository;
import com.invsys.repository.MeshCatalogListingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeshSourcingSuggestionsHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired BootstrapJdbc bootstrapJdbc;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLevelRepository inventoryLevelRepository;
    @Autowired BinReplenishmentRuleRepository binReplenishmentRuleRepository;
    @Autowired MeshCatalogListingRepository listingRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void getMeshSourcingSuggestionsFindsPublishedPartnerSkuBelowReorder() throws Exception {
        String buyerSlug = "srcb-" + UUID.randomUUID().toString().substring(0, 8);
        String sellerSlug = "srcs-" + UUID.randomUUID().toString().substring(0, 8);
        String buyerEmail = "owner@" + buyerSlug + ".test";

        TokenResponse buyerTokens = authService.signup(new SignupRequest(
                "Sourcing Buyer", buyerSlug, buyerEmail, "password123", "Owner"));
        TokenResponse sellerTokens = authService.signup(new SignupRequest(
                "Sourcing Seller", sellerSlug, "owner@" + sellerSlug + ".test", "password123", "Owner"));
        UUID buyer = buyerTokens.tenantId();
        UUID seller = sellerTokens.tenantId();

        TenantContext.setTenantId(buyer);
        Product buyerProduct = product(buyer, "SRC", "Low Stock Widget");
        ProductVariant buyerVariant = variant(buyer, buyerProduct.getId(), "SHARED-SKU", "BC-SHARED");
        Location bin = location(buyer);
        InventoryLevel level = new InventoryLevel();
        level.setTenantId(buyer);
        level.setVariantId(buyerVariant.getId());
        level.setLocationId(bin.getId());
        level.setOnHand(new BigDecimal("2"));
        level.setAllocated(BigDecimal.ZERO);
        inventoryLevelRepository.save(level);
        BinReplenishmentRule rule = new BinReplenishmentRule();
        rule.setTenantId(buyer);
        rule.setLocationId(bin.getId());
        rule.setVariantId(buyerVariant.getId());
        rule.setMinQuantity(new BigDecimal("10"));
        rule.setMaxQuantity(new BigDecimal("40"));
        binReplenishmentRuleRepository.save(rule);
        Supplier supplier = new Supplier();
        supplier.setTenantId(buyer);
        supplier.setName("Sourcing Seller");
        supplier = supplierRepository.save(supplier);

        TenantContext.setTenantId(seller);
        Product sellerProduct = product(seller, "SRCS", "Partner Widget");
        ProductVariant sellerVariant = variant(seller, sellerProduct.getId(), "SHARED-SKU", "BC-SHARED");
        MeshCatalogListing listing = new MeshCatalogListing();
        listing.setTenantId(seller);
        listing.setVariantId(sellerVariant.getId());
        listing.setPublished(true);
        listing.setMeshWholesalePrice(new BigDecimal("12.00"));
        listingRepository.save(listing);
        Customer customer = new Customer();
        customer.setTenantId(seller);
        customer.setName("Sourcing Buyer");
        customer = customerRepository.save(customer);

        bootstrapJdbc.upsertMeshPartner(buyer, seller, supplier.getId(), customer.getId(), "CONNECTED");

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", buyerEmail,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = login.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);

        mockMvc.perform(get("/api/v1/dashboard/mesh-sourcing-suggestions").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Low Stock Widget"))
                .andExpect(jsonPath("$[0].partnerName").value("Sourcing Seller"))
                .andExpect(jsonPath("$[0].meshPartnerSku").value("SHARED-SKU"))
                .andExpect(jsonPath("$[0].supplierId").value(supplier.getId().toString()));
    }

    private Product product(UUID tenantId, String root, String name) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(root);
        product.setName(name);
        return productRepository.save(product);
    }

    private ProductVariant variant(UUID tenantId, UUID productId, String sku, String barcode) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        variant.setBarcode(barcode);
        return productVariantRepository.save(variant);
    }

    private Location location(UUID tenantId) {
        Location loc = new Location();
        loc.setTenantId(tenantId);
        loc.setType("BIN");
        loc.setCode("MESH-BIN");
        loc.setName("Mesh bin");
        loc.setPath("WH/MESH-BIN");
        return locationRepository.save(loc);
    }
}
