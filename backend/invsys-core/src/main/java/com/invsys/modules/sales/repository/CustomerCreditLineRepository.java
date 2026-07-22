package com.invsys.modules.sales.repository;

import com.invsys.domain.CustomerCreditLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerCreditLineRepository extends JpaRepository<CustomerCreditLine, UUID> {
    Optional<CustomerCreditLine> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
