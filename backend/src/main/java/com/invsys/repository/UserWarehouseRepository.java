package com.invsys.repository;

import com.invsys.domain.UserWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserWarehouseRepository extends JpaRepository<UserWarehouse, UUID> {
    List<UserWarehouse> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
