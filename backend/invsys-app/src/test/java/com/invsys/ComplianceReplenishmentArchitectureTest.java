package com.invsys;

import com.invsys.api.dto.ComplianceLotTraceResponse;
import com.invsys.api.dto.ReplenishmentTaskDto;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.domain.BinReplenishmentRule;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Lot;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.repository.BinReplenishmentRuleRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.LotRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.InventoryGenealogyService;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.ReplenishmentService;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.modules.fulfillment.service.ShipmentService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@AutoConfigureMockMvc
class ComplianceReplenishmentArchitectureTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired InventoryGenealogyService genealogyService;
    @Autowired ReplenishmentService replenishmentService;
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
    @Autowired BinReplenishmentRuleRepository ruleRepository;
    @Autowired TestDataHelper testDataHelper;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void complianceLotTraceReturnsOriginExposureAndDownstream() throws Exception {
        UUID tenantId = testDataHelper.createTenant("Compliance", "cmp-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("CMP");
        product.setName("Compliance SKU");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("CMP-1");
        variant.setLotTracked(true);
        variant = variantRepository.save(variant);

        Location warehouse = location("WH-C", "WAREHOUSE", "STANDARD", null, tenantId);
        Location pick = location("PF-1", "BIN", "PICK_FACE", warehouse.getId(), tenantId);
        pick.setPath(warehouse.getPath() + "/PF-1");
        pick = locationRepository.save(pick);

        Lot lot = new Lot();
        lot.setTenantId(tenantId);
        lot.setVariantId(variant.getId());
        lot.setLotNumber("LOT-CMP-001");
        lot = lotRepository.save(lot);

        inventoryService.receive(variant.getId(), pick.getId(), lot.getId(), new BigDecimal("8"),
                "PURCHASE_ORDER_LINE", null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Recall Buyer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-CMP-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line = salesOrderLineRepository.save(line);

        salesOrderService.allocate(order.getId());
        shipmentService.createShipment(order.getId(), "UPS", "1ZCMP", List.of(
                new ShipmentService.ShipLineRequest(line.getId(), new BigDecimal("2"))));

        ComplianceLotTraceResponse trace = genealogyService.complianceTrace(null, "LOT-CMP-001");
        assertThat(trace.lotNumber()).isEqualTo("LOT-CMP-001");
        assertThat(trace.origin()).isNotNull();
        assertThat(trace.origin().quantity()).isEqualByComparingTo("8");
        assertThat(trace.currentExposure()).isNotEmpty();
        assertThat(trace.currentExposure().getFirst().zoneBehavior()).isEqualTo("PICK_FACE");
        assertThat(trace.downstream()).isNotEmpty();
        assertThat(trace.downstream().getFirst().customerName()).isEqualTo("Recall Buyer");

        String slug = "cmpapi-" + UUID.randomUUID().toString().substring(0, 8);
        var tokens = authService.signup(new SignupRequest(
                "Cmp API", slug, "owner@" + slug + ".test", "password123", "Owner"));

        // Seed lot on the signup tenant is separate — hit genealogy on current tenant via service already.
        // API smoke: validation requires a param.
        mockMvc.perform(get("/api/v1/compliance/lot-trace")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replenishmentSuggestsReserveToPickFaceTransferAndConfirmPostsLedger() throws Exception {
        String slug = "repl-" + UUID.randomUUID().toString().substring(0, 8);
        var tokens = authService.signup(new SignupRequest(
                "Repl Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("RPL");
        product.setName("Replenish item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("RPL-1");
        final UUID variantId = variantRepository.save(variant).getId();

        Location warehouse = location("WH-R", "WAREHOUSE", "STANDARD", null, tenantId);
        Location reserve = location("RSV-1", "BIN", "RESERVE", warehouse.getId(), tenantId);
        reserve.setPath(warehouse.getPath() + "/RSV-1");
        final UUID reserveId = locationRepository.save(reserve).getId();
        Location pick = location("PF-R", "BIN", "PICK_FACE", warehouse.getId(), tenantId);
        pick.setPath(warehouse.getPath() + "/PF-R");
        final UUID pickId = locationRepository.save(pick).getId();

        inventoryService.receive(variantId, reserveId, null, new BigDecimal("50"),
                "TEST", null, new BigDecimal("1.00"));
        inventoryService.receive(variantId, pickId, null, new BigDecimal("2"),
                "TEST", null, new BigDecimal("1.00"));

        BinReplenishmentRule rule = new BinReplenishmentRule();
        rule.setTenantId(tenantId);
        rule.setLocationId(pickId);
        rule.setVariantId(variantId);
        rule.setMinQuantity(new BigDecimal("10"));
        rule.setMaxQuantity(new BigDecimal("40"));
        ruleRepository.save(rule);

        List<ReplenishmentTaskDto> tasks = replenishmentService.listSuggestedTransfers();
        assertThat(tasks).isNotEmpty();
        ReplenishmentTaskDto task = tasks.stream()
                .filter(t -> t.variantId().equals(variantId))
                .findFirst()
                .orElseThrow();
        assertThat(task.fromLocationId()).isEqualTo(reserveId);
        assertThat(task.toLocationId()).isEqualTo(pickId);
        assertThat(task.suggestedQuantity()).isEqualByComparingTo("38");
        assertThat(task.instruction()).contains("Move").contains("RPL-1");

        mockMvc.perform(get("/api/v1/warehouse/replenishments")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("RPL-1"));

        String body = """
                {
                  "variantId": "%s",
                  "fromLocationId": "%s",
                  "toLocationId": "%s",
                  "quantity": 5
                }
                """.formatted(variantId, reserveId, pickId);

        mockMvc.perform(post("/api/v1/warehouse/replenishments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferGroupId").isNotEmpty());
    }

    private Location location(String code, String type, String zoneBehavior, UUID parentId, UUID tenantId) {
        Location loc = new Location();
        loc.setTenantId(tenantId);
        loc.setType(type);
        loc.setCode(code);
        loc.setName(code);
        loc.setPath(code);
        loc.setZoneBehavior(zoneBehavior);
        loc.setParentLocationId(parentId);
        loc.setSequenceIndex(0);
        return locationRepository.save(loc);
    }
}
