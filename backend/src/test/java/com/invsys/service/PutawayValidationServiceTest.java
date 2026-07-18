package com.invsys.service;

import com.invsys.auth.AuthService;
import com.invsys.common.ApiException;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.User;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PutawayValidationServiceTest {

    private static final UUID TENANT = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID VARIANT_ID = UUID.fromString("b0000000-0000-4000-8000-000000000801");
    private static final UUID LOCATION_ID = UUID.fromString("b0000000-0000-4000-8000-000000000604");

    @Mock ProductVariantRepository variantRepository;
    @Mock LocationRepository locationRepository;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;

    private PutawayValidationService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new PutawayValidationService(
                variantRepository, locationRepository, userRepository, auditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void fatalBlocksHazmatIntoNonHazmatBin() {
        ProductVariant variant = variant(true, "AMBIENT", 10, 2);
        Location location = location(false, "AMBIENT", 100);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> service.validatePutaway(VARIANT_ID, LOCATION_ID, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(api.getCode()).isEqualTo("HAZMAT_ZONE_VIOLATION");
                });
    }

    @Test
    void fatalBlocksFrozenIntoAmbient() {
        ProductVariant variant = variant(false, "FROZEN", null, null);
        Location location = location(false, "AMBIENT", null);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> service.validatePutaway(VARIANT_ID, LOCATION_ID, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TEMP_ZONE_VIOLATION"));
    }

    @Test
    void warningRequiresManagerPinForTiHiOverCapacity() {
        ProductVariant variant = variant(false, "AMBIENT", 10, 5);
        Location location = location(false, "AMBIENT", 20);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> service.validatePutaway(VARIANT_ID, LOCATION_ID, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("MANAGER_OVERRIDE_REQUIRED");
                    assertThat(api.getProperties()).containsEntry("requiresManagerPin", true);
                });
    }

    @Test
    void inspectReportsFatalAndWarningWithoutThrowing() {
        ProductVariant variant = variant(true, "FROZEN", 10, 5);
        Location location = location(false, "AMBIENT", 20);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));

        Map<String, Object> report = service.inspect(VARIANT_ID, LOCATION_ID);
        assertThat(report.get("fatal")).isNotNull();
        assertThat(report.get("warning")).isNotNull();
    }

    @Test
    void ambientIntoFrozenIsAllowed() {
        ProductVariant variant = variant(false, "AMBIENT", null, null);
        Location location = location(false, "FROZEN", null);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));

        service.validatePutaway(VARIANT_ID, LOCATION_ID, null);
    }

    @Test
    void warningAllowsOverrideWithValidTerminalPin() {
        ProductVariant variant = variant(false, "AMBIENT", 10, 5);
        Location location = location(false, "AMBIENT", 20);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));

        String pin = "4242";
        User manager = new User();
        manager.setId(UUID.fromString("b0000000-0000-4000-8000-000000000101"));
        manager.setTenantId(TENANT);
        manager.setStatus("ACTIVE");
        manager.setTerminalPinHash(AuthService.hashTerminalPin(TENANT, pin));
        when(userRepository.findByTenantIdAndTerminalPinHash(TENANT, manager.getTerminalPinHash()))
                .thenReturn(Optional.of(manager));

        service.validatePutaway(VARIANT_ID, LOCATION_ID, pin);

        verify(auditService).record(
                eq("PUTAWAY_MANAGER_OVERRIDE"),
                eq("LOCATION"),
                eq(LOCATION_ID),
                any());
    }

    private ProductVariant variant(boolean hazmat, String temp, Integer tie, Integer high) {
        ProductVariant v = new ProductVariant();
        v.setId(VARIANT_ID);
        v.setTenantId(TENANT);
        v.setHazmat(hazmat);
        v.setStorageTempZone(temp);
        v.setPalletTie(tie);
        v.setPalletHigh(high);
        return v;
    }

    private Location location(boolean allowsHazmat, String temp, Integer maxPositions) {
        Location l = new Location();
        l.setId(LOCATION_ID);
        l.setTenantId(TENANT);
        l.setAllowsHazmat(allowsHazmat);
        l.setStorageTempZone(temp);
        l.setMaxPalletPositions(maxPositions);
        return l;
    }
}
