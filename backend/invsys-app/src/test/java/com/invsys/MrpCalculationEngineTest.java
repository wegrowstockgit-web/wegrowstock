package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.MrpCalculationEngine;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MrpCalculationEngineTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired MrpCalculationEngine mrpCalculationEngine;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void suggestionsReflectOpenSalesAndSafetyStock() throws Exception {
        String slug = "mrp-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "MRP Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-M");
        wh.setName("WH-M");
        wh.setPath("/WH-M");
        wh = locationRepository.save(wh);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("MRP Supplier");
        supplier.setMinimumOrderQuantityValue(new BigDecimal("10"));
        supplier.setDefaultLeadTimeDays(7);
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("MRP");
        product.setName("MRP Item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("MRP-1");
        variant.setSafetyStock(new BigDecimal("5"));
        variant.setDefaultSupplierId(supplier.getId());
        variant.setAvgCost(new BigDecimal("4.50"));
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("MRP Buyer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-MRP-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("20"));
        salesOrderLineRepository.save(line);

        MrpCalculationEngine.MrpSuggestion suggestion = mrpCalculationEngine.calculateSuggestions().stream()
                .filter(s -> "MRP-1".equals(s.sku()))
                .findFirst()
                .orElseThrow();
        assertThat(suggestion.netRequirement()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(suggestion.suggestedOrderQty()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(suggestion.capitalEstimate()).isEqualByComparingTo(new BigDecimal("112.5"));
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/purchasing/mrp/suggestions")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("MRP-1"))
                .andExpect(jsonPath("$[0].netRequirement").value(25.0));

        mockMvc.perform(post("/api/v1/purchasing/mrp/calculate")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdPurchaseOrders[0].number").value(org.hamcrest.Matchers.startsWith("PO-MRP-")))
                .andExpect(jsonPath("$.suggestions[0].suggestedOrderQty").value(25.0));
    }
}
