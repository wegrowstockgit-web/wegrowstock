package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.dns.DnsTxtLookup;
import com.invsys.domain.TenantDomain;
import com.invsys.gateway.DynamicCorsWhitelist;
import com.invsys.repository.TenantDomainRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantDomainService {

    public static final String TXT_PREFIX = "growstock-verification=";

    private final TenantDomainRepository tenantDomainRepository;
    private final BootstrapJdbc bootstrapJdbc;
    private final DnsTxtLookup dnsTxtLookup;
    private final DynamicCorsWhitelist dynamicCorsWhitelist;

    public TenantDomainService(TenantDomainRepository tenantDomainRepository,
                               BootstrapJdbc bootstrapJdbc,
                               DnsTxtLookup dnsTxtLookup,
                               DynamicCorsWhitelist dynamicCorsWhitelist) {
        this.tenantDomainRepository = tenantDomainRepository;
        this.bootstrapJdbc = bootstrapJdbc;
        this.dnsTxtLookup = dnsTxtLookup;
        this.dynamicCorsWhitelist = dynamicCorsWhitelist;
    }

    public List<TenantDomain> list() {
        return tenantDomainRepository.findByTenantIdOrderByDomainNameAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public TenantDomain register(String domainName) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalized = domainName.toLowerCase(Locale.ROOT).trim();
        assertDomainNotVerifiedElsewhere(normalized, tenantId);
        TenantDomain domain = new TenantDomain();
        domain.setTenantId(tenantId);
        domain.setDomainName(normalized);
        domain.setVerificationStatus("PENDING");
        domain.setVerified(false);
        String txtValue = TXT_PREFIX + tenantId;
        domain.setDnsVerificationToken(txtValue);
        List<Map<String, String>> tokens = new ArrayList<>();
        tokens.add(Map.of(
                "type", "TXT",
                "host", "@",
                "value", txtValue,
                "name", normalized));
        tokens.add(Map.of(
                "type", "CNAME",
                "host", "dkim1._domainkey." + normalized,
                "value", "dkim1.invsys.mail.example"));
        tokens.add(Map.of(
                "type", "CNAME",
                "host", "dkim2._domainkey." + normalized,
                "value", "dkim2.invsys.mail.example"));
        domain.setDkimTokens(tokens);
        return tenantDomainRepository.save(domain);
    }

    @Transactional
    public TenantDomain verify(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantDomain domain = tenantDomainRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Domain not found"));
        assertDomainNotVerifiedElsewhere(domain.getDomainName(), tenantId);

        String expected = TXT_PREFIX + tenantId;
        List<String> txtRecords;
        try {
            txtRecords = dnsTxtLookup.lookupTxt(domain.getDomainName());
        } catch (Exception ex) {
            domain.setVerificationStatus("FAILED");
            tenantDomainRepository.save(domain);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DNS_LOOKUP_FAILED",
                    "Could not resolve TXT records for " + domain.getDomainName())
                    .withProperty("expectedTxt", expected);
        }

        boolean matched = txtRecords.stream()
                .map(String::trim)
                .anyMatch(record -> record.equalsIgnoreCase(expected) || record.contains(expected));
        if (!matched) {
            domain.setVerificationStatus("FAILED");
            tenantDomainRepository.save(domain);
            throw new ApiException(HttpStatus.BAD_REQUEST, "DNS_TXT_MISSING",
                    "TXT record not found. Publish: " + expected)
                    .withProperty("expectedTxt", expected)
                    .withProperty("observedTxt", txtRecords);
        }

        domain.setVerificationStatus("ACTIVE");
        domain.setVerified(true);
        TenantDomain saved = tenantDomainRepository.save(domain);
        dynamicCorsWhitelist.invalidate();
        return saved;
    }

    private void assertDomainNotVerifiedElsewhere(String domainName, UUID tenantId) {
        bootstrapJdbc.findVerifiedDomainOwner(domainName).ifPresent(ownerTenantId -> {
            if (!ownerTenantId.equals(tenantId)) {
                throw new ApiException(HttpStatus.CONFLICT, "DOMAIN_IN_USE",
                        "This domain is already verified by another organization");
            }
        });
    }
}
