package com.invsys.repository;

import com.invsys.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByProviderAndExternalId(String provider, String externalId);
    Optional<PaymentIntent> findByInvoiceId(UUID invoiceId);
    List<PaymentIntent> findByInvoiceIdIn(Collection<UUID> invoiceIds);
}
