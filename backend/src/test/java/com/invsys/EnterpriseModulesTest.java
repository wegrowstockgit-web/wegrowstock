package com.invsys;

import com.invsys.domain.Allocation;
import com.invsys.domain.Customer;
import com.invsys.domain.DemandForecast;
import com.invsys.domain.EdiTradingPartner;
import com.invsys.domain.Invoice;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.repository.EdiDocumentLogRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.SupplierInvoiceIngestionRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.ApOcrIngestionService;
import com.invsys.service.EdiTranslationEngine;
import com.invsys.service.FintechUnderwritingService;
import com.invsys.service.ForecastingInferenceService;
import com.invsys.service.ForecastingWorker;
import com.invsys.service.PickingService;
import com.invsys.service.PickingWaveService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseModulesTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired PickingService pickingService;
    @Autowired PickingWaveService pickingWaveService;
    @Autowired AllocationRepository allocationRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired ForecastingInferenceService inferenceService;
    @Autowired ForecastingWorker forecastingWorker;
    @Autowired DemandForecastRepository forecastRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired ApOcrIngestionService apOcrIngestionService;
    @Autowired SupplierInvoiceIngestionRepository ingestionRepository;
    @Autowired EdiTranslationEngine ediTranslationEngine;
    @Autowired EdiTradingPartnerRepository partnerRepository;
    @Autowired EdiDocumentLogRepository ediDocumentLogRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired FintechUnderwritingService fintechUnderwritingService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void pickingServiceOptimizesRouteNearestNeighbor() {
        UUID tenantId = testDataHelper.createTenant("Pick Ent", "pickent-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Location locA = saveLocation(tenantId, "A-01", "WH/Z-A/01");
        Location locC = saveLocation(tenantId, "C-99", "WH/Z-C/99");
        Location locB = saveLocation(tenantId, "B-50", "WH/Z-B/50");

        Product product = saveProduct(tenantId);
        ProductVariant variant = saveVariant(tenantId, product.getId(), "PICK-1");

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Pick Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-PICK-ENT");
        order.setStatus("ALLOCATED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(BigDecimal.ONE);
        line = salesOrderLineRepository.save(line);

        Allocation aC = saveAllocation(tenantId, line.getId(), variant.getId(), locC.getId());
        Allocation aA = saveAllocation(tenantId, line.getId(), variant.getId(), locA.getId());
        Allocation aB = saveAllocation(tenantId, line.getId(), variant.getId(), locB.getId());

        Map<UUID, String> locationPaths = Map.of(
                locA.getId(), locA.getPath(),
                locB.getId(), locB.getPath(),
                locC.getId(), locC.getPath()
        );

        List<Allocation> route = pickingService.optimizePickSequence(
                List.of(aC, aA, aB), locationPaths);

        assertThat(route).hasSize(3);
        assertThat(route.getFirst().getLocationId()).isEqualTo(locA.getId());
        assertThat(route.get(1).getLocationId()).isEqualTo(locB.getId());
        assertThat(route.get(2).getLocationId()).isEqualTo(locC.getId());
    }

    @Test
    void forecastingInferencePopulatesSeasonalityAndConfidence() {
        UUID tenantId = testDataHelper.createTenant("Fc Ent", "fcent-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = saveProduct(tenantId);
        ProductVariant variant = saveVariant(tenantId, product.getId(), "FC-1");

        forecastingWorker.calculateForTenant(tenantId);
        TenantContext.setTenantId(tenantId);

        DemandForecast forecast = forecastRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                .orElseThrow();
        assertThat(forecast.getSeasonalityIndex()).isGreaterThan(BigDecimal.ZERO);
        assertThat(forecast.getConfidenceScore()).isGreaterThan(BigDecimal.ZERO);
        assertThat(forecast.getExternalSignals()).isNotEmpty();
    }

    @Test
    void apOcrReconcilesMatchingPoLines() {
        UUID tenantId = testDataHelper.createTenant("AP Ent", "apent-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("AP Supplier");
        supplier = supplierRepository.save(supplier);

        Product product = saveProduct(tenantId);
        ProductVariant variant = saveVariant(tenantId, product.getId(), "AP-SKU");

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-AP-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("10"));
        line.setUnitCost(new BigDecimal("5.00"));
        purchaseOrderLineRepository.save(line);

        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("lines", List.of(Map.of("sku", "AP-SKU", "qty", 10, "unitCost", 5.00)));

        SupplierInvoiceIngestion ingestion = apOcrIngestionService.submitDocument(po.getId(), extracted);
        assertThat(ingestion.getStatus()).isEqualTo("PENDING");

        SupplierInvoiceIngestion reconciled = apOcrIngestionService.reconcile(ingestion.getId());
        assertThat(reconciled.getStatus()).isEqualTo("RECONCILED");
        assertThat(reconciled.getMatchConfidence()).isGreaterThanOrEqualTo(new BigDecimal("100"));
    }

    @Test
    void ediTranslationEngineParsesInbound850() {
        UUID tenantId = testDataHelper.createTenant("EDI Ent", "edient-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("EDI Customer");
        customer = customerRepository.save(customer);

        EdiTradingPartner partner = new EdiTradingPartner();
        partner.setTenantId(tenantId);
        partner.setCustomerId(customer.getId());
        partner.setAs2Id("PARTNER-EDI-1");
        partner = partnerRepository.save(partner);

        String payload = "ISA*00*          *00*          *ZZ*PARTNER        *ZZ*INVSYS         *"
                + "260713*1200*U*00401*000000001*0*P*>~"
                + "ST*850*0001~"
                + "BEG*00*NE*PO-EDI-123**260713~"
                + "PO1*5*EA*10.00**VP*WIDGET-S~"
                + "SE*4*0001~"
                + "GE*1*0001~"
                + "IEA*1*000000001~";

        EdiTranslationEngine.InboundOrder order = ediTranslationEngine.parseInbound850(partner.getId(), payload);
        assertThat(order.poNumber()).isEqualTo("PO-EDI-123");
        assertThat(order.lines()).isNotEmpty();
        assertThat(ediDocumentLogRepository.findAll()).hasSize(1);
    }

    @Test
    void fintechUnderwritingProvisionsCreditAndFactoring() {
        UUID tenantId = testDataHelper.createTenant("Fin Ent", "finent-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Fin Customer");
        customer = customerRepository.save(customer);

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(customer.getId());
        invoice.setNumber("INV-FIN-1");
        invoice.setStatus("OPEN");
        invoice.setSubtotal(new BigDecimal("1000"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("1000"));
        invoice.setDueAt(Instant.now().plusSeconds(86400 * 30));
        invoice = invoiceRepository.save(invoice);

        FintechUnderwritingService.FintechDashboard dash = fintechUnderwritingService.dashboard();
        assertThat(dash.creditLine().getCreditLimit()).isGreaterThan(BigDecimal.ZERO);
        assertThat(dash.eligibleInvoices()).isNotEmpty();

        var factored = fintechUnderwritingService.requestFactoring(invoice.getId());
        assertThat(factored.getFundingStatus()).isEqualTo("FUNDED");
        assertThat(factored.getEscrowPayoutRef()).isNotBlank();

        var line = fintechUnderwritingService.drawCapital(new BigDecimal("1000"));
        assertThat(line.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    private Product saveProduct(UUID tenantId) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("ENT");
        product.setName("Enterprise Product");
        return productRepository.save(product);
    }

    private ProductVariant saveVariant(UUID tenantId, UUID productId, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        return variantRepository.save(variant);
    }

    private Location saveLocation(UUID tenantId, String code, String path) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("BIN");
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        return locationRepository.save(location);
    }

    private Allocation saveAllocation(UUID tenantId, UUID lineId, UUID variantId, UUID locationId) {
        Allocation allocation = new Allocation();
        allocation.setTenantId(tenantId);
        allocation.setSalesOrderLineId(lineId);
        allocation.setVariantId(variantId);
        allocation.setLocationId(locationId);
        allocation.setQuantity(BigDecimal.ONE);
        allocation.setStatus("ACTIVE");
        return allocationRepository.save(allocation);
    }
}
