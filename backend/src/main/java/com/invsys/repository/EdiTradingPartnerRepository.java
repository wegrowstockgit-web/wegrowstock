package com.invsys.repository;

import com.invsys.domain.EdiTradingPartner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EdiTradingPartnerRepository extends JpaRepository<EdiTradingPartner, UUID> {
    List<EdiTradingPartner> findByTenantId(UUID tenantId);
}
