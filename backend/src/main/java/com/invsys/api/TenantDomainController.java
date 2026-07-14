package com.invsys.api;

import com.invsys.domain.TenantDomain;
import com.invsys.service.TenantDomainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/email-domains")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class TenantDomainController {

    private final TenantDomainService tenantDomainService;

    public TenantDomainController(TenantDomainService tenantDomainService) {
        this.tenantDomainService = tenantDomainService;
    }

    @GetMapping
    public List<DomainResponse> list() {
        return tenantDomainService.list().stream().map(DomainResponse::from).toList();
    }

    @PostMapping
    public DomainResponse register(@Valid @RequestBody RegisterDomainRequest request) {
        return DomainResponse.from(tenantDomainService.register(request.domainName()));
    }

    @PostMapping("/{id}/verify")
    public DomainResponse verify(@PathVariable UUID id) {
        return DomainResponse.from(tenantDomainService.verify(id));
    }

    public record RegisterDomainRequest(@NotBlank String domainName) {
    }

    public record DomainResponse(
            UUID id,
            String domainName,
            String verificationStatus,
            List<Map<String, String>> dkimTokens
    ) {
        static DomainResponse from(TenantDomain domain) {
            return new DomainResponse(
                    domain.getId(),
                    domain.getDomainName(),
                    domain.getVerificationStatus(),
                    domain.getDkimTokens());
        }
    }
}
