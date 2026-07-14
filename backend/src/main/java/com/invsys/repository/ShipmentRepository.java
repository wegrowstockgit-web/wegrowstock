package com.invsys.repository;

import com.invsys.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    List<Shipment> findBySalesOrderId(UUID salesOrderId);
}
