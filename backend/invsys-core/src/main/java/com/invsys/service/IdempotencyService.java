package com.invsys.service;

import com.invsys.domain.IdempotencyKey;
import com.invsys.repository.IdempotencyKeyRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public Optional<StoredResponse> find(String key) {
        return repository.findByTenantIdAndKey(TenantContext.requireTenantId(), key)
                .filter(k -> k.getExpiresAt().isAfter(Instant.now()))
                .filter(k -> k.getResponseStatus() != null)
                .map(k -> new StoredResponse(k.getResponseStatus(), k.getResponseBody()));
    }

    @Transactional
    public void store(String key, String requestHash, int status, Map<String, Object> body) {
        IdempotencyKey entity = repository.findByTenantIdAndKey(TenantContext.requireTenantId(), key)
                .orElseGet(() -> {
                    IdempotencyKey created = new IdempotencyKey();
                    created.setTenantId(TenantContext.requireTenantId());
                    created.setKey(key);
                    created.setExpiresAt(Instant.now().plusSeconds(86400));
                    return created;
                });
        entity.setRequestHash(requestHash);
        entity.setResponseStatus(status);
        entity.setResponseBody(body);
        repository.save(entity);
    }

    public record StoredResponse(int status, Map<String, Object> body) {
    }
}
