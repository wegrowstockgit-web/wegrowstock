package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.fulfillment.service.AllocationService;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.PickingWaveService;
import com.invsys.support.SupportChatService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guardrail: core inventory lifecycle must run with the chatbot feature disabled
 * (Support Co-Pilot beans absent) and without security/runtime regressions.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "invsys.features.chatbot.enabled=false")
class CoreModuleWithoutChatbotIT extends AbstractIntegrationTest {

    @Autowired ApplicationContext applicationContext;
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired AllocationService allocationService;
    @Autowired InventoryService inventoryService;
    @Autowired PickingWaveService pickingWaveService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void coreLifecycleWorksWithoutSupportChatServiceBean() throws Exception {
        assertThat(applicationContext.getBeanNamesForType(SupportChatService.class)).isEmpty();

        String slug = "nocb-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "No Chatbot Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("NCB");
        product.setName("Core Lifecycle SKU");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("NCB-1");
        variant.setBarcode("8800111122223");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-NCB");
        wh.setName("No Chatbot WH");
        wh.setPath("/WH-NCB");
        wh = locationRepository.save(wh);

        // Receive
        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("5"), "SEED", null);
        List<InventoryLedger> receiveLedgers =
                ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId());
        assertThat(receiveLedgers).isNotEmpty();
        assertThat(receiveLedgers.stream().anyMatch(l -> "RECEIVE".equals(l.getMovementType()))).isTrue();

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Lifecycle Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setStatus("CONFIRMED");
        order.setNumber("SO-NCB-1");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line.setQtyAllocated(BigDecimal.ZERO);
        line.setQtyShipped(BigDecimal.ZERO);
        line = salesOrderLineRepository.save(line);

        // Allocate
        List<Allocation> created = allocationService.allocate(line, List.of(wh.getId()));
        assertThat(created).isNotEmpty();

        PickingWaveService.WaveResult wave = pickingWaveService.generateWave(null, null);
        pickingWaveService.releaseWave(wave.wave().getId());
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/picking/waves/" + wave.wave().getId() + "/claim")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationsClaimed").value(org.hamcrest.Matchers.greaterThan(0)));

        // Pick
        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"8800111122223","warehouseId":"%s","mode":"pick","allocationId":"%s"}
                                """.formatted(wh.getId(), created.getFirst().getId())))
                .andExpect(status().isOk());

        // Ship
        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"salesOrderId":"%s","carrier":"UPS","trackingNumber":"1ZNCB","lines":[{"salesOrderLineId":"%s","quantity":2}]}
                                """.formatted(order.getId(), line.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        TenantContext.setTenantId(tenantId);
        SalesOrderLine shipped = salesOrderLineRepository.findById(line.getId()).orElseThrow();
        assertThat(shipped.getQtyShipped()).isEqualByComparingTo("2");

        List<InventoryLedger> allLedgers =
                ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId());
        assertThat(allLedgers).isNotEmpty();
        assertThat(allLedgers.stream().map(InventoryLedger::getMovementType).distinct())
                .contains("RECEIVE");

        // Support API must not be mapped when chatbot is disabled
        mockMvc.perform(post("/api/v1/support/chat")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isNotFound());
    }
}
