package com.invsys.modules.sales.service;

import com.invsys.modules.sales.api.CustomerLookup;
import com.invsys.modules.sales.api.InvoiceLookup;
import com.invsys.modules.sales.api.SalesOrderLineLookup;
import com.invsys.modules.sales.api.SalesOrderLookup;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class InvoiceLookupAdapter implements InvoiceLookup {

    private final InvoiceRepository repository;

    InvoiceLookupAdapter(InvoiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}

@Component
class SalesOrderLineLookupAdapter implements SalesOrderLineLookup {

    private final SalesOrderLineRepository repository;

    SalesOrderLineLookupAdapter(SalesOrderLineRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SalesOrderLine> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<SalesOrderLine> findBySalesOrderId(UUID salesOrderId) {
        return repository.findBySalesOrderId(salesOrderId);
    }

    @Override
    public SalesOrderLine save(SalesOrderLine line) {
        return repository.save(line);
    }
}

@Component
class SalesOrderLookupAdapter implements SalesOrderLookup {

    private final SalesOrderRepository repository;

    SalesOrderLookupAdapter(SalesOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SalesOrder> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public SalesOrder save(SalesOrder order) {
        return repository.save(order);
    }
}

@Component
class CustomerLookupAdapter implements CustomerLookup {

    private final CustomerRepository repository;

    CustomerLookupAdapter(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return repository.findById(id);
    }
}
