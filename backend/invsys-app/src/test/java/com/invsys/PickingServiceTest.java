package com.invsys;

import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.fulfillment.service.PickingService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PickingServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired PickingService pickingService;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void optimizePickSequenceVisitsNearestBins() {
        UUID tenantId = testDataHelper.createTenant("Pick Svc", "picksvc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Location a = saveLoc(tenantId, "WH/A/01");
        Location b = saveLoc(tenantId, "WH/B/50");
        Location c = saveLoc(tenantId, "WH/C/99");

        Allocation allocC = alloc(c.getId());
        Allocation allocA = alloc(a.getId());
        Allocation allocB = alloc(b.getId());

        Map<UUID, String> paths = Map.of(
                a.getId(), a.getPath(),
                b.getId(), b.getPath(),
                c.getId(), c.getPath());

        List<Allocation> route = pickingService.optimizePickSequence(
                List.of(allocC, allocA, allocB), paths);

        assertThat(route.get(0).getLocationId()).isEqualTo(a.getId());
        assertThat(route.get(1).getLocationId()).isEqualTo(b.getId());
        assertThat(route.get(2).getLocationId()).isEqualTo(c.getId());
    }

    @Test
    void hierarchicalPathSortOrdersWarehouseZoneAisleBay() {
        UUID tenantId = testDataHelper.createTenant("Hier Path", "hier-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Location bay10 = saveLoc(tenantId, "WH-01/Z-A/A-2/B-10");
        Location zoneB = saveLoc(tenantId, "WH-01/Z-B/A-1/B-01");
        Location bay01 = saveLoc(tenantId, "WH-01/Z-A/A-2/B-01");
        Location aisle1 = saveLoc(tenantId, "WH-01/Z-A/A-1/B-01");

        Map<UUID, String> paths = Map.of(
                bay10.getId(), bay10.getPath(),
                zoneB.getId(), zoneB.getPath(),
                bay01.getId(), bay01.getPath(),
                aisle1.getId(), aisle1.getPath());

        var route = pickingService.optimizePickSequence(
                List.of(alloc(bay10.getId()), alloc(zoneB.getId()), alloc(bay01.getId()), alloc(aisle1.getId())),
                paths);

        assertThat(route).extracting(Allocation::getLocationId).containsExactly(
                aisle1.getId(), bay01.getId(), bay10.getId(), zoneB.getId());
    }

    @Test
    void fragileVariantsAreSequencedLastInPickPath() {
        UUID tenantId = testDataHelper.createTenant("Fragile Pick", "frag-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Location early = saveLoc(tenantId, "WH/A/01");
        Location late = saveLoc(tenantId, "WH/Z/99");

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("FRG");
        product.setName("Fragile kit");
        product = productRepository.save(product);

        ProductVariant sturdy = new ProductVariant();
        sturdy.setTenantId(tenantId);
        sturdy.setProductId(product.getId());
        sturdy.setSku("FRG-STURDY");
        sturdy.setFragile(false);
        sturdy = productVariantRepository.save(sturdy);

        ProductVariant fragile = new ProductVariant();
        fragile.setTenantId(tenantId);
        fragile.setProductId(product.getId());
        fragile.setSku("FRG-GLASS");
        fragile.setFragile(true);
        fragile = productVariantRepository.save(fragile);

        Allocation fragileEarly = alloc(early.getId(), fragile.getId());
        Allocation sturdyLate = alloc(late.getId(), sturdy.getId());

        Map<UUID, String> paths = Map.of(
                early.getId(), early.getPath(),
                late.getId(), late.getPath());

        List<Allocation> route = pickingService.optimizePickSequence(
                List.of(fragileEarly, sturdyLate), paths);

        assertThat(route.get(0).getVariantId()).isEqualTo(sturdy.getId());
        assertThat(route.get(1).getVariantId()).isEqualTo(fragile.getId());
    }

    @Test
    void locationPathKeyParsesNumericBayOrdering() {
        var a = PickingService.LocationPathKey.parse("WH-01/Z-A/A-1/B-2");
        var b = PickingService.LocationPathKey.parse("WH-01/Z-A/A-1/B-10");
        assertThat(a.compareTo(b)).isLessThan(0);
    }

    @Test
    void buildStopsIncludesZoneAndSequence() {
        UUID tenantId = testDataHelper.createTenant("Pick Stop", "pickstp-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Location loc = saveLoc(tenantId, "WH/Z-A/01");
        Allocation alloc = alloc(loc.getId());

        var stops = pickingService.buildStops(List.of(alloc), Map.of(loc.getId(), loc));
        assertThat(stops).hasSize(1);
        assertThat(stops.getFirst().zone()).isEqualTo("Z-A");
        assertThat(stops.getFirst().sequenceOrder()).isEqualTo(1);
    }

    private Location saveLoc(UUID tenantId, String path) {
        Location loc = new Location();
        loc.setTenantId(tenantId);
        loc.setType("BIN");
        loc.setCode(path.replace("/", "-"));
        loc.setName(path);
        loc.setPath(path);
        return locationRepository.save(loc);
    }

    private static Allocation alloc(UUID locationId) {
        return alloc(locationId, null);
    }

    private static Allocation alloc(UUID locationId, UUID variantId) {
        Allocation a = new Allocation();
        a.setId(UUID.randomUUID());
        a.setLocationId(locationId);
        a.setVariantId(variantId);
        a.setQuantity(BigDecimal.ONE);
        return a;
    }
}
