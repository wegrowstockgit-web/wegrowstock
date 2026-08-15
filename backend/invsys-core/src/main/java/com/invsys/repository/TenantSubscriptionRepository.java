package com.invsys.repository;

import com.invsys.domain.subscription.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {
}
