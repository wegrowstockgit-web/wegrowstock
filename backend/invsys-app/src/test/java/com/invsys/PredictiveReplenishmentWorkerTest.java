package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.BinReplenishmentRule;
import com.invsys.domain.DemandForecast;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.WaveReplenishmentTrigger;
import com.invsys.repository.BinReplenishmentRuleRepository;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.WaveReplenishmentTriggerRepository;
import com.invsys.service.InventoryLevelFlushWorker;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.PredictiveReplenishmentWorker;
import com.invsys.service.TaskOrchestratorService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PredictiveReplenishmentWorkerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired BinReplenishmentRuleRepository ruleRepository;
    @Autowired DemandForecastRepository forecastRepository;
    @Autowired WaveReplenishmentTriggerRepository triggerRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLevelFlushWorker flushWorker;
    @Autowired PredictiveReplenishmentWorker worker;
    @Autowired TaskOrchestratorService orchestrator;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void predictiveWorkerOpensActiveTriggerAndInterleavesAsReplenish() throws Exception {
        String slug = "pred-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Pred Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PRED");
        product.setName("Pred Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PRED-1");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-P", "/WH-P", 0, null, null);
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-P/Z1", 0, null, null);
        Location pick = loc(tenantId, zone.getId(), "BIN", "PF1", "/WH-P/Z1/PF1", 10,
                new BigDecimal("1"), new BigDecimal("1"));
        pick.setZoneBehavior("PICK_FACE");
        pick = locationRepository.save(pick);
        Location here = loc(tenantId, zone.getId(), "BIN", "HERE", "/WH-P/Z1/HERE", 12,
                new BigDecimal("1.1"), new BigDecimal("1.0"));

        // Low on-hand at pick face (delta path → flush before predictive evaluation)
        inventoryService.receive(variant.getId(), pick.getId(), null, new BigDecimal("2"), "SEED", null);
        flushWorker.flushOnce();

        BinReplenishmentRule rule = new BinReplenishmentRule();
        rule.setTenantId(tenantId);
        rule.setLocationId(pick.getId());
        rule.setVariantId(variant.getId());
        rule.setMinQuantity(new BigDecimal("10"));
        rule.setMaxQuantity(new BigDecimal("40"));
        ruleRepository.save(rule);

        DemandForecast forecast = new DemandForecast();
        forecast.setTenantId(tenantId);
        forecast.setVariantId(variant.getId());
        forecast.setVelocity30d(new BigDecimal("20")); // 20/day → 40 over 48h
        forecast.setRecommendedPoQty(new BigDecimal("100"));
        forecastRepository.save(forecast);

        int created = worker.evaluateTenant(tenantId);
        assertThat(created).isEqualTo(1);

        // evaluateTenant clears TenantContext in finally — restore for RLS-bound reads
        TenantContext.setTenantId(tenantId);
        List<WaveReplenishmentTrigger> triggers =
                triggerRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        assertThat(triggers).hasSize(1);
        assertThat(triggers.getFirst().getProjectedDemand()).isEqualByComparingTo("40.0000");
        assertThat(triggers.getFirst().getCurrentBinQty()).isEqualByComparingTo("2");

        TaskOrchestratorService.NextBestAction nba = orchestrator.nextBestAction(here.getId());
        assertThat(nba.taskType()).isEqualTo("REPLENISH");
        assertThat(nba.locationId()).isEqualTo(pick.getId());
        assertThat(nba.travelScore()).isNotNull();
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/tasks/next-best-action")
                        .param("currentLocationId", here.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("REPLENISH"))
                .andExpect(jsonPath("$.locationId").value(pick.getId().toString()))
                .andExpect(jsonPath("$.travelScore").exists());
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path,
                         int seq, BigDecimal x, BigDecimal y) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        location.setSequenceIndex(seq);
        location.setCoordX(x);
        location.setCoordY(y);
        return locationRepository.save(location);
    }
}
