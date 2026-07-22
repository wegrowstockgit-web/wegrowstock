package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.WarehouseContextRule;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.WarehouseContextRuleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves warehouse context from handheld network signals (Wi-Fi SSID) or
 * mobile geofencing — automated gate used instead of a passive warehouse dropdown.
 */
@Service
public class WarehouseContextService {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private final WarehouseContextRuleRepository ruleRepository;
    private final LocationRepository locationRepository;

    public WarehouseContextService(WarehouseContextRuleRepository ruleRepository,
                                   LocationRepository locationRepository) {
        this.ruleRepository = ruleRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<WarehouseContextRule> listRules() {
        return ruleRepository.findByTenantIdOrderByPriorityAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public WarehouseContextRule create(CreateRuleRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        validateLocation(tenantId, request.locationId());
        WarehouseContextRule rule = new WarehouseContextRule();
        rule.setTenantId(tenantId);
        apply(rule, request);
        return ruleRepository.save(rule);
    }

    @Transactional
    public WarehouseContextRule update(UUID id, CreateRuleRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        WarehouseContextRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Rule not found"));
        validateLocation(tenantId, request.locationId());
        apply(rule, request);
        return ruleRepository.save(rule);
    }

    @Transactional
    public void delete(UUID id) {
        WarehouseContextRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Rule not found"));
        ruleRepository.delete(rule);
    }

    /**
     * Match the strongest enabled rule against provided signals, intersected with
     * the caller's authorized warehouse_ids (empty authorized = elevated / all).
     */
    @Transactional(readOnly = true)
    public Optional<ResolveResult> resolve(String ssid, BigDecimal latitude, BigDecimal longitude) {
        UUID tenantId = TenantContext.requireTenantId();
        List<UUID> authorized = TenantContext.getAuthorizedWarehouseIds();
        List<WarehouseContextRule> rules = ruleRepository
                .findByTenantIdAndEnabledTrueOrderByPriorityAsc(tenantId);

        Optional<ResolveResult> ssidMatch = matchSsid(rules, ssid, authorized);
        if (ssidMatch.isPresent()) {
            return ssidMatch;
        }
        return matchGeofence(rules, latitude, longitude, authorized);
    }

    private Optional<ResolveResult> matchSsid(List<WarehouseContextRule> rules,
                                              String ssid,
                                              List<UUID> authorized) {
        if (ssid == null || ssid.isBlank()) {
            return Optional.empty();
        }
        String normalized = ssid.trim().toLowerCase(Locale.ROOT);
        return rules.stream()
                .filter(r -> "WIFI_SSID".equals(r.getMatchType()))
                .filter(r -> r.getSsid() != null && normalized.equals(r.getSsid().trim().toLowerCase(Locale.ROOT)))
                .filter(r -> isAuthorized(authorized, r.getLocationId()))
                .min(Comparator.comparingInt(WarehouseContextRule::getPriority))
                .map(r -> new ResolveResult(r.getLocationId(), "WIFI_SSID", r.getId(), r.getLabel()));
    }

    private Optional<ResolveResult> matchGeofence(List<WarehouseContextRule> rules,
                                                  BigDecimal latitude,
                                                  BigDecimal longitude,
                                                  List<UUID> authorized) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        return rules.stream()
                .filter(r -> "GEOFENCE".equals(r.getMatchType()))
                .filter(r -> r.getLatitude() != null && r.getLongitude() != null && r.getRadiusMeters() != null)
                .filter(r -> isAuthorized(authorized, r.getLocationId()))
                .filter(r -> haversineMeters(lat, lng,
                        r.getLatitude().doubleValue(), r.getLongitude().doubleValue())
                        <= r.getRadiusMeters().doubleValue())
                .min(Comparator.comparingInt(WarehouseContextRule::getPriority)
                        .thenComparingDouble(r -> haversineMeters(lat, lng,
                                r.getLatitude().doubleValue(), r.getLongitude().doubleValue())))
                .map(r -> new ResolveResult(r.getLocationId(), "GEOFENCE", r.getId(), r.getLabel()));
    }

    private void apply(WarehouseContextRule rule, CreateRuleRequest request) {
        String matchType = request.matchType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("WIFI_SSID", "GEOFENCE").contains(matchType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MATCH_TYPE",
                    "matchType must be WIFI_SSID or GEOFENCE");
        }
        rule.setLocationId(request.locationId());
        rule.setMatchType(matchType);
        rule.setPriority(request.priority() != null ? request.priority() : 100);
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setLabel(request.label());
        if ("WIFI_SSID".equals(matchType)) {
            if (request.ssid() == null || request.ssid().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SSID_REQUIRED", "ssid is required for WIFI_SSID");
            }
            rule.setSsid(request.ssid().trim());
            rule.setLatitude(null);
            rule.setLongitude(null);
            rule.setRadiusMeters(null);
        } else {
            if (request.latitude() == null || request.longitude() == null || request.radiusMeters() == null
                    || request.radiusMeters().signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GEOFENCE_REQUIRED",
                        "latitude, longitude, and positive radiusMeters are required for GEOFENCE");
            }
            rule.setSsid(null);
            rule.setLatitude(request.latitude());
            rule.setLongitude(request.longitude());
            rule.setRadiusMeters(request.radiusMeters());
        }
    }

    private void validateLocation(UUID tenantId, UUID locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Location not found"));
        if (!tenantId.equals(location.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Location not found");
        }
        if (!"WAREHOUSE".equals(location.getType()) && !"VEHICLE".equals(location.getType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOCATION",
                    "Context rules must target a WAREHOUSE or VEHICLE location");
        }
    }

    private static boolean isAuthorized(List<UUID> authorized, UUID locationId) {
        return authorized == null || authorized.isEmpty() || authorized.contains(locationId);
    }

    /** Great-circle distance in meters. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    public record CreateRuleRequest(
            UUID locationId,
            String matchType,
            String ssid,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radiusMeters,
            Integer priority,
            Boolean enabled,
            String label
    ) {
    }

    public record ResolveResult(
            UUID warehouseId,
            String matchType,
            UUID ruleId,
            String label
    ) {
    }
}
