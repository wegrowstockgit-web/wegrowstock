package com.invsys;

import com.invsys.core.security.AuthCookieService;
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
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApWorkspaceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;
    @Autowired LocationRepository locationRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void ingestExtractsHeaderLinksPoAndFlagsPriceVariance() throws Exception {
        String slug = "apws-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        TokenResponse tokens = authService.signup(new SignupRequest(
                "AP Workspace Co", slug, email, "password123", "Owner"));
        UUID tenantId = tokens.tenantId();

        TenantContext.setTenantId(tenantId);
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Harbor Freight Co");
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
        po.setNumber("PO-APWS-1001");
        po.setStatus("PARTIALLY_RECEIVED");
        po = purchaseOrderRepository.save(po);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyReceived(new BigDecimal("10"));
        line.setUnitCost(new BigDecimal("5.00"));
        purchaseOrderLineRepository.save(line);
        TenantContext.clear();

        String body = """
                Invoice Number: INV-4411
                Invoice Date: 2026-08-22
                Supplier: Harbor Freight Co
                PO: PO-APWS-1001
                Subtotal: 80.00
                Tax: 6.40
                SKU WIDGET-S 10 @ $8.00
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

        MvcResult ingest = mockMvc.perform(multipart("/api/v1/ap/ingest")
                        .file(file)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.invoiceNumber").value("INV-4411"))
                .andExpect(jsonPath("$.header.detectedPoNumber").value("PO-APWS-1001"))
                .andExpect(jsonPath("$.purchaseOrderNumber").value("PO-APWS-1001"))
                .andExpect(jsonPath("$.lines[0].sku").value("WIDGET-S"))
                .andExpect(jsonPath("$.lines[0].matchStatus").value("PRICE_VARIANCE"))
                .andExpect(jsonPath("$.hasPriceVariance").value(true))
                .andExpect(jsonPath("$.allMatched").value(false))
                .andReturn();

        JsonNode json = objectMapper.readTree(ingest.getResponse().getContentAsString());
        String ingestionId = json.path("ingestionId").asText();
        assertThat(ingestionId).isNotBlank();

        mockMvc.perform(post("/api/v1/ap/ingestions/" + ingestionId + "/approve")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"sku\":\"WIDGET-S\",\"qty\":10,\"unitCost\":8.00}]}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/ap/ingestions/" + ingestionId + "/approve")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"sku\":\"WIDGET-S\",\"qty\":10,\"unitCost\":5.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.allMatched").value(true));

        JsonNode disputed = objectMapper.readTree(
                mockMvc.perform(multipart("/api/v1/ap/ingest").file(file).cookie(cookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        mockMvc.perform(post("/api/v1/ap/ingestions/" + disputed.path("ingestionId").asText() + "/dispute")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andExpect(jsonPath("$.rtvPath").value("/purchasing/rtv"))
                .andExpect(jsonPath("$.disputeLetter").value(org.hamcrest.Matchers.containsString("weGrowStock")));
    }

    @Test
    void ingestWithoutPoThenBindPreviewAndRecount() throws Exception {
        String slug = "apwsb-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        TokenResponse tokens = authService.signup(new SignupRequest(
                "AP Bind Co", slug, email, "password123", "Owner"));
        UUID tenantId = tokens.tenantId();

        TenantContext.setTenantId(tenantId);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("BOLT");
        product.setName("Bolt");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("BOLT-M8");
        variant = productVariantRepository.save(variant);

        Location bin = new Location();
        bin.setTenantId(tenantId);
        bin.setType("BIN");
        bin.setCode("AP-BIN");
        bin.setName("AP Bin");
        bin.setPath("WH/AP-BIN");
        bin = locationRepository.save(bin);
        TenantContext.clear();

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = login.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);

        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.txt", "text/plain",
                "Invoice Number: INV-BIND\nSKU BOLT-M8 4 @ $2.00\n".getBytes());
        MvcResult unbound = mockMvc.perform(multipart("/api/v1/ap/ingest").file(file).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.invoiceNumber").value("INV-BIND"))
                .andExpect(jsonPath("$.purchaseOrderId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        JsonNode extracted = objectMapper.readTree(unbound.getResponse().getContentAsString());

        TenantContext.setTenantId(tenantId);
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Bind Supplier");
        supplier = supplierRepository.save(supplier);
        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-BIND-22");
        po.setStatus("RECEIVED");
        po.setDestinationLocationId(bin.getId());
        po = purchaseOrderRepository.save(po);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("4"));
        line.setQtyReceived(new BigDecimal("2"));
        line.setUnitCost(new BigDecimal("2.00"));
        purchaseOrderLineRepository.save(line);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/ap/workspace/preview")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purchaseOrderId":"%s","extractedData":{"invoiceNumber":"INV-BIND","lines":[{"sku":"BOLT-M8","qty":4,"unitCost":2.00}]}}
                                """.formatted(po.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].sku").value("BOLT-M8"))
                .andExpect(jsonPath("$.receivedLessThanInvoiced").value(true));

        MvcResult bound = mockMvc.perform(post("/api/v1/ap/workspace/bind")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purchaseOrderId":"%s","extractedData":{"invoiceNumber":"INV-BIND","lines":[{"sku":"BOLT-M8","qty":4,"unitCost":2.00}]}}
                                """.formatted(po.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseOrderNumber").value("PO-BIND-22"))
                .andExpect(jsonPath("$.ingestionId").isNotEmpty())
                .andReturn();
        String ingestionId = objectMapper.readTree(bound.getResponse().getContentAsString()).path("ingestionId").asText();

        mockMvc.perform(post("/api/v1/ap/ingestions/" + ingestionId + "/request-recount")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleCountId").isNotEmpty())
                .andExpect(jsonPath("$.variancePath").value("/inventory/variances"));
        assertThat(extracted.path("header").path("invoiceNumber").asText()).isEqualTo("INV-BIND");
    }
}
