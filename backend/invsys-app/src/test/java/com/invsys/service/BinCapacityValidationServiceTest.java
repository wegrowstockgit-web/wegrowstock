package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinCapacityValidationServiceTest {

    private static final UUID TENANT = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID LOCATION_ID = UUID.fromString("b0000000-0000-4000-8000-000000000604");
    private static final UUID VARIANT_A = UUID.fromString("b0000000-0000-4000-8000-000000000801");
    private static final UUID VARIANT_B = UUID.fromString("b0000000-0000-4000-8000-000000000802");

    @Mock LocationRepository locationRepository;
    @Mock InventoryLevelRepository levelRepository;
    @Mock ProductVariantRepository variantRepository;

    private BinCapacityValidationService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new BinCapacityValidationService(locationRepository, levelRepository, variantRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsWhenIncomingVolumeExceedsMaxCubicCm() {
        Location bin = binLocation(new BigDecimal("1000"), null);
        ProductVariant existing = variant(VARIANT_A, new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("1"));
        ProductVariant incoming = variant(VARIANT_B, new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("1"));

        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(bin));
        when(variantRepository.findById(VARIANT_B)).thenReturn(Optional.of(incoming));
        when(variantRepository.findAll()).thenReturn(List.of(existing, incoming));
        when(levelRepository.findByTenantIdAndLocationId(TENANT, LOCATION_ID)).thenReturn(List.of(
                level(VARIANT_A, new BigDecimal("10"))));

        assertThatThrownBy(() -> service.assertFits(LOCATION_ID, VARIANT_B, new BigDecimal("2")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("BIN_CAPACITY_EXCEEDED");
                });
    }

    @Test
    void rejectsWhenIncomingWeightExceedsMaxWeightKg() {
        Location bin = binLocation(null, new BigDecimal("20"));
        ProductVariant existing = variant(VARIANT_A, new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("3"));
        ProductVariant incoming = variant(VARIANT_B, new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("3"));

        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(bin));
        when(variantRepository.findById(VARIANT_B)).thenReturn(Optional.of(incoming));
        when(variantRepository.findAll()).thenReturn(List.of(existing, incoming));
        when(levelRepository.findByTenantIdAndLocationId(TENANT, LOCATION_ID)).thenReturn(List.of(
                level(VARIANT_A, new BigDecimal("5"))));

        assertThatThrownBy(() -> service.assertFits(LOCATION_ID, VARIANT_B, new BigDecimal("2")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("BIN_CAPACITY_EXCEEDED"));
    }

    @Test
    void allowsPutawayWithinCapacity() {
        Location bin = binLocation(new BigDecimal("5000"), new BigDecimal("100"));
        ProductVariant variant = variant(VARIANT_A, new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("1"));

        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(bin));
        when(variantRepository.findById(VARIANT_A)).thenReturn(Optional.of(variant));
        when(variantRepository.findAll()).thenReturn(List.of(variant));
        when(levelRepository.findByTenantIdAndLocationId(TENANT, LOCATION_ID)).thenReturn(List.of());

        service.assertFits(LOCATION_ID, VARIANT_A, new BigDecimal("1"));
    }

    private Location binLocation(BigDecimal maxCubicCm, BigDecimal maxWeightKg) {
        Location location = new Location();
        location.setId(LOCATION_ID);
        location.setTenantId(TENANT);
        location.setType("BIN");
        location.setMaxCubicCm(maxCubicCm);
        location.setMaxWeightKg(maxWeightKg);
        return location;
    }

    private ProductVariant variant(UUID id, BigDecimal length, BigDecimal width, BigDecimal height, BigDecimal weight) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setTenantId(TENANT);
        variant.setLength(length);
        variant.setWidth(width);
        variant.setHeight(height);
        variant.setWeight(weight);
        return variant;
    }

    private InventoryLevel level(UUID variantId, BigDecimal onHand) {
        InventoryLevel level = new InventoryLevel();
        level.setTenantId(TENANT);
        level.setVariantId(variantId);
        level.setLocationId(LOCATION_ID);
        level.setOnHand(onHand);
        level.setAllocated(BigDecimal.ZERO);
        return level;
    }
}
