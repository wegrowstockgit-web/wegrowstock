package com.invsys.repository;

import com.invsys.domain.SupplierUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierUserMappingRepository extends JpaRepository<SupplierUserMapping, UUID> {
    Optional<SupplierUserMapping> findByUserId(UUID userId);
}
