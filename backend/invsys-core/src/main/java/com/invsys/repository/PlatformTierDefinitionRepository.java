package com.invsys.repository;

import com.invsys.domain.subscription.PlatformTierDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformTierDefinitionRepository extends JpaRepository<PlatformTierDefinition, String> {

    List<PlatformTierDefinition> findAllByOrderByTierCodeAsc();
}
