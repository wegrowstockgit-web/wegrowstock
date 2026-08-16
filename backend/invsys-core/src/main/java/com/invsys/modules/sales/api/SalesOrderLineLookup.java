package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.SalesOrderLine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderLineLookup {

    Optional<SalesOrderLine> findById(UUID id);

    List<SalesOrderLine> findBySalesOrderId(UUID salesOrderId);

    SalesOrderLine save(SalesOrderLine line);
}
