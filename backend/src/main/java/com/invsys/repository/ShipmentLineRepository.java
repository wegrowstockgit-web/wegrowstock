package com.invsys.repository;

import com.invsys.domain.ShipmentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, UUID> {
    List<ShipmentLine> findByShipmentId(UUID shipmentId);
}
