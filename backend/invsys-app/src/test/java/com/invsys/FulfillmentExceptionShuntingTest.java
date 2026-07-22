package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.fulfillment.domain.FulfillmentException;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.fulfillment.repository.FulfillmentExceptionRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FulfillmentExceptionShuntingTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired FulfillmentExceptionRepository exceptionRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reportShuntsAllocationWithoutLedgerWriteAndReleasesHold() throws Exception {
        String slug = "exsh-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Exception Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(owner.userId());

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("EXSH");
        product.setName("Exception SKU");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("EXSH-1");
        variant.setBarcode("EXSH-1");
        variant.setLotTracked(true);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-EX");
        wh.setName("WH");
        wh.setPath("/WH-EX");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("5"), "SEED", null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Cust");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-EX-1");
        order.setStatus("ALLOCATED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line = salesOrderLineRepository.save(line);

        Allocation allocation = new Allocation();
        allocation.setTenantId(tenantId);
        allocation.setSalesOrderLineId(line.getId());
        allocation.setVariantId(variant.getId());
        allocation.setLocationId(wh.getId());
        allocation.setQuantity(new BigDecimal("2"));
        allocation.setStatus("ACTIVE");
        allocation.setAssignedToUserId(owner.userId());
        allocation = allocationRepository.save(allocation);

        BigDecimal allocatedBefore = levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                .stream()
                .map(InventoryLevel::getAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocatedBefore).isEqualByComparingTo("2");

        long ledgerBefore = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId())
                .size();

        TenantContext.clear();
        mockMvc.perform(post("/api/v1/fulfillment/exceptions/report")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allocationId":"%s","metadata":{"reason":"DAMAGED_BARCODE"}}
                                """.formatted(allocation.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationStatus").value("EXCEPTION_DAMAGED_BARCODE"))
                .andExpect(jsonPath("$.resolutionStatus").value("OPEN"));

        TenantContext.setTenantId(tenantId);
        Allocation updated = allocationRepository.findById(allocation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("EXCEPTION_DAMAGED_BARCODE");
        assertThat(updated.getAssignedToUserId()).isNull();

        BigDecimal allocatedAfter = levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                .stream()
                .map(InventoryLevel::getAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocatedAfter).isEqualByComparingTo("0");

        List<InventoryLedger> ledgerAfter = ledgerRepository
                .findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId());
        assertThat(ledgerAfter).hasSize((int) ledgerBefore);

        List<FulfillmentException> exceptions = exceptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.getFirst().getAllocationId()).isEqualTo(allocation.getId());

        mockMvc.perform(get("/api/v1/office/exceptions/list")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resolutionStatus").value("OPEN"));

        mockMvc.perform(post("/api/v1/office/exceptions/" + exceptions.getFirst().getId() + "/resolve")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLEAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionStatus").value("RESOLVED"));

        TenantContext.setTenantId(tenantId);
        assertThat(allocationRepository.findById(allocation.getId()).orElseThrow().getStatus())
                .isEqualTo("ACTIVE");
    }
}
