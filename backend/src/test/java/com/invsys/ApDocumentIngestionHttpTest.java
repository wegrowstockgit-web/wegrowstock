package com.invsys;

import com.invsys.auth.AuthCookieService;
import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.ApInvoiceIngestion;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.repository.ApInvoiceIngestionRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.ApDocumentParseService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApDocumentIngestionHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApInvoiceIngestionRepository ingestionRepository;
    @Autowired ApDocumentParseService parseService;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void uploadParsesAndStagesAgainstOpenPo() throws Exception {
        String slug = "apdoc-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        TokenResponse tokens = authService.signup(new SignupRequest(
                "AP Doc Co", slug, email, "password123", "Owner"));
        UUID tenantId = tokens.tenantId();

        TenantContext.setTenantId(tenantId);
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Global Parts Inc");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("WIDGET");
        product.setName("Widget");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("WIDGET-S");
        variant = productVariantRepository.save(variant);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-AP-" + UUID.randomUUID().toString().substring(0, 6));
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("100"));
        line.setUnitCost(new BigDecimal("5.00"));
        purchaseOrderLineRepository.save(line);
        TenantContext.clear();

        String body = """
                Supplier: Global Parts Inc
                SKU WIDGET-S 100 @ $5.00
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.txt", "text/plain", body.getBytes());

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = login.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/ap-ingestions/upload")
                        .file(file)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingestionStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.fileStorageKey").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(upload.getResponse().getContentAsString());
        UUID ingestionId = UUID.fromString(json.path("id").asString());

        // Deterministic parse (same worker logic) for assertion without racing the VT.
        parseService.processIngestion(tenantId, ingestionId);

        TenantContext.setTenantId(tenantId);
        ApInvoiceIngestion staged = ingestionRepository.findById(ingestionId).orElseThrow();
        assertThat(staged.getIngestionStatus()).isEqualTo("STAGED");
        assertThat(staged.getMatchedPurchaseOrderId()).isEqualTo(po.getId());
        assertThat(staged.getParsedMetadata().get("supplierName")).isEqualTo("Global Parts Inc");
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/ap-ingestions/" + ingestionId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingestionStatus").value("STAGED"))
                .andExpect(jsonPath("$.matchedPurchaseOrderId").value(po.getId().toString()));
    }
}
