package com.invsys.integration.repository;

import com.invsys.integration.domain.IntegrationRateBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRateBucketRepository extends JpaRepository<IntegrationRateBucket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM IntegrationRateBucket b WHERE b.tenantId = :tenantId AND b.system = :system")
    Optional<IntegrationRateBucket> findForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("system") String system);
}
