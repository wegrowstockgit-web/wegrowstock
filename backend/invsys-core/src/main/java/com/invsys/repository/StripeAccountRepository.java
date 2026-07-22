package com.invsys.repository;

import com.invsys.domain.StripeAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StripeAccountRepository extends JpaRepository<StripeAccount, UUID> {
    Optional<StripeAccount> findByTenantId(UUID tenantId);
}
