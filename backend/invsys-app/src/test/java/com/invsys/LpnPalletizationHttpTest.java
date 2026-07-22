package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LpnPalletizationHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired LicensePlateRepository licensePlateRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired InventoryService inventoryService;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mintPackAndShipByLpn() throws Exception {
        String slug = "pal-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Pallet Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PAL");
        product.setName("Pallet Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PAL-1");
        variant.setBarcode("7700111100017");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-PAL", "/WH-PAL");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-PAL/Z1");
        Location bin = loc(tenantId, zone.getId(), "BIN", "B1", "/WH-PAL/Z1/B1");

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("10"), "SEED", null);

        List<InventoryLevel> loose = levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId()).stream()
                .filter(l -> l.getLpnId() == null && l.getOnHand().signum() > 0)
                .toList();
        assertThat(loose).isNotEmpty();
        UUID levelId = loose.getFirst().getId();

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Ship To");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-PAL-1");
        order.setStatus("ALLOCATED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyShipped(BigDecimal.ZERO);
        line = salesOrderLineRepository.save(line);
        TenantContext.clear();

        MvcResult mintResult = mockMvc.perform(post("/api/v1/inventory/lpns/mint")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"" + bin.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lpnBarcode").value(org.hamcrest.Matchers.startsWith("LPN-")))
                .andExpect(jsonPath("$.zpl").value(org.hamcrest.Matchers.containsString("^XA")))
                .andReturn();

        String mintBody = mintResult.getResponse().getContentAsString();
        String lpnBarcode = mintBody.replaceAll("(?s).*\"lpnBarcode\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(lpnBarcode).startsWith("LPN-");

        mockMvc.perform(post("/api/v1/inventory/lpns/" + lpnBarcode + "/pack")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryLevelIds\":[\"" + levelId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linesPacked").value(1))
                .andExpect(jsonPath("$.itemCount").value(1));

        mockMvc.perform(get("/api/v1/inventory/lpns/" + lpnBarcode)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(10));

        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"salesOrderId":"%s","carrier":"UPS","trackingNumber":"1ZPAL","lpnBarcode":"%s","lines":[]}
                                """.formatted(order.getId(), lpnBarcode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        TenantContext.setTenantId(tenantId);
        LicensePlate lpn = licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, lpnBarcode).orElseThrow();
        assertThat(lpn.getStatus()).isEqualTo("DISPATCHED");
        BigDecimal remainingOnLpn = levelRepository.findByTenantIdAndLpnId(tenantId, lpn.getId()).stream()
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(remainingOnLpn).isEqualByComparingTo(BigDecimal.ZERO);

        SalesOrderLine shippedLine = salesOrderLineRepository.findById(line.getId()).orElseThrow();
        assertThat(shippedLine.getQtyShipped()).isEqualByComparingTo("10");
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
}
