package com.invsys;

import com.invsys.api.dto.DashboardStatsResponse;
import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.DashboardKpiSnapshot;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.DashboardKpiSnapshotRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.DashboardKpiService;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DashboardKpiCqrsHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired DashboardKpiService dashboardKpiService;
    @Autowired DashboardKpiSnapshotRepository snapshotRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void statsReadFromSnapshotAfterRefresh() throws Exception {
        String slug = "cqrs-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "CQRS Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("CQRS");
        product.setName("CQRS Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("CQRS-1");
        variant.setPrice(new BigDecimal("10.00"));
        variant.setReorderPoint(new BigDecimal("100"));
        variant = variantRepository.save(variant);

        Location bin = new Location();
        bin.setTenantId(tenantId);
        bin.setType("BIN");
        bin.setCode("B-01");
        bin.setName("Bin 01");
        bin.setPath("WH-CQRS/Z-A/A-1/B-01");
        bin = locationRepository.save(bin);

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("5"), "CQRS_SEED", null);

        DashboardKpiSnapshot refreshed = dashboardKpiService.refresh(tenantId, "TEST_SEED");
        assertThat(refreshed.getStockValue()).isEqualByComparingTo("50.00");
        assertThat(refreshed.getLowStockCount()).isGreaterThanOrEqualTo(1);

        DashboardStatsResponse stats = dashboardKpiService.readStats();
        assertThat(stats.stockValue()).isEqualByComparingTo(refreshed.getStockValue());
        assertThat(snapshotRepository.findByTenantId(tenantId)).isPresent();

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockValue").exists())
                .andExpect(jsonPath("$.openOrdersCount").exists())
                .andExpect(jsonPath("$.currency").isNotEmpty());
    }
}
