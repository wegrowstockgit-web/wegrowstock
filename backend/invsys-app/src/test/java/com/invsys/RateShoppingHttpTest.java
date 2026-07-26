package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.domain.ShippingCarton;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.catalog.repository.ShippingCartonRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RateShoppingHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired ShippingCartonRepository shippingCartonRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rateShopReturnsRankedQuotesAndAutoBuyCreatesLabel() throws Exception {
        String slug = "rate-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Rate Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        ShippingCarton carton = new ShippingCarton();
        carton.setTenantId(tenantId);
        carton.setName("Small");
        carton.setLength(new BigDecimal("12"));
        carton.setWidth(new BigDecimal("10"));
        carton.setHeight(new BigDecimal("8"));
        carton.setMaxWeight(new BigDecimal("50"));
        carton.setEmptyWeight(BigDecimal.ZERO);
        carton.setActive(true);
        carton = shippingCartonRepository.save(carton);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("RATE");
        product.setName("Rate Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("RATE-1");
        variant.setLength(new BigDecimal("4"));
        variant.setWidth(new BigDecimal("4"));
        variant.setHeight(new BigDecimal("4"));
        variant.setDimUnit("in");
        variant.setWeight(new BigDecimal("1"));
        variant.setWeightUnit("lb");
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Ship To Co");
        customer.setShippingAddress(Map.of(
                "street", "100 Main St",
                "city", "Austin",
                "state", "TX",
                "postalCode", "78701",
                "country", "US"));
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-RATE-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("1"));
        line.setQtyShipped(BigDecimal.ZERO);
        salesOrderLineRepository.save(line);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/shipments/rate-shop")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesOrderId\":\"" + order.getId() + "\",\"cartonId\":\"" + carton.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rates").isArray())
                .andExpect(jsonPath("$.recommended").exists())
                .andExpect(jsonPath("$.billableWeightLb").exists());

        mockMvc.perform(post("/api/v1/shipments/auto-buy-label")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesOrderId\":\"" + order.getId() + "\",\"cartonId\":\"" + carton.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LABEL_CREATED"))
                .andExpect(jsonPath("$.trackingNumber").exists())
                .andExpect(jsonPath("$.labelRef").exists());
    }
}
