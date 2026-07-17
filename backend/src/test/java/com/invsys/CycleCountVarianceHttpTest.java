package com.invsys;

import com.invsys.domain.CycleCount;
import com.invsys.domain.CycleCountLine;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.CycleCountLineRepository;
import com.invsys.repository.CycleCountRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.service.CycleCountService;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional coverage for blind cycle-count variance escalation (Rules A/B/C)
 * plus manager approve / recount.
 */
class CycleCountVarianceHttpTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired CycleCountService cycleCountService;
    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired CycleCountRepository cycleCountRepository;
    @Autowired CycleCountLineRepository cycleCountLineRepository;
    @Autowired TenantSettingsRepository tenantSettingsRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void endToEnd_rulesAndManagerDesk() {
        UUID tenantId = testDataHelper.createTenant("Blind CC", "bcc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSettings.withDefaults(tenantId));
        settings.setBlindCycleCounts(true);
        settings.setMaxAutoAdjustValue(new BigDecimal("100.00"));
        tenantSettingsRepository.save(settings);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("CC");
        product.setName("Count Item");
        product = productRepository.save(product);

        ProductVariant cheapDraft = new ProductVariant();
        cheapDraft.setTenantId(tenantId);
        cheapDraft.setProductId(product.getId());
        cheapDraft.setSku("CC-CHEAP");
        cheapDraft.setAvgCost(new BigDecimal("5.00"));
        final ProductVariant cheap = variantRepository.save(cheapDraft);

        ProductVariant priceyDraft = new ProductVariant();
        priceyDraft.setTenantId(tenantId);
        priceyDraft.setProductId(product.getId());
        priceyDraft.setSku("CC-PRICEY");
        priceyDraft.setAvgCost(new BigDecimal("50.00"));
        final ProductVariant pricey = variantRepository.save(priceyDraft);

        Location binDraft = new Location();
        binDraft.setTenantId(tenantId);
        binDraft.setType("BIN");
        binDraft.setCode("CC-BIN");
        binDraft.setName("Count Bin");
        binDraft.setPath("WH/CC-BIN");
        final Location bin = locationRepository.save(binDraft);

        inventoryService.receive(cheap.getId(), bin.getId(), null, new BigDecimal("10"), null, null,
                new BigDecimal("5.00"), null);
        inventoryService.receive(pricey.getId(), bin.getId(), null, new BigDecimal("10"), null, null,
                new BigDecimal("50.00"), null);

        CycleCountService.CycleCountDetail detail = cycleCountService.startCount(bin.getId());
        assertThat(detail.blindCycleCounts()).isTrue();
        assertThat(detail.lines()).hasSize(2);

        CycleCountLine cheapLine = cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(detail.id()).stream()
                .filter(l -> l.getVariantId().equals(cheap.getId()))
                .findFirst()
                .orElseThrow();
        CycleCountLine priceyLine = cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(detail.id()).stream()
                .filter(l -> l.getVariantId().equals(pricey.getId()))
                .findFirst()
                .orElseThrow();

        // Rule A — exact match
        CycleCountService.CycleCountLineView match =
                cycleCountService.submitCountedQty(detail.id(), cheapLine.getId(), new BigDecimal("10"));
        assertThat(match.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_AUTO_APPROVED);

        // Reset cheap line for Rule B by starting a fresh count on another bin snapshot path:
        // Rule B on pricey with small delta: expected 10, counted 9 → impact 50 < 100
        CycleCountService.CycleCountLineView auto =
                cycleCountService.submitCountedQty(detail.id(), priceyLine.getId(), new BigDecimal("9"));
        assertThat(auto.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_AUTO_APPROVED);

        BigDecimal priceyOnHand = onHand(tenantId, bin.getId(), pricey.getId());
        assertThat(priceyOnHand).isEqualByComparingTo("9");

        // New count for Rule C escalation
        Location bin2 = new Location();
        bin2.setTenantId(tenantId);
        bin2.setType("BIN");
        bin2.setCode("CC-BIN-2");
        bin2.setName("Count Bin 2");
        bin2.setPath("WH/CC-BIN-2");
        bin2 = locationRepository.save(bin2);
        inventoryService.receive(pricey.getId(), bin2.getId(), null, new BigDecimal("10"), null, null,
                new BigDecimal("50.00"), null);

        CycleCountService.CycleCountDetail detail2 = cycleCountService.startCount(bin2.getId());
        CycleCountLine escalateLine = cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(detail2.id()).getFirst();

        // expected 10, counted 0, avg 50 → impact 500 > 100
        CycleCountService.CycleCountLineView pending =
                cycleCountService.submitCountedQty(detail2.id(), escalateLine.getId(), BigDecimal.ZERO);
        assertThat(pending.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_PENDING_MANAGER_REVIEW);
        assertThat(onHand(tenantId, bin2.getId(), pricey.getId())).isEqualByComparingTo("10");

        assertThat(cycleCountService.pendingVariances()).extracting(CycleCountService.PendingVariance::lineId)
                .contains(escalateLine.getId());

        cycleCountService.requestRecount(escalateLine.getId());
        CycleCountLine afterRecount = cycleCountLineRepository.findById(escalateLine.getId()).orElseThrow();
        assertThat(afterRecount.getVarianceStatus()).isEqualTo(CycleCountService.VARIANCE_PENDING);
        assertThat(afterRecount.getCountedQty()).isNull();

        CycleCountService.CycleCountLineView pendingAgain =
                cycleCountService.submitCountedQty(detail2.id(), escalateLine.getId(), BigDecimal.ZERO);
        assertThat(pendingAgain.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_PENDING_MANAGER_REVIEW);

        cycleCountService.approveLedgerAdjustment(escalateLine.getId());
        assertThat(onHand(tenantId, bin2.getId(), pricey.getId())).isEqualByComparingTo("0");
        CycleCount completed = cycleCountRepository.findById(detail2.id()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
    }

    private BigDecimal onHand(UUID tenantId, UUID locationId, UUID variantId) {
        return levelRepository.findByTenantIdAndLocationId(tenantId, locationId).stream()
                .filter(l -> l.getVariantId().equals(variantId))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
