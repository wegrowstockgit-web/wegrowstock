package com.invsys.api;

import com.invsys.domain.WarehouseContextRule;
import com.invsys.service.WarehouseContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WarehouseContextController {

    private final WarehouseContextService warehouseContextService;

    public WarehouseContextController(WarehouseContextService warehouseContextService) {
        this.warehouseContextService = warehouseContextService;
    }

    /**
     * Boot-time / MDM hardware intent: resolve warehouse from Wi-Fi SSID or geofence.
     * Intersects with JWT warehouse_ids so operators cannot escape LBAC.
     */
    @PostMapping("/terminals/resolve-context")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> resolve(@Valid @RequestBody ResolveRequest request) {
        return warehouseContextService.resolve(request.ssid(), request.latitude(), request.longitude())
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "matched", true,
                        "warehouseId", result.warehouseId().toString(),
                        "matchType", result.matchType(),
                        "ruleId", result.ruleId().toString(),
                        "label", result.label() != null ? result.label() : "",
                        "locked", true)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("matched", false, "locked", false)));
    }

    @GetMapping("/warehouse-context-rules")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<RuleResponse> list() {
        return warehouseContextService.listRules().stream().map(RuleResponse::from).toList();
    }

    @PostMapping("/warehouse-context-rules")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleBody body) {
        WarehouseContextRule saved = warehouseContextService.create(body.toService());
        return ResponseEntity.status(HttpStatus.CREATED).body(RuleResponse.from(saved));
    }

    @PutMapping("/warehouse-context-rules/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public RuleResponse update(@PathVariable UUID id, @Valid @RequestBody RuleBody body) {
        return RuleResponse.from(warehouseContextService.update(id, body.toService()));
    }

    @DeleteMapping("/warehouse-context-rules/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        warehouseContextService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record ResolveRequest(
            String ssid,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record RuleBody(
            @NotNull UUID locationId,
            @NotBlank String matchType,
            String ssid,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radiusMeters,
            Integer priority,
            Boolean enabled,
            String label
    ) {
        WarehouseContextService.CreateRuleRequest toService() {
            return new WarehouseContextService.CreateRuleRequest(
                    locationId, matchType, ssid, latitude, longitude, radiusMeters, priority, enabled, label);
        }
    }

    public record RuleResponse(
            UUID id,
            UUID locationId,
            String matchType,
            String ssid,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radiusMeters,
            int priority,
            boolean enabled,
            String label
    ) {
        static RuleResponse from(WarehouseContextRule rule) {
            return new RuleResponse(
                    rule.getId(),
                    rule.getLocationId(),
                    rule.getMatchType(),
                    rule.getSsid(),
                    rule.getLatitude(),
                    rule.getLongitude(),
                    rule.getRadiusMeters(),
                    rule.getPriority(),
                    rule.isEnabled(),
                    rule.getLabel());
        }
    }
}
