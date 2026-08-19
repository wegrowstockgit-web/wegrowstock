package com.invsys.api;

import com.invsys.api.dto.CurrentNetworkInfoResponse;
import com.invsys.core.security.ClientIpResolver;
import com.invsys.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class SettingsController {

    private final SettingsService settingsService;
    private final ClientIpResolver clientIpResolver;

    public SettingsController(SettingsService settingsService, ClientIpResolver clientIpResolver) {
        this.settingsService = settingsService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        return settingsService.getSettings();
    }

    @PatchMapping
    public Map<String, Object> patchSettings(@RequestBody Map<String, Object> patch) {
        return settingsService.patchSettings(patch);
    }

    @PutMapping
    public Map<String, Object> putSettings(@RequestBody Map<String, Object> body) {
        return settingsService.patchSettings(body);
    }

    /** Force-invalidate Redis/local tenant settings cache after ops policy changes. */
    @PostMapping("/cache/flush")
    public Map<String, Object> flushCache() {
        return settingsService.flushCache();
    }

    @GetMapping("/network/current-ip")
    public CurrentNetworkInfoResponse currentNetwork(HttpServletRequest request) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return new CurrentNetworkInfoResponse(
                clientIp,
                ClientIpResolver.suggestedCidr(clientIp),
                ClientIpResolver.isPrivateNetwork(clientIp),
                ClientIpResolver.networkHint(clientIp));
    }
}
