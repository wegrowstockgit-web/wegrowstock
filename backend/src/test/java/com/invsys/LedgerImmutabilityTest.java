package com.invsys;

import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.Tenant;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerImmutabilityTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired TenantRepository tenantRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() {
        UUID tenantId = testDataHelper.createTenant("Ledger Tenant", "ledger-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("LEDGER-SKU");
        product.setName("Ledger Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("LEDGER-V1");
        variant = variantRepository.save(variant);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-L");
        location.setName("Ledger WH");
        location.setPath("/WH-L");
        location = locationRepository.save(location);

        InventoryLedger entry = inventoryService.receive(variant.getId(), location.getId(), null,
                BigDecimal.TEN, null, null);

        InventoryLedger loaded = ledgerRepository.findById(entry.getId()).orElseThrow();
        loaded.setQuantityDelta(BigDecimal.ONE);
        assertThatThrownBy(() -> ledgerRepository.saveAndFlush(loaded))
                .isInstanceOf(Exception.class);

        assertThatThrownBy(() -> ledgerRepository.delete(loaded))
                .isInstanceOf(DataAccessException.class);
    }

}
