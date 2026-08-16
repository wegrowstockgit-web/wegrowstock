package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerLookup {

    Optional<Customer> findById(UUID id);
}
