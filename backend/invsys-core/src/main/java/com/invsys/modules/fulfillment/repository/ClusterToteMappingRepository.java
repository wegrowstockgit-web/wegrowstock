package com.invsys.modules.fulfillment.repository;

import com.invsys.modules.fulfillment.domain.ClusterToteMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClusterToteMappingRepository extends JpaRepository<ClusterToteMapping, UUID> {

    List<ClusterToteMapping> findByTenantIdAndBatchId(UUID tenantId, UUID batchId);

    void deleteByTenantIdAndBatchId(UUID tenantId, UUID batchId);
}
