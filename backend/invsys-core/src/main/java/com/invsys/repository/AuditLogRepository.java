package com.invsys.repository;

import com.invsys.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.invsys.core.tenancy.TenantContext;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndEntityId(UUID tenantId, UUID entityId);

    List<AuditLog> findByTenantIdAndEntityTypeInAndEntityIdOrderByCreatedAtDescIdDesc(
            UUID tenantId,
            Collection<String> entityTypes,
            UUID entityId,
            Pageable pageable);

    List<AuditLog> findByTenantIdAndEntityIdAndActionOrderByCreatedAtDesc(
            UUID tenantId,
            UUID entityId,
            String action,
            Pageable pageable);

    /**
     * Aged rows for cold archival. Call with {@code TenantContext} bound so RLS
     * scopes the page to the current tenant.
     */
    List<AuditLog> findByCreatedAtBefore(Instant cutoffDate, Pageable pageable);

    /**
     * Purges archived rows via SECURITY DEFINER {@code archive_purge_audit_logs}
     * (app_user has no direct DELETE on {@code audit_log}).
     */
    default int deleteByIdIn(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String idCsv = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        Integer deleted = purgeArchivedByIdCsv(idCsv);
        return deleted != null ? deleted : 0;
    }

    /**
     * Side-effecting SELECT of SECURITY DEFINER purge function (not a JPA DELETE).
     */
    @Query(value = """
            SELECT archive_purge_audit_logs(CAST(string_to_array(:idCsv, ',') AS uuid[]))
            """, nativeQuery = true)
    Integer purgeArchivedByIdCsv(@Param("idCsv") String idCsv);

    @Query("""
            SELECT a FROM AuditLog a
             WHERE a.tenantId = :tenantId
               AND (:entityType IS NULL OR upper(a.entityType) = :entityType)
               AND (:action IS NULL OR upper(a.action) = :action)
             ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<AuditLog> findTenantFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
             WHERE a.tenantId = :tenantId
               AND (:entityType IS NULL OR upper(a.entityType) = :entityType)
               AND (:action IS NULL OR upper(a.action) = :action)
               AND (
                    a.createdAt < :cursorCreatedAt
                    OR (a.createdAt = :cursorCreatedAt AND a.id < :cursorId)
               )
             ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<AuditLog> findTenantAfterCursor(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
