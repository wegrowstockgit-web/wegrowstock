package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReturnWorkspaceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void cursorPageCreateAndCompleteDraftsCreditMemo() throws Exception {
        String slug = "rmaws-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "RMA Workspace Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("RMAWS");
        product.setName("Return Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(owner.tenantId());
        variant.setProductId(product.getId());
        variant.setSku("RMAWS-1");
        variant = variantRepository.save(variant);

        Location bin = new Location();
        bin.setTenantId(owner.tenantId());
        bin.setType("BIN");
        bin.setCode("BIN-RMAWS");
        bin.setName("Restock Bin");
        bin.setPath("/BIN-RMAWS");
        bin = locationRepository.save(bin);

        Customer customer = new Customer();
        customer.setTenantId(owner.tenantId());
        customer.setName("Return Buyer");
        customer = customerRepository.save(customer);

        SalesOrder so = new SalesOrder();
        so.setTenantId(owner.tenantId());
        so.setCustomerId(customer.getId());
        so.setNumber("SO-RMAWS-1");
        so.setStatus("SHIPPED");
        so = salesOrderRepository.save(so);

        SalesOrderLine sol = new SalesOrderLine();
        sol.setTenantId(owner.tenantId());
        sol.setSalesOrderId(so.getId());
        sol.setVariantId(variant.getId());
        sol.setQtyOrdered(new BigDecimal("2"));
        sol.setQtyShipped(new BigDecimal("2"));
        sol.setUnitPrice(new BigDecimal("25.00"));
        sol = salesOrderLineRepository.save(sol);

        mockMvc.perform(get("/api/v1/returns")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "salesOrderId":"%s",
                                  "resolutionType":"REFUND_CREDIT_MEMO",
                                  "generateLabel":false,
                                  "lines":[{
                                    "salesOrderLineId":"%s",
                                    "quantityExpected":1,
                                    "reasonCode":"DAMAGED_IN_TRANSIT"
                                  }]
                                }
                                """.formatted(so.getId(), sol.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.resolutionType").value("REFUND_CREDIT_MEMO"))
                .andExpect(jsonPath("$.lines[0].reasonCode").value("DAMAGED_IN_TRANSIT"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        String returnId = created.get("id").asString();
        String lineId = created.get("lines").get(0).get("id").asString();

        mockMvc.perform(get("/api/v1/returns")
                        .param("limit", "25")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(returnId))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/v1/returns")
                        .param("page", "1")
                        .param("size", "25")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(returnId))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(put("/api/v1/returns/" + returnId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disposition":"RESTOCK","restockLocationId":"%s","restockingFeePct":10}
                                """.formatted(bin.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disposition").value("RESTOCK"));

        JsonNode closed = objectMapper.readTree(mockMvc.perform(post("/api/v1/returns/" + returnId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.creditMemoId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String creditMemoId = closed.get("creditMemoId").asString();
        assertThat(closed.get("creditMemoNumber").asString()).startsWith("CM-");

        mockMvc.perform(get("/api/v1/invoices/" + creditMemoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void completeWithoutRestockBinAndEscalateWithoutDefectReturnProblemDetails() throws Exception {
        String slug = "rmaerr-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "RMA Error Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("RMAERR");
        product.setName("Return Error Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(owner.tenantId());
        variant.setProductId(product.getId());
        variant.setSku("RMAERR-1");
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(owner.tenantId());
        customer.setName("Return Error Buyer");
        customer = customerRepository.save(customer);

        SalesOrder so = new SalesOrder();
        so.setTenantId(owner.tenantId());
        so.setCustomerId(customer.getId());
        so.setNumber("SO-RMAERR-1");
        so.setStatus("SHIPPED");
        so = salesOrderRepository.save(so);

        SalesOrderLine sol = new SalesOrderLine();
        sol.setTenantId(owner.tenantId());
        sol.setSalesOrderId(so.getId());
        sol.setVariantId(variant.getId());
        sol.setQtyOrdered(new BigDecimal("1"));
        sol.setQtyShipped(new BigDecimal("1"));
        sol.setUnitPrice(new BigDecimal("25.00"));
        sol = salesOrderLineRepository.save(sol);

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "salesOrderId":"%s",
                                  "resolutionType":"REFUND_CREDIT_MEMO",
                                  "generateLabel":false,
                                  "lines":[{
                                    "salesOrderLineId":"%s",
                                    "quantityExpected":1,
                                    "reasonCode":"DAMAGED_IN_TRANSIT"
                                  }]
                                }
                                """.formatted(so.getId(), sol.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String returnId = created.get("id").asString();
        String lineId = created.get("lines").get(0).get("id").asString();

        mockMvc.perform(put("/api/v1/returns/" + returnId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"RESTOCK\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/returns/" + returnId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("RESTOCK_BIN_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Choose a restock target bin for every RESTOCK line"));

        mockMvc.perform(post("/api/v1/returns/" + returnId + "/escalate-rtv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("NO_DEFECT_LINES"))
                .andExpect(jsonPath("$.detail").value(
                        "Escalate to RTV requires a DEFECTIVE_PRODUCT reason or SCRAP disposition"));
    }
}
