package com.invsys.repository;

import com.invsys.domain.BillingAccrual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingAccrualRepository extends JpaRepository<BillingAccrual, UUID> {
    List<BillingAccrual> findByTenantIdAndCustomerIdAndStatusOrderByAccrualDateDesc(
            UUID tenantId, UUID customerId, String status);

    List<BillingAccrual> findByTenantIdAndCustomerIdAndAccrualDateGreaterThanEqualAndStatusOrderByAccrualDateDesc(
            UUID tenantId, UUID customerId, LocalDate fromInclusive, String status);

    Optional<BillingAccrual> findByTenantIdAndCustomerIdAndAccrualDateAndDescription(
            UUID tenantId, UUID customerId, LocalDate accrualDate, String description);
}
