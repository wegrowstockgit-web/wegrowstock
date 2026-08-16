package com.invsys.service.modulith;

import com.invsys.modules.fulfillment.repository.ShipmentLineRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.modules.sales.api.ShipmentInvoiceSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShipmentInvoiceSourceAdapter implements ShipmentInvoiceSource {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;

    public ShipmentInvoiceSourceAdapter(ShipmentRepository shipmentRepository,
                                        ShipmentLineRepository shipmentLineRepository) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
    }

    @Override
    public Optional<ShipmentRef> findById(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .map(s -> new ShipmentRef(s.getId(), s.getTenantId(), s.getSalesOrderId()));
    }

    @Override
    public List<LineQty> findLines(UUID shipmentId) {
        return shipmentLineRepository.findByShipmentId(shipmentId).stream()
                .map(line -> new LineQty(line.getSalesOrderLineId(), line.getQuantity()))
                .toList();
    }
}
