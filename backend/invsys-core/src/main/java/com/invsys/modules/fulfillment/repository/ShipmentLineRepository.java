package com.invsys.modules.fulfillment.repository;

import com.invsys.modules.fulfillment.domain.ShipmentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, UUID> {
    List<ShipmentLine> findByShipmentId(UUID shipmentId);
}
