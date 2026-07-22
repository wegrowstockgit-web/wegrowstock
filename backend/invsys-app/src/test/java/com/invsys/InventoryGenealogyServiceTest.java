package com.invsys;

import com.invsys.api.dto.LotTraceResponse;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Lot;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.LotRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.InventoryGenealogyService;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.modules.fulfillment.service.ShipmentService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryGenealogyServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired InventoryGenealogyService genealogyService;
    @Autowired InventoryService inventoryService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired ShipmentService shipmentService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired LotRepository lotRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void upstreamAndDownstreamTraceForLot() {
        UUID tenantId = testDataHelper.createTenant("Genealogy", "gene-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("LOT");
        product.setName("Lot Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("LOT-1");
        // Genealogy traces ledger rows by lot_id; graceful lot handling nulls lot when untracked.
        variant.setLotTracked(true);
        variant = variantRepository.save(variant);

        Location warehouse = new Location();
        warehouse.setTenantId(tenantId);
        warehouse.setType("WAREHOUSE");
        warehouse.setCode("WH-LOT");
        warehouse.setName("Lot WH");
        warehouse.setPath("/WH-LOT");
        warehouse = locationRepository.save(warehouse);

        Lot lot = new Lot();
        lot.setTenantId(tenantId);
        lot.setVariantId(variant.getId());
        lot.setLotNumber("LOT-TRACE-001");
        lot = lotRepository.save(lot);

        inventoryService.receive(variant.getId(), warehouse.getId(), lot.getId(), new BigDecimal("5"),
                "PURCHASE_ORDER_LINE", null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Recall Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-LOT-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line = salesOrderLineRepository.save(line);

        salesOrderService.allocate(order.getId());
        shipmentService.createShipment(order.getId(), "UPS", "1ZLOT", List.of(
                new ShipmentService.ShipLineRequest(line.getId(), new BigDecimal("2"))));

        LotTraceResponse byId = genealogyService.traceByLotId(lot.getId());
        assertThat(byId.lotNumber()).isEqualTo("LOT-TRACE-001");
        assertThat(byId.upstream().children()).isNotEmpty();
        assertThat(byId.upstream().children().stream().anyMatch(n -> "RECEIVE".equals(n.type()))).isTrue();
        assertThat(byId.downstream().children()).isNotEmpty();
        assertThat(byId.downstream().children().stream().anyMatch(n -> "SHIP".equals(n.type()))).isTrue();

        LotTraceResponse byNumber = genealogyService.traceByLotNumber("LOT-TRACE-001");
        assertThat(byNumber.lotId()).isEqualTo(lot.getId());
    }
}
