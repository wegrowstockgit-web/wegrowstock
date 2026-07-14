package com.invsys.api;

import com.invsys.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        return settingsService.getSettings();
    }

    @PatchMapping
    public Map<String, Object> patchSettings(@RequestBody Map<String, Object> patch) {
        return settingsService.patchSettings(patch);
    }
}
