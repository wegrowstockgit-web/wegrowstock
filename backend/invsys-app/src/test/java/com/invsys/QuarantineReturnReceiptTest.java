package com.invsys;

import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.ReturnLine;
import com.invsys.domain.ReturnOrder;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.ReturnLineRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.ReturnService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarantineReturnReceiptTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired ReturnService returnService;
    @Autowired ReturnLineRepository returnLineRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void processReceiptRejectsNonQuarantineDestinationForInspection() {
        UUID tenantId = testDataHelper.createTenant("RMA Quar", "rmq-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("RMQ");
        product.setName("Return Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("RMQ-1");
        variant = variantRepository.save(variant);

        Location quarantine = new Location();
        quarantine.setTenantId(tenantId);
        quarantine.setType("QUARANTINE");
        quarantine.setCode("QUAR-RMA");
        quarantine.setName("Returns Quarantine");
        quarantine.setPath("/QUAR-RMA");
        quarantine = locationRepository.save(quarantine);

        Location pickBin = new Location();
        pickBin.setTenantId(tenantId);
        pickBin.setType("BIN");
        pickBin.setCode("BIN-RMA");
        pickBin.setName("Pick Bin");
        pickBin.setPath("/BIN-RMA");
        pickBin = locationRepository.save(pickBin);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("RMA Customer");
        customer = customerRepository.save(customer);

        SalesOrder so = new SalesOrder();
        so.setTenantId(tenantId);
        so.setCustomerId(customer.getId());
        so.setNumber("SO-RMQ-1");
        so.setStatus("SHIPPED");
        so = salesOrderRepository.save(so);

        SalesOrderLine sol = new SalesOrderLine();
        sol.setTenantId(tenantId);
        sol.setSalesOrderId(so.getId());
        sol.setVariantId(variant.getId());
        sol.setQtyOrdered(new BigDecimal("2"));
        sol.setQtyShipped(new BigDecimal("2"));
        sol.setUnitPrice(new BigDecimal("10.00"));
        sol = salesOrderLineRepository.save(sol);

        ReturnOrder rma = returnService.create(so.getId(), List.of(
                new ReturnService.ReturnLineInput(sol.getId(), new BigDecimal("2"))));
        returnService.approve(rma.getId());
        ReturnLine line = returnLineRepository.findByReturnId(rma.getId()).getFirst();

        Location finalPick = pickBin;
        assertThatThrownBy(() -> returnService.processReceipt(line.getId(), finalPick.getId(), "QUARANTINE"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(api.getCode()).isEqualTo("QUARANTINE_LOCATION_REQUIRED");
                });

        ReturnLine received = returnService.processReceipt(line.getId(), quarantine.getId(), "QUARANTINE");
        assertThat(received.getQuantityReceived()).isEqualByComparingTo("2");
        assertThat(received.getDisposition()).isEqualTo("QUARANTINE");
    }
}
