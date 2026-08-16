package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.modules.catalog.domain.Lot;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LotRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.fintech.domain.FactoredInvoice;
import com.invsys.modules.fintech.repository.FactoredInvoiceRepository;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.TenantSubscriptionService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GlobalSearchHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired FactoredInvoiceRepository factoredInvoiceRepository;
    @Autowired LicensePlateRepository licensePlateRepository;
    @Autowired LotRepository lotRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerSearchIsCategorizedAndMeExposesTier() throws Exception {
        Fixture fx = seedTenant("gs-own");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("ENTERPRISE"));

        MvcResult result = mockMvc.perform(get("/api/v1/search/global")
                        .param("q", "ZXQ")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(categories(body)).contains("Catalog", "Customer", "Invoice", "Purchase Order", "Lot", "LPN");
        assertThat(titles(body)).contains("ZXQ Widget", "ZXQ Buyer", "INV-ZXQ-1", "PO-ZXQ-1", "LOT-ZXQ-1", "LPN-ZXQ-1");
    }

    @Test
    void pickerCannotSeeFinanceOrPartners() throws Exception {
        Fixture fx = seedTenant("gs-pk");
        TokenResponse picker = invitePicker(fx.owner, fx.slug);

        MvcResult result = mockMvc.perform(get("/api/v1/search/global")
                        .param("q", "ZXQ")
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(categories(body)).contains("Catalog", "Lot", "LPN");
        assertThat(categories(body)).doesNotContain("Invoice", "Customer", "Supplier", "Purchase Order", "Sales Order");
    }

    @Test
    void fintechAndB2bDomainsAreModuleGated() throws Exception {
        Fixture fx = seedTenant("gs-mod");

        MvcResult enabled = mockMvc.perform(get("/api/v1/search/global")
                        .param("q", "ZXQ")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(categories(objectMapper.readTree(enabled.getResponse().getContentAsString())))
                .contains("Factored Invoice", "B2B Order");

        tenantSubscriptionService.replaceEnabledModules(fx.owner.tenantId(), List.of(AppModule.CORE));

        MvcResult disabled = mockMvc.perform(get("/api/v1/search/global")
                        .param("q", "ZXQ")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(disabled.getResponse().getContentAsString());
        assertThat(categories(body)).doesNotContain("Factored Invoice", "B2B Order");
        assertThat(titles(body)).doesNotContain("SO-PORTAL-ZXQ");
    }

    @Test
    void shortQueryAndUnauthenticatedAreRejectedSafely() throws Exception {
        Fixture fx = seedTenant("gs-q");
        mockMvc.perform(get("/api/v1/search/global")
                        .param("q", "Z")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/v1/search/global").param("q", "ZXQ"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selfServiceCanPersistPreferredLanguage() throws Exception {
        Fixture fx = seedTenant("gs-lang");
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + fx.owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLanguage":"es-MX"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localeLanguage").value("es"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localeLanguage").value("es"));
    }

    private Fixture seedTenant(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Search Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("ZXQ");
        product.setName("ZXQ Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("ZXQ-SKU-1");
        variant.setBarcode("ZXQ-BC-1");
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("ZXQ Buyer");
        customer.setEmail("buyer@" + slug + ".test");
        customer = customerRepository.save(customer);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("ZXQ Supplier");
        supplier = supplierRepository.save(supplier);

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(customer.getId());
        invoice.setNumber("INV-ZXQ-1");
        invoice.setStatus("OPEN");
        invoice.setSubtotal(new BigDecimal("10.00"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("10.00"));
        invoice.setCurrency("USD");
        invoice = invoiceRepository.save(invoice);

        FactoredInvoice factored = new FactoredInvoice();
        factored.setTenantId(tenantId);
        factored.setInvoiceId(invoice.getId());
        factored.setEscrowPayoutRef("ZXQ-ESCROW");
        factoredInvoiceRepository.save(factored);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-ZXQ-1");
        po.setStatus("DRAFT");
        purchaseOrderRepository.save(po);

        SalesOrder portal = new SalesOrder();
        portal.setTenantId(tenantId);
        portal.setCustomerId(customer.getId());
        portal.setNumber("SO-PORTAL-ZXQ");
        portal.setChannel("PORTAL");
        salesOrderRepository.save(portal);

        LicensePlate lpn = new LicensePlate();
        lpn.setTenantId(tenantId);
        lpn.setLpnBarcode("LPN-ZXQ-1");
        licensePlateRepository.save(lpn);

        Lot lot = new Lot();
        lot.setTenantId(tenantId);
        lot.setVariantId(variant.getId());
        lot.setLotNumber("LOT-ZXQ-1");
        lot.setReceivedAt(Instant.now());
        lotRepository.save(lot);

        TenantContext.clear();
        return new Fixture(slug, owner);
    }

    private TokenResponse invitePicker(TokenResponse owner, String slug) throws Exception {
        MvcResult inviteMvc = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode inviteJson = objectMapper.readTree(inviteMvc.getResponse().getContentAsString());
        String token = inviteJson.get("token").asString();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Picker One","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());
        return authService.login(new LoginRequest("picker@" + slug + ".test", "password123"));
    }

    private static List<String> categories(JsonNode body) {
        return fieldValues(body, "category");
    }

    private static List<String> titles(JsonNode body) {
        return fieldValues(body, "title");
    }

    private static List<String> fieldValues(JsonNode body, String field) {
        List<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            values.add(body.get(i).get(field).asString());
        }
        return values;
    }

    private record Fixture(String slug, TokenResponse owner) {
    }
}
