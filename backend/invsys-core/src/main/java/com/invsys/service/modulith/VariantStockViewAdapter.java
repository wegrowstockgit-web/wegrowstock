package com.invsys.service.modulith;

import com.invsys.modules.catalog.api.VariantStockView;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class VariantStockViewAdapter implements VariantStockView {

    private final InventoryLevelRepository levelRepository;

    public VariantStockViewAdapter(InventoryLevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    @Override
    public StockTotals totals() {
        Map<UUID, BigDecimal> onHandByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getOnHand,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map<UUID, BigDecimal> allocatedByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAllocated,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        return new StockTotals(onHandByVariant, allocatedByVariant);
    }
}
