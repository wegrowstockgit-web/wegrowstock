package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLevelDeltaFlushRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.InventoryLevelFlushWorker;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InventoryLevelDeltaFlushHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLevelDeltaFlushRepository deltaFlushRepository;
    @Autowired InventoryLevelFlushWorker flushWorker;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void receiveWritesDeltaThenFlushMaterializesLevels() throws Exception {
        String slug = "delta-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Delta Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("DELTA");
        product.setName("Delta Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("DELTA-1");
        variant.setBarcode("7700222200018");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-D", "/WH-D");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-D/Z1");
        Location bin = loc(tenantId, zone.getId(), "BIN", "B1", "/WH-D/Z1/B1");

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("7"), "DELTA_TEST", null);

        // receive() commits before return → AFTER_COMMIT flush; drain again for determinism.
        flushWorker.flushOnce();

        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId());
        assertThat(levels).isNotEmpty();
        BigDecimal onHand = levels.stream()
                .filter(l -> bin.getId().equals(l.getLocationId()))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(onHand).isEqualByComparingTo("7");

        JdbcTemplate bootstrapJdbc = new JdbcTemplate(bootstrapDataSource);
        Integer pending = bootstrapJdbc.queryForObject(
                """
                SELECT COUNT(*) FROM inventory_level_deltas
                WHERE tenant_id = ? AND variant_id = ? AND applied_at IS NULL
                """,
                Integer.class,
                tenantId,
                variant.getId());
        assertThat(pending).isZero();
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/inventory/levels")
                        .param("variantId", variant.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].onHand").value(7));

        assertThat(deltaFlushRepository.flushBatch(50)).isZero();
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path) {
        Location loc = new Location();
        loc.setTenantId(tenantId);
        loc.setParentLocationId(parentId);
        loc.setType(type);
        loc.setCode(code);
        loc.setName(code);
        loc.setPath(path);
        return locationRepository.save(loc);
    }
}
