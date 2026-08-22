package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PurchaseOrderWorkspaceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void draftLinesLockAfterSubmitAndCancelRequiresZeroReceipts() throws Exception {
        String slug = "pows-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "PO Workspace Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("POWS");
        product.setName("Workspace Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"POWS-1",
                                  "price":4.50,
                                  "currency":"USD",
                                  "weight":1,
                                  "weightUnit":"lb",
                                  "length":2,
                                  "width":2,
                                  "height":2,
                                  "dimUnit":"in"
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String supplierId = objectMapper.readTree(mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Workspace Vendor\",\"contact\":{}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","number":"PO-WS-1","lines":[{"variantId":"%s","qtyOrdered":10,"unitCost":4.5}]}
                                """.formatted(supplierId, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String poId = created.get("id").asString();

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String lineId = detail.get("lines").get(0).get("id").asString();

        mockMvc.perform(patch("/api/v1/purchase-orders/" + poId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyOrdered\":12,\"unitCost\":5.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtyOrdered").value(12))
                .andExpect(jsonPath("$.unitCost").value(5.25));

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(patch("/api/v1/purchase-orders/" + poId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyOrdered\":99}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void listPurchaseOrdersReturnsGridEnrichmentFields() throws Exception {
        String slug = "pogrid-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "PO Grid Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("POGRID");
        product.setName("Grid Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"POGRID-1",
                                  "price":4.50,
                                  "currency":"USD",
                                  "weight":1,
                                  "weightUnit":"lb",
                                  "length":2,
                                  "width":2,
                                  "height":2,
                                  "dimUnit":"in"
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String supplierId = objectMapper.readTree(mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Grid Vendor\",\"contact\":{}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":"%s",
                                  "number":"PO-GRID-1",
                                  "freightAmount":10.00,
                                  "expectedAt":"2026-09-01T12:00:00Z",
                                  "vendorReference":"VN-REF-99",
                                  "lines":[{"variantId":"%s","qtyOrdered":10,"unitCost":4.50}]
                                }
                                """.formatted(supplierId, variantId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("search", "PO-GRID-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].number").value("PO-GRID-1"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].expectedDeliveryDate").isNotEmpty())
                .andExpect(jsonPath("$.items[0].totalAmount").value(55.00))
                .andExpect(jsonPath("$.items[0].totalQtyOrdered").value(10))
                .andExpect(jsonPath("$.items[0].totalQtyReceived").value(0))
                .andExpect(jsonPath("$.items[0].vendorReference").value("VN-REF-99"));
    }

    @Test
    void markInTransitStoresTrackingAndBlocksCancel() throws Exception {
        String slug = "potrn-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "PO Transit Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("POTRN");
        product.setName("Transit Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"POTRN-1",
                                  "price":4.50,
                                  "currency":"USD",
                                  "weight":1,
                                  "weightUnit":"lb",
                                  "length":2,
                                  "width":2,
                                  "height":2,
                                  "dimUnit":"in"
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String supplierId = objectMapper.readTree(mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transit Vendor\",\"contact\":{}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String poId = objectMapper.readTree(mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","number":"PO-TRN-1","lines":[{"variantId":"%s","qtyOrdered":4,"unitCost":2}]}
                                """.formatted(supplierId, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMeshPartner").value(false));

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/mark-in-transit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vendorReference":"ASN-77",
                                  "trackingNumber":"1Z999AA10123456784",
                                  "carrier":"UPS",
                                  "expectedDeliveryDate":"2026-09-15T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        mockMvc.perform(get("/api/v1/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.vendorReference").value("ASN-77"))
                .andExpect(jsonPath("$.trackingNumber").value("1Z999AA10123456784"))
                .andExpect(jsonPath("$.carrier").value("UPS"));

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/revert-to-submitted")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}
