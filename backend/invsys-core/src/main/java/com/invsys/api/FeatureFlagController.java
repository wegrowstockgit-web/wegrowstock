package com.invsys.api;

import com.invsys.core.service.FeatureFlagService;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feature-flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    public EnabledFlagsResponse listEnabled() {
        return new EnabledFlagsResponse(featureFlagService.listEnabledKeys(TenantContext.requireTenantId()));
    }

    public record EnabledFlagsResponse(List<String> flags) {
    }
}
