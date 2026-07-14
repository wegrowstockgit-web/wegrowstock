package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.AccountMapping;
import com.invsys.repository.AccountMappingRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountMappingService {

    private final AccountMappingRepository repository;

    public AccountMappingService(AccountMappingRepository repository) {
        this.repository = repository;
    }

    public List<AccountMapping> list(String system) {
        UUID tenantId = TenantContext.requireTenantId();
        if (system == null || system.isBlank()) {
            return repository.findByTenantIdOrderBySystemAscAccountTypeAsc(tenantId);
        }
        return repository.findByTenantIdAndSystem(tenantId, system);
    }

    @Transactional
    public List<AccountMapping> upsertAll(List<UpsertInput> mappings) {
        return mappings.stream()
                .map(m -> upsert(m.system(), m.accountType(), m.externalAccountId()))
                .toList();
    }

    @Transactional
    public AccountMapping upsert(String system, String accountType, String externalAccountId) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountMapping mapping = repository.findByTenantIdAndSystemAndAccountType(tenantId, system, accountType)
                .orElseGet(() -> {
                    AccountMapping created = new AccountMapping();
                    created.setTenantId(tenantId);
                    created.setSystem(system);
                    created.setAccountType(accountType);
                    return created;
                });
        mapping.setExternalAccountId(externalAccountId);
        return repository.save(mapping);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Mapping not found");
        }
        repository.deleteById(id);
    }

    public record UpsertInput(String system, String accountType, String externalAccountId) {
    }
}
