package com.invsys.repository;

import com.invsys.domain.MagicLoginToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MagicLoginTokenRepository extends JpaRepository<MagicLoginToken, UUID> {
    Optional<MagicLoginToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE MagicLoginToken t
            SET t.consumedAt = :now
            WHERE t.tokenHash = :hash AND t.consumedAt IS NULL
            """)
    int consumeIfUnused(@Param("hash") String hash, @Param("now") Instant now);
}
