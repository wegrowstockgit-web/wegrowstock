package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantIdOrderByNameAsc(UUID tenantId);
}
