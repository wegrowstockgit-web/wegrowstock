package com.invsys.service;

import com.invsys.core.security.AuthService;
import com.invsys.core.common.ApiException;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.User;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tiered putaway validation: FATAL hard-stops vs WARNING soft overrides (manager PIN).
 */
@Service
public class PutawayValidationService {

    public enum Severity { FATAL, WARNING }

    public record Finding(Severity severity, String code, String detail) {
    }

    private static final Set<String> TEMP_ZONES = Set.of("AMBIENT", "REFRIGERATED", "FROZEN");
    private static final Map<String, Integer> TEMP_RANK = Map.of(
            "AMBIENT", 0,
            "REFRIGERATED", 1,
            "FROZEN", 2
    );

    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final BinCapacityValidationService binCapacityValidationService;

    public PutawayValidationService(ProductVariantRepository variantRepository,
                                    LocationRepository locationRepository,
                                    UserRepository userRepository,
                                    AuditService auditService,
                                    BinCapacityValidationService binCapacityValidationService) {
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.binCapacityValidationService = binCapacityValidationService;
    }

    public void validatePutaway(UUID variantId, UUID locationId, String managerOverridePin) {
        validatePutaway(variantId, locationId, null, managerOverridePin);
    }

    /**
     * Validates inbound putaway / transfer-in to {@code locationId}.
     * FATAL → ApiException 422. WARNING without PIN → ApiException 409 MANAGER_OVERRIDE_REQUIRED.
     * Successful PIN override is written to {@code audit_log}.
     */
    public void validatePutaway(UUID variantId, UUID locationId, BigDecimal quantity, String managerOverridePin) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = variantRepository.findById(variantId)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        Location location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Location not found"));

        Finding fatal = fatalFinding(variant, location);
        if (fatal != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, fatal.code(), fatal.detail());
        }

        if (quantity != null) {
            binCapacityValidationService.assertFits(locationId, variantId, quantity);
        }

        Finding warning = warningFinding(variant, location);
        if (warning == null) {
            return;
        }
        if (managerOverridePin == null || managerOverridePin.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "MANAGER_OVERRIDE_REQUIRED", warning.detail())
                    .withProperty("warningCode", warning.code())
                    .withProperty("requiresManagerPin", true);
        }
        User manager = assertManagerOverridePin(tenantId, managerOverridePin.trim());
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("user_id", manager.getId().toString());
        diff.put("location_id", locationId.toString());
        diff.put("variant_id", variantId.toString());
        diff.put("reason_code", warning.code());
        diff.put("detail", warning.detail());
        diff.put("override", true);
        auditService.record("PUTAWAY_MANAGER_OVERRIDE", "LOCATION", locationId, diff);
    }

    Finding fatalFinding(ProductVariant variant, Location location) {
        if (variant.isHazmat() && !location.isAllowsHazmat()) {
            return new Finding(Severity.FATAL, "HAZMAT_ZONE_VIOLATION",
                    "Hazmat SKU cannot be put away into a location that does not allow dangerous goods");
        }
        String productZone = normalizeTemp(variant.getStorageTempZone());
        String binZone = normalizeTemp(location.getStorageTempZone());
        if (TEMP_RANK.getOrDefault(productZone, 0) > TEMP_RANK.getOrDefault(binZone, 0)) {
            return new Finding(Severity.FATAL, "TEMP_ZONE_VIOLATION",
                    "Cannot put " + productZone + " product into " + binZone + " bin");
        }
        return null;
    }

    Finding warningFinding(ProductVariant variant, Location location) {
        Integer maxPositions = location.getMaxPalletPositions();
        Integer tie = variant.getPalletTie();
        Integer high = variant.getPalletHigh();
        if (maxPositions == null || tie == null || high == null) {
            return null;
        }
        long required = (long) tie * (long) high;
        if (required > maxPositions) {
            return new Finding(Severity.WARNING, "PALLET_CAPACITY_EXCEEDED",
                    "Pallet Ti×Hi (" + tie + "×" + high + "=" + required
                            + ") exceeds bin max pallet positions (" + maxPositions
                            + "). Manager PIN required to override.");
        }
        return null;
    }

    private User assertManagerOverridePin(UUID tenantId, String pin) {
        if (!pin.matches("\\d{4}")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PIN", "Invalid manager override PIN");
        }
        String pinHash = AuthService.hashTerminalPin(tenantId, pin);
        User target = userRepository.findByTenantIdAndTerminalPinHash(tenantId, pinHash).orElse(null);
        if (target == null || !"ACTIVE".equals(target.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PIN", "Invalid manager override PIN");
        }
        return target;
    }

    private static String normalizeTemp(String zone) {
        if (zone == null || zone.isBlank()) {
            return "AMBIENT";
        }
        String normalized = zone.trim().toUpperCase();
        return TEMP_ZONES.contains(normalized) ? normalized : "AMBIENT";
    }

    /** Test helper — exposes findings without throwing. */
    public Map<String, Object> inspect(UUID variantId, UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = variantRepository.findById(variantId).orElseThrow();
        Location location = locationRepository.findById(locationId).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        Finding fatal = fatalFinding(variant, location);
        Finding warning = warningFinding(variant, location);
        out.put("fatal", fatal == null ? null : Map.of("code", fatal.code(), "detail", fatal.detail()));
        out.put("warning", warning == null ? null : Map.of("code", warning.code(), "detail", warning.detail()));
        out.put("tenantId", tenantId.toString());
        return out;
    }
}
