package com.invsys;

import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerImmutabilityTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbcTemplate;

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
        // Hibernate @Immutable ignores mutations — DB triggers still enforce append-only.
        ledgerRepository.saveAndFlush(loaded);
        assertThat(ledgerRepository.findById(entry.getId()).orElseThrow().getQuantityDelta())
                .isEqualByComparingTo(BigDecimal.TEN);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE inventory_ledger SET quantity_delta = 1 WHERE id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM inventory_ledger WHERE id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void doubleReversalAndReversingACorrectionAreFatal() {
        UUID tenantId = testDataHelper.createTenant(
                "Ledger Rev", "ledger-rev-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("REV-SKU");
        product.setName("Rev Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("REV-V1");
        variant = variantRepository.save(variant);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-R");
        location.setName("Rev WH");
        location.setPath("/WH-R");
        location = locationRepository.save(location);

        InventoryLedger original = inventoryService.receive(
                variant.getId(), location.getId(), null, BigDecimal.TEN, null, null);
        InventoryLedger reversal = inventoryService.reverseLedgerEntry(original.getId());

        assertThatThrownBy(() -> inventoryService.reverseLedgerEntry(original.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("ALREADY_REVERSED");
                });

        assertThatThrownBy(() -> inventoryService.reverseLedgerEntry(reversal.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CANNOT_REVERSE_REVERSAL"));
    }

}
