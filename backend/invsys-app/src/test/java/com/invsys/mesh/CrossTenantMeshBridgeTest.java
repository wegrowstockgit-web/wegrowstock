package com.invsys.mesh;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.domain.ExternalReference;
import com.invsys.domain.PlatformAlert;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.core.integration.OutboxDispatcher;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.repository.ExternalReferenceRepository;
import com.invsys.repository.PlatformAlertRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.purchasing.service.PurchaseOrderService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "invsys.integration.outbox.dispatcher-enabled=true",
        "spring.task.scheduling.enabled=false"
})
class CrossTenantMeshBridgeTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired BootstrapJdbc bootstrapJdbc;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository productVariantRepository;
    @Autowired ExternalReferenceRepository externalReferenceRepository;
    @Autowired PlatformAlertRepository platformAlertRepository;
    @Autowired CrossTenantMeshBridgeService meshBridgeService;
    @Autowired OutboxDispatcher outboxDispatcher;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void submittedPoCreatesConfirmedSalesOrderUsingMeshNetworkMapping() {
        UUID buyer = testDataHelper.createTenant("Mesh Buyer", "mbuy-" + UUID.randomUUID().toString().substring(0, 8));
        UUID seller = testDataHelper.createTenant("Mesh Seller", "msell-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(buyer);
        Supplier supplier = saveSupplier(buyer, "Partner Supplier Mirror");
        Product buyerProduct = product(buyer, "MB", "Buyer Widget");
        ProductVariant buyerVariant = variant(buyer, buyerProduct.getId(), "BUYER-SKU-1");

        TenantContext.setTenantId(seller);
        Customer sellerCustomer = saveCustomer(seller, "Buyer as Customer");
        Product sellerProduct = product(seller, "MS", "Seller Widget");
        ProductVariant sellerVariant = variant(seller, sellerProduct.getId(), "SELLER-SKU-1");

        bootstrapJdbc.upsertMeshPartner(buyer, seller, supplier.getId(), sellerCustomer.getId(), "CONNECTED");

        TenantContext.setTenantId(buyer);
        persistMeshNetworkMapping(buyer, buyerVariant.getId(), seller, sellerVariant.getId());

        PurchaseOrder po = saveDraftPo(buyer, supplier.getId(), "PO-MESH-1");
        savePoLine(buyer, po.getId(), buyerVariant.getId(), "5", "12.50");
        PurchaseOrder submitted = purchaseOrderService.submit(po.getId());
        UUID poId = submitted.getId();
        TenantContext.clear();

        drainOutbox("PURCHASE_ORDER_SUBMITTED");

        TenantContext.setTenantId(seller);
        assertThat(salesOrderRepository.findAll()).anySatisfy(so -> {
            assertThat(so.getStatus()).isEqualTo("CONFIRMED");
            assertThat(so.getChannel()).isEqualTo("MESH");
            assertThat(so.getCustomerPoNumber()).isEqualTo("PO-MESH-1");
            assertThat(so.getCustomerId()).isEqualTo(sellerCustomer.getId());
        });

        SalesOrder meshSo = salesOrderRepository.findAll().stream()
                .filter(so -> "MESH".equals(so.getChannel()))
                .findFirst()
                .orElseThrow();

        meshBridgeService.onSalesOrderShipped(seller, meshSo.getId(), Map.of(
                "salesOrderId", meshSo.getId(),
                "shipmentId", UUID.randomUUID(),
                "carrier", "FEDEX",
                "trackingNumber", "1ZMESH999"));

        TenantContext.setTenantId(buyer);
        PurchaseOrder updated = purchaseOrderRepository.findById(poId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("IN_TRANSIT");
        assertThat(updated.getTrackingMetadata().getFirst().get("trackingNumber")).isEqualTo("1ZMESH999");
    }

    @Test
    void confirmOrderCreatesUnallocatedSalesOrderAndPoNote() {
        UUID buyer = testDataHelper.createTenant("Mesh Buyer C", "mbc-" + UUID.randomUUID().toString().substring(0, 8));
        UUID seller = testDataHelper.createTenant("Mesh Seller C", "msc-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(buyer);
        Supplier supplier = saveSupplier(buyer, "Partner Supplier Mirror");
        Product buyerProduct = product(buyer, "MBC", "Buyer Widget");
        ProductVariant buyerVariant = variant(buyer, buyerProduct.getId(), "BUYER-SKU-C");

        TenantContext.setTenantId(seller);
        Customer sellerCustomer = saveCustomer(seller, "Buyer as Customer");
        Product sellerProduct = product(seller, "MSC", "Seller Widget");
        ProductVariant sellerVariant = variant(seller, sellerProduct.getId(), "SELLER-SKU-C");

        bootstrapJdbc.upsertMeshPartner(buyer, seller, supplier.getId(), sellerCustomer.getId(), "CONNECTED");

        TenantContext.setTenantId(buyer);
        persistMeshNetworkMapping(buyer, buyerVariant.getId(), seller, sellerVariant.getId());

        PurchaseOrder po = saveDraftPo(buyer, supplier.getId(), "PO-MESH-CONFIRM");
        savePoLine(buyer, po.getId(), buyerVariant.getId(), "4", "9.00");
        PurchaseOrder confirmed = purchaseOrderService.confirmOrder(po.getId());
        assertThat(confirmed.getNotes()).contains("Linked to Mesh Partner Sales Order #");

        TenantContext.setTenantId(seller);
        assertThat(salesOrderRepository.findAll()).anySatisfy(so -> {
            assertThat(so.getStatus()).isEqualTo("UNALLOCATED");
            assertThat(so.getChannel()).isEqualTo("MESH");
            assertThat(so.getCustomerPoNumber()).isEqualTo("PO-MESH-CONFIRM");
            assertThat(so.getCustomerId()).isEqualTo(sellerCustomer.getId());
            assertThat(confirmed.getNotes()).contains(so.getNumber());
        });
    }

    @Test
    void unmappedLinesCreateDraftExceptionOrderAndSellerAlert() {
        UUID buyer = testDataHelper.createTenant("Mesh Buyer 2", "mb2-" + UUID.randomUUID().toString().substring(0, 8));
        UUID seller = testDataHelper.createTenant("Mesh Seller 2", "ms2-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(buyer);
        Supplier supplier = saveSupplier(buyer, "Supplier");
        Product buyerProduct = product(buyer, "MB2", "Buyer Mix");
        ProductVariant mappedBuyer = variant(buyer, buyerProduct.getId(), "MAPPED-BUY");
        ProductVariant unmappedBuyer = variant(buyer, buyerProduct.getId(), "UNMAPPED-BUY");

        TenantContext.setTenantId(seller);
        Customer sellerCustomer = saveCustomer(seller, "Buyer Cust");
        Product sellerProduct = product(seller, "MS2", "Seller Mix");
        ProductVariant mappedSeller = variant(seller, sellerProduct.getId(), "MAPPED-SELL");

        bootstrapJdbc.upsertMeshPartner(buyer, seller, supplier.getId(), sellerCustomer.getId(), "CONNECTED");

        TenantContext.setTenantId(buyer);
        persistMeshNetworkMapping(buyer, mappedBuyer.getId(), seller, mappedSeller.getId());

        PurchaseOrder po = saveDraftPo(buyer, supplier.getId(), "PO-MESH-MIX");
        savePoLine(buyer, po.getId(), mappedBuyer.getId(), "2", "10");
        savePoLine(buyer, po.getId(), unmappedBuyer.getId(), "3", "11");
        purchaseOrderService.submit(po.getId());
        TenantContext.clear();

        drainOutbox("PURCHASE_ORDER_SUBMITTED");

        TenantContext.setTenantId(seller);
        List<SalesOrder> orders = salesOrderRepository.findAll();
        assertThat(orders).anyMatch(so -> "MESH".equals(so.getChannel()) && "CONFIRMED".equals(so.getStatus()));
        assertThat(orders).anyMatch(so ->
                CrossTenantMeshBridgeService.CHANNEL_MESH_EXCEPTION.equals(so.getChannel())
                        && "DRAFT".equals(so.getStatus()));

        List<PlatformAlert> alerts = platformAlertRepository
                .findByTenantIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(seller);
        assertThat(alerts).anySatisfy(alert -> {
            assertThat(alert.getAlertType()).isEqualTo(CrossTenantMeshBridgeService.ALERT_MESH_CATALOG);
            assertThat(alert.getTitle()).isEqualTo("New mesh items require catalog mapping.");
            assertThat(alert.getDetails().get("unmappedSkus")).asList().contains("UNMAPPED-BUY");
        });
    }

    private void persistMeshNetworkMapping(UUID buyerTenantId, UUID buyerVariantId,
                                           UUID partnerTenantId, UUID sellerVariantId) {
        ExternalReference ref = new ExternalReference();
        ref.setTenantId(buyerTenantId);
        ref.setEntityType(MeshCatalogTranslationService.ENTITY_VARIANT);
        ref.setEntityId(buyerVariantId);
        ref.setSystem(MeshCatalogTranslationService.MESH_NETWORK);
        ref.setExternalId(MeshCatalogTranslationService.encodePartnerVariant(partnerTenantId, sellerVariantId));
        externalReferenceRepository.save(ref);
    }

    private void drainOutbox(String eventType) {
        JdbcTemplate jdbc = new JdbcTemplate(bootstrapDataSource);
        for (int i = 0; i < 200; i++) {
            Integer pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING' AND event_type = ?",
                    Integer.class,
                    eventType);
            if (pending != null && pending == 0) {
                return;
            }
            transactionTemplate.executeWithoutResult(status -> outboxDispatcher.dispatchNext());
        }
    }

    private Supplier saveSupplier(UUID tenantId, String name) {
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName(name);
        return supplierRepository.save(supplier);
    }

    private Customer saveCustomer(UUID tenantId, String name) {
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName(name);
        return customerRepository.save(customer);
    }

    private PurchaseOrder saveDraftPo(UUID tenantId, UUID supplierId, String number) {
        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplierId);
        po.setNumber(number);
        po.setStatus("DRAFT");
        return purchaseOrderRepository.save(po);
    }

    private void savePoLine(UUID tenantId, UUID poId, UUID variantId, String qty, String cost) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(poId);
        line.setVariantId(variantId);
        line.setQtyOrdered(new BigDecimal(qty));
        line.setUnitCost(new BigDecimal(cost));
        purchaseOrderLineRepository.save(line);
    }

    private Product product(UUID tenantId, String root, String name) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(root);
        product.setName(name);
        return productRepository.save(product);
    }

    private ProductVariant variant(UUID tenantId, UUID productId, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        return productVariantRepository.save(variant);
    }
}
