package com.invsys.documents;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.Tenant;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.fulfillment.domain.ShipmentLine;
import com.invsys.modules.fulfillment.repository.ShipmentLineRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Packing slip PDF — shipment + lines without prices.
 */
@Component
public class PackingSlipDocumentBuilder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final TenantRepository tenantRepository;
    private final DocumentRenderingService renderingService;

    public PackingSlipDocumentBuilder(
            ShipmentRepository shipmentRepository,
            ShipmentLineRepository shipmentLineRepository,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            CustomerRepository customerRepository,
            ProductVariantRepository productVariantRepository,
            TenantRepository tenantRepository,
            DocumentRenderingService renderingService
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.customerRepository = customerRepository;
        this.productVariantRepository = productVariantRepository;
        this.tenantRepository = tenantRepository;
        this.renderingService = renderingService;
    }

    public byte[] buildPdf(UUID shipmentId) {
        return renderingService.generatePdf("documents/packing_slip_template", buildVariables(shipmentId));
    }

    public Map<String, Object> buildVariables(UUID shipmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Shipment not found"));

        SalesOrder order = salesOrderRepository.findById(shipment.getSalesOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found"));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tenant not found"));

        List<ShipmentLine> shipLines = shipmentLineRepository.findByShipmentId(shipmentId);
        Map<UUID, SalesOrderLine> orderLines = salesOrderLineRepository.findBySalesOrderId(order.getId()).stream()
                .collect(Collectors.toMap(SalesOrderLine::getId, Function.identity(), (a, b) -> a));
        Map<UUID, ProductVariant> variants = productVariantRepository.findAllById(
                        orderLines.values().stream().map(SalesOrderLine::getVariantId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity(), (a, b) -> a));

        List<DocumentLineView> lines = shipLines.stream()
                .map(sl -> {
                    SalesOrderLine sol = orderLines.get(sl.getSalesOrderLineId());
                    String desc = "Item";
                    if (sol != null) {
                        ProductVariant variant = variants.get(sol.getVariantId());
                        if (variant != null && variant.getSku() != null) {
                            desc = variant.getSku();
                        } else {
                            desc = "Variant " + sol.getVariantId();
                        }
                    }
                    return DocumentLineView.packing(desc, formatQty(sl.getQuantity()));
                })
                .toList();

        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenant.getName());
        vars.put("tenantAddressHtml", escape(tenant.getName()) + "<br/>" + escape("Tenant " + tenant.getSlug()));
        vars.put("documentTitle", "PACKING SLIP");
        vars.put("documentNumber", "PS-" + shipment.getId().toString().substring(0, 8).toUpperCase());
        vars.put("status", shipment.getStatus());
        vars.put("customerName", customer.getName());
        vars.put("customerAddressHtml", DocumentAddressFormatter.toHtml(customer.getShippingAddress()));
        vars.put("shipDate", shipment.getCreatedAt() == null ? "—" : DAY.format(shipment.getCreatedAt()));
        vars.put("carrier", shipment.getCarrier() == null || shipment.getCarrier().isBlank() ? "—" : shipment.getCarrier());
        vars.put("trackingNumber", shipment.getTrackingNumber());
        vars.put("salesOrderNumber", order.getNumber());
        vars.put("lines", lines);
        vars.put("legalDisclaimer",
                "Packing slip for " + order.getNumber() + ". Prices omitted. © " + tenant.getName());
        return vars;
    }

    private static String formatQty(BigDecimal qty) {
        BigDecimal value = qty == null ? BigDecimal.ZERO : qty;
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
