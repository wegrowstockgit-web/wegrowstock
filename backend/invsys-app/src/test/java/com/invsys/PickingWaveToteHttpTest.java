package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PickingWaveToteHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void generateWaveAssignsToteIdentifiersPerSalesOrder() throws Exception {
        String slug = "tote-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Tote Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-T", "/WH-T");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-T/Z1");
        Location aisle = loc(tenantId, zone.getId(), "AISLE", "A1", "/WH-T/Z1/A1");
        Location bin = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH-T/Z1/A1/B1");
        bin.setCoordX(new BigDecimal("5"));
        bin.setCoordY(new BigDecimal("5"));
        bin = locationRepository.save(bin);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("TOTE");
        product.setName("Tote SKU");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("TOTE-1");
        variant.setBarcode("5500111100018");
        variant = variantRepository.save(variant);

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("20"), "SEED", null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Tote Buyer");
        customer = customerRepository.save(customer);

        SalesOrder so1 = so(tenantId, customer.getId(), "SO-T1");
        SalesOrder so2 = so(tenantId, customer.getId(), "SO-T2");
        SalesOrderLine line1 = line(tenantId, so1.getId(), variant.getId(), new BigDecimal("2"));
        SalesOrderLine line2 = line(tenantId, so2.getId(), variant.getId(), new BigDecimal("3"));

        alloc(tenantId, line1.getId(), variant.getId(), bin.getId(), new BigDecimal("2"));
        alloc(tenantId, line2.getId(), variant.getId(), bin.getId(), new BigDecimal("3"));
        TenantContext.clear();

        MvcResult result = mockMvc.perform(post("/api/v1/picking/waves/generate")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks[0].toteIdentifier").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Tote ");
        // Two distinct sales orders ⇒ at least A and B appear across tasks
        assertThat(body).containsPattern("Tote [AB]");
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        return locationRepository.save(location);
    }

    private SalesOrder so(UUID tenantId, UUID customerId, String number) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customerId);
        order.setNumber(number);
        order.setStatus("ALLOCATED");
        return salesOrderRepository.save(order);
    }

    private SalesOrderLine line(UUID tenantId, UUID soId, UUID variantId, BigDecimal qty) {
        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(soId);
        line.setVariantId(variantId);
        line.setQtyOrdered(qty);
        return salesOrderLineRepository.save(line);
    }

    private void alloc(UUID tenantId, UUID lineId, UUID variantId, UUID locationId, BigDecimal qty) {
        Allocation allocation = new Allocation();
        allocation.setTenantId(tenantId);
        allocation.setSalesOrderLineId(lineId);
        allocation.setVariantId(variantId);
        allocation.setLocationId(locationId);
        allocation.setQuantity(qty);
        allocation.setStatus("ACTIVE");
        allocationRepository.save(allocation);
    }
}
