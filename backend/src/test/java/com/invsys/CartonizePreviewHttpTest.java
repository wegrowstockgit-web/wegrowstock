package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Customer;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.ShippingCarton;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.ShippingCartonRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CartonizePreviewHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired ShippingCartonRepository shippingCartonRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void cartonizePreviewReturnsFfdPackingPlacements() throws Exception {
        String slug = "ffd-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "FFD Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        ShippingCarton small = carton(tenantId, "Small Mailer", "8", "6", "4", "5");
        ShippingCarton medium = carton(tenantId, "Medium Corrugated", "14", "10", "8", "30");
        shippingCartonRepository.save(small);
        shippingCartonRepository.save(medium);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("FFD");
        product.setName("FFD Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("FFD-1");
        variant.setLength(new BigDecimal("6"));
        variant.setWidth(new BigDecimal("4"));
        variant.setHeight(new BigDecimal("3"));
        variant.setDimUnit("in");
        variant.setWeight(new BigDecimal("0.75"));
        variant.setWeightUnit("lb");
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Ship To");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-FFD-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line.setQtyShipped(BigDecimal.ZERO);
        salesOrderLineRepository.save(line);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/shipments/cartonize-preview")
                        .param("salesOrderId", order.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartonName").value("Medium Corrugated"))
                .andExpect(jsonPath("$.packing").isArray())
                .andExpect(jsonPath("$.packing.length()").value(2))
                .andExpect(jsonPath("$.packing[0].xIn").exists())
                .andExpect(jsonPath("$.packing[0].lengthIn").value(6.0))
                .andExpect(jsonPath("$.billableWeightLb").exists());
    }

    private static ShippingCarton carton(
            UUID tenantId, String name, String l, String w, String h, String max) {
        ShippingCarton c = new ShippingCarton();
        c.setTenantId(tenantId);
        c.setName(name);
        c.setLength(new BigDecimal(l));
        c.setWidth(new BigDecimal(w));
        c.setHeight(new BigDecimal(h));
        c.setMaxWeight(new BigDecimal(max));
        c.setEmptyWeight(new BigDecimal("0.5"));
        c.setDimUnit("in");
        c.setWeightUnit("lb");
        c.setActive(true);
        return c;
    }
}
