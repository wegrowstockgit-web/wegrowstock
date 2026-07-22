package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.dns.DnsTxtLookup;
import com.invsys.domain.TenantDomain;
import com.invsys.gateway.DynamicCorsWhitelist;
import com.invsys.repository.TenantDomainRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDomainDnsVerifyTest {

    @Mock TenantDomainRepository tenantDomainRepository;
    @Mock BootstrapJdbc bootstrapJdbc;
    @Mock DnsTxtLookup dnsTxtLookup;
    @Mock DynamicCorsWhitelist dynamicCorsWhitelist;

    TenantDomainService service;
    UUID tenantId;
    UUID domainId;

    @BeforeEach
    void setUp() {
        service = new TenantDomainService(tenantDomainRepository, bootstrapJdbc, dnsTxtLookup, dynamicCorsWhitelist);
        tenantId = UUID.randomUUID();
        domainId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        when(bootstrapJdbc.findVerifiedDomainOwner(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void verifyMarksActiveWhenTxtPresent() throws Exception {
        TenantDomain domain = new TenantDomain();
        domain.setId(domainId);
        domain.setTenantId(tenantId);
        domain.setDomainName("acme.example");
        domain.setVerificationStatus("PENDING");
        when(tenantDomainRepository.findByTenantIdAndId(tenantId, domainId)).thenReturn(Optional.of(domain));
        when(dnsTxtLookup.lookupTxt("acme.example"))
                .thenReturn(List.of("growstock-verification=" + tenantId));
        when(tenantDomainRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantDomain verified = service.verify(domainId);
        assertThat(verified.getVerificationStatus()).isEqualTo("ACTIVE");
        verify(dynamicCorsWhitelist).invalidate();
    }

    @Test
    void verifyFailsWhenTxtMissing() throws Exception {
        TenantDomain domain = new TenantDomain();
        domain.setId(domainId);
        domain.setTenantId(tenantId);
        domain.setDomainName("acme.example");
        domain.setVerificationStatus("PENDING");
        when(tenantDomainRepository.findByTenantIdAndId(tenantId, domainId)).thenReturn(Optional.of(domain));
        when(dnsTxtLookup.lookupTxt("acme.example")).thenReturn(List.of("unrelated=1"));
        when(tenantDomainRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verify(domainId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("DNS_TXT_MISSING");

        ArgumentCaptor<TenantDomain> captor = ArgumentCaptor.forClass(TenantDomain.class);
        verify(tenantDomainRepository).save(captor.capture());
        assertThat(captor.getValue().getVerificationStatus()).isEqualTo("FAILED");
    }
}
