package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Customer;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.service.AllocationService;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FulfillmentScanGs1HttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired TenantSettingsRepository tenantSettingsRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLevelRepository inventoryLevelRepository;
    @Autowired AllocationService allocationService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void receiveUsesClientParsedGs1QuantityWithoutReparsingComposite() throws Exception {
        String slug = "gs1r-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "GS1 Receive", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSettingsRepository.save(TenantSettings.withDefaults(tenantId)));
        Map<String, Object> updated = new HashMap<>(settings.getSettings());
        updated.put("allow_blind_receiving", true);
        settings.setSettings(updated);
        tenantSettingsRepository.save(settings);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("GS1R");
        product.setName("GS1 Receive Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("GS1R-1");
        variant.setBarcode("01234567890128");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-GS1R");
        wh.setName("GS1 Receive WH");
        wh.setPath("/WH-GS1R");
        wh = locationRepository.save(wh);
        TenantContext.clear();

        // Client already decoded GS1 — barcode is GTIN, quantity is AI 30.
        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode":"01234567890128",
                                  "warehouseId":"%s",
                                  "mode":"receive",
                                  "gtin":"01234567890128",
                                  "lotNumber":"LOT-GS1",
                                  "expiryDate":"2025-12-31",
                                  "quantity":5,
                                  "isGs1":true,
                                  "rawBarcode":"(01)01234567890128(10)LOT-GS1(17)251231(30)5"
                                }
                                """.formatted(wh.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("GS1R-1"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("5")));

        TenantContext.setTenantId(tenantId);
        BigDecimal onHand = inventoryLevelRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                .stream()
                .map(level -> level.getOnHand())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(onHand).isEqualByComparingTo("5");
    }

    @Test
    void pickConsumesClientParsedQuantity() throws Exception {
        String slug = "gs1p-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "GS1 Pick", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("GS1P");
        product.setName("GS1 Pick Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("GS1P-1");
        variant.setBarcode("9900111122223");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-GS1P");
        wh.setName("GS1 Pick WH");
        wh.setPath("/WH-GS1P");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"), "SEED", null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("GS1 Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setStatus("CONFIRMED");
        order.setNumber("SO-GS1-1");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("3"));
        line = salesOrderLineRepository.save(line);

        var allocations = allocationService.allocate(line, List.of(wh.getId()));
        assertThat(allocations).isNotEmpty();
        UUID allocationId = allocations.getFirst().getId();
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode":"9900111122223",
                                  "warehouseId":"%s",
                                  "mode":"pick",
                                  "allocationId":"%s",
                                  "gtin":"9900111122223",
                                  "quantity":3,
                                  "isGs1":true,
                                  "lotNumber":"A1",
                                  "expiryDate":"2026-01-15"
                                }
                                """.formatted(wh.getId(), allocationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3")));
    }
}
