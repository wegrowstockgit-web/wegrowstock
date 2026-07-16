package com.invsys.api;

import com.invsys.service.IntegrationVaultSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/integration-credentials")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class IntegrationVaultSettingsController {

    private final IntegrationVaultSettingsService vaultSettingsService;

    public IntegrationVaultSettingsController(IntegrationVaultSettingsService vaultSettingsService) {
        this.vaultSettingsService = vaultSettingsService;
    }

    @GetMapping
    public List<IntegrationVaultSettingsService.CredentialStatus> list(
            @RequestParam(required = false) String systems) {
        List<String> parsed = systems == null || systems.isBlank()
                ? List.of()
                : java.util.Arrays.stream(systems.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return vaultSettingsService.list(parsed);
    }

    @PostMapping
    public IntegrationVaultSettingsService.CredentialStatus save(@Valid @RequestBody SaveRequest request) {
        return vaultSettingsService.save(request.system(), request.apiKey());
    }

    @DeleteMapping("/{system}")
    public IntegrationVaultSettingsService.CredentialStatus disconnect(@PathVariable String system) {
        return vaultSettingsService.disconnect(system);
    }

    public record SaveRequest(@NotBlank String system, @NotBlank String apiKey) {
    }
}
