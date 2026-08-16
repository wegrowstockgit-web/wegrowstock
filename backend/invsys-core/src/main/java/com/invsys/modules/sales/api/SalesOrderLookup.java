package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.SalesOrder;

import java.util.Optional;
import java.util.UUID;

public interface SalesOrderLookup {

    Optional<SalesOrder> findById(UUID id);

    SalesOrder save(SalesOrder order);
}
