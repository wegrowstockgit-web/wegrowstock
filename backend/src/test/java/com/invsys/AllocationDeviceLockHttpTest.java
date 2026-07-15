package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Allocation;
import com.invsys.domain.Customer;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.User;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.AllocationService;
import com.invsys.service.InventoryService;
import com.invsys.service.PickingWaveService;
import com.invsys.tenancy.TenantContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AllocationDeviceLockHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired AllocationService allocationService;
    @Autowired InventoryService inventoryService;
    @Autowired PickingWaveService pickingWaveService;
    @Autowired UserRepository userRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void claimWaveLocksAllocationsAndForeignScanReturns409() throws Exception {
        String slugA = "lk-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Lock Co", slugA, "owner@" + slugA + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("LCK");
        product.setName("Lock Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("LCK-1");
        variant.setBarcode("9900666677771");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-L");
        wh.setName("Lock WH");
        wh.setPath("/WH-L");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("5"), "SEED", null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Lock Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setStatus("CONFIRMED");
        order.setNumber("SO-LOCK-1");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line.setQtyAllocated(BigDecimal.ZERO);
        line = salesOrderLineRepository.save(line);

        List<Allocation> created = allocationService.allocate(line, List.of(wh.getId()));
        assertThat(created).isNotEmpty();

        PickingWaveService.WaveResult wave = pickingWaveService.generateWave(null, null);
        pickingWaveService.releaseWave(wave.wave().getId());
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/picking/waves/" + wave.wave().getId() + "/claim")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationsClaimed").value(org.hamcrest.Matchers.greaterThan(0)));

        TenantContext.setTenantId(tenantId);
        Allocation locked = allocationRepository.findById(created.getFirst().getId()).orElseThrow();
        assertThat(locked.getAssignedToUserId()).isEqualTo(owner.userId());
        // Simulate reassignment to another real picker in the tenant
        User otherPicker = new User();
        otherPicker.setTenantId(tenantId);
        otherPicker.setEmail("picker@" + slugA + ".test");
        otherPicker.setDisplayName("Other Picker");
        otherPicker.setPasswordHash("x");
        otherPicker.setStatus("ACTIVE");
        otherPicker = userRepository.save(otherPicker);
        locked.setAssignedToUserId(otherPicker.getId());
        allocationRepository.save(locked);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"9900666677771","warehouseId":"%s","mode":"pick","allocationId":"%s"}
                                """.formatted(wh.getId(), created.getFirst().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALLOCATION_LOCKED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reassigned")));

        TenantContext.setTenantId(tenantId);
        locked = allocationRepository.findById(created.getFirst().getId()).orElseThrow();
        locked.setAssignedToUserId(owner.userId());
        locked.setStatus("CONSUMED");
        allocationRepository.save(locked);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"9900666677771","warehouseId":"%s","mode":"pick","allocationId":"%s"}
                                """.formatted(wh.getId(), created.getFirst().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ALLOCATION_CONSUMED"));
    }
}
