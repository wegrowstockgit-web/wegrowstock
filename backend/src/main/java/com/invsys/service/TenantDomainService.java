package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.TenantDomain;
import com.invsys.repository.TenantDomainRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantDomainService {

    private final TenantDomainRepository tenantDomainRepository;

    public TenantDomainService(TenantDomainRepository tenantDomainRepository) {
        this.tenantDomainRepository = tenantDomainRepository;
    }

    public List<TenantDomain> list() {
        return tenantDomainRepository.findByTenantIdOrderByDomainNameAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public TenantDomain register(String domainName) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantDomain domain = new TenantDomain();
        domain.setTenantId(tenantId);
        domain.setDomainName(domainName.toLowerCase().trim());
        domain.setVerificationStatus("PENDING");
        domain.setDkimTokens(List.of(
                Map.of("type", "CNAME", "host", "dkim1._domainkey." + domainName, "value", "dkim1.invsys.mail.example"),
                Map.of("type", "CNAME", "host", "dkim2._domainkey." + domainName, "value", "dkim2.invsys.mail.example")
        ));
        return tenantDomainRepository.save(domain);
    }

    @Transactional
    public TenantDomain verify(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantDomain domain = tenantDomainRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Domain not found"));
        domain.setVerificationStatus("VERIFIED");
        return tenantDomainRepository.save(domain);
    }
}
