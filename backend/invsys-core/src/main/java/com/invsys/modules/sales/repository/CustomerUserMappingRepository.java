package com.invsys.modules.sales.repository;

import com.invsys.domain.CustomerUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerUserMappingRepository extends JpaRepository<CustomerUserMapping, UUID> {
    Optional<CustomerUserMapping> findByUserId(UUID userId);
}
