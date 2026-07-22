package com.invsys.repository;

import com.invsys.domain.DocumentSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ds FROM DocumentSequence ds WHERE ds.tenantId = :tenantId AND ds.docType = :docType AND ds.period = :period")
    Optional<DocumentSequence> findForUpdate(UUID tenantId, String docType, String period);
}
