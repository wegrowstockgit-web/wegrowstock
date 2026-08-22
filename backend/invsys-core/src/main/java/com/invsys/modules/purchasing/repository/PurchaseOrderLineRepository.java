package com.invsys.modules.purchasing.repository;

import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPurchaseOrderId(UUID purchaseOrderId);

    List<PurchaseOrderLine> findByPurchaseOrderIdIn(Collection<UUID> purchaseOrderIds);
}
