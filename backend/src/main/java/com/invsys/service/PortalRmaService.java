package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Customer;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.ReturnLine;
import com.invsys.domain.ReturnOrder;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.integration.easypost.EasyPostGateway;
import com.invsys.integration.easypost.EasyPostProperties;
import com.invsys.media.MediaAttachmentService;
import com.invsys.media.MediaUploadService;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.ReturnLineRepository;
import com.invsys.repository.ReturnOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PortalRmaService {

    private static final Set<String> REASONS = Set.of(
            "DAMAGED", "WRONG_ITEM", "NOT_AS_DESCRIBED", "CHANGED_MIND", "OTHER");
    private static final Set<String> RETURNABLE_ORDER_STATUSES = Set.of(
            "SHIPPED", "PARTIALLY_SHIPPED", "CLOSED");

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final ReturnLineRepository returnLineRepository;
    private final ReturnService returnService;
    private final RmaApprovalEngine approvalEngine;
    private final EasyPostGateway easyPostGateway;
    private final EasyPostProperties easyPostProperties;
    private final CustomerRepository customerRepository;
    private final MediaAttachmentService mediaAttachmentService;
    private final MediaUploadService mediaUploadService;
    private final DocumentSequenceService sequenceService;

    public PortalRmaService(SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            ProductVariantRepository variantRepository,
                            ProductRepository productRepository,
                            ReturnOrderRepository returnOrderRepository,
                            ReturnLineRepository returnLineRepository,
                            ReturnService returnService,
                            RmaApprovalEngine approvalEngine,
                            EasyPostGateway easyPostGateway,
                            EasyPostProperties easyPostProperties,
                            CustomerRepository customerRepository,
                            MediaAttachmentService mediaAttachmentService,
                            MediaUploadService mediaUploadService,
                            DocumentSequenceService sequenceService) {
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.returnOrderRepository = returnOrderRepository;
        this.returnLineRepository = returnLineRepository;
        this.returnService = returnService;
        this.approvalEngine = approvalEngine;
        this.easyPostGateway = easyPostGateway;
        this.easyPostProperties = easyPostProperties;
        this.customerRepository = customerRepository;
        this.mediaAttachmentService = mediaAttachmentService;
        this.mediaUploadService = mediaUploadService;
        this.sequenceService = sequenceService;
    }

    @Transactional(readOnly = true)
    public List<EligibleLine> eligibleLines(UUID salesOrderId) {
        SalesOrder order = requireCustomerOrder(salesOrderId);
        if (!RETURNABLE_ORDER_STATUSES.contains(order.getStatus())) {
            return List.of();
        }
        List<EligibleLine> out = new ArrayList<>();
        for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(order.getId())) {
            BigDecimal already = returnLineRepository.sumExpectedForLine(line.getId());
            BigDecimal max = line.getQtyShipped().subtract(already);
            if (max.signum() <= 0) {
                continue;
            }
            ProductVariant variant = variantRepository.findById(line.getVariantId()).orElse(null);
            String sku = variant != null ? variant.getSku() : "—";
            String name = variant != null
                    ? productRepository.findById(variant.getProductId()).map(Product::getName).orElse(sku)
                    : sku;
            out.add(new EligibleLine(
                    line.getId(),
                    line.getVariantId(),
                    sku,
                    name,
                    max,
                    line.getUnitPrice(),
                    variant != null && variant.isRmaRequiresReview()));
        }
        return out;
    }

    @Transactional
    public PortalRmaResult createPortalReturn(UUID salesOrderId,
                                              String reasonCode,
                                              List<PortalRmaLineInput> lines) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        SalesOrder order = requireCustomerOrder(salesOrderId);
        if (!RETURNABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER",
                    "Only shipped orders can be returned");
        }
        String reason = normalizeReason(reasonCode);
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "At least one line is required");
        }
        if (RmaApprovalEngine.REASON_DAMAGED.equals(reason)) {
            boolean missingPhoto = lines.stream().anyMatch(l -> l.mediaObjectId() == null);
            if (missingPhoto) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EVIDENCE_REQUIRED",
                        "DAMAGED returns require a photo upload for each line");
            }
        }

        BigDecimal merchandiseValue = BigDecimal.ZERO;
        List<ProductVariant> variants = new ArrayList<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        BigDecimal totalWeightLb = BigDecimal.ZERO;

        for (PortalRmaLineInput input : lines) {
            SalesOrderLine sol = salesOrderLineRepository.findById(input.salesOrderLineId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Line not found"));
            if (!order.getId().equals(sol.getSalesOrderId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Line does not belong to order");
            }
            returnService.validateReturnQuantityPublic(sol, input.quantity());
            ProductVariant variant = variantRepository.findById(sol.getVariantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
            variants.add(variant);
            merchandiseValue = merchandiseValue.add(sol.getUnitPrice().multiply(input.quantity()));
            totalWeightLb = totalWeightLb.add(weightLb(variant).multiply(input.quantity()));
            if (input.mediaObjectId() != null) {
                mediaUploadService.requireUploadedByCurrentUser(input.mediaObjectId());
            }
            resolved.add(new ResolvedLine(sol, variant, input.quantity(), input.mediaObjectId()));
        }

        RmaApprovalEngine.Decision decision = approvalEngine.evaluate(merchandiseValue, reason, variants);

        EasyPostGateway.ParcelSpec parcel = buildParcel(order, totalWeightLb.max(new BigDecimal("0.5")));
        // Rate-only — never purchases (LiveEasyPostGateway.shopRates; Mock overrides estimate)
        BigDecimal estimatedCost = easyPostGateway.estimateCheapestRate(parcel, "RMA-" + order.getNumber());

        ReturnOrder returnOrder = new ReturnOrder();
        returnOrder.setTenantId(tenantId);
        returnOrder.setSalesOrderId(order.getId());
        returnOrder.setNumber(sequenceService.nextNumber("RMA", "RMA-{YYYY}-{seq:5}"));
        returnOrder.setReasonCode(reason);
        returnOrder.setEstimatedLabelCost(estimatedCost);
        returnOrder.setStatus(decision.status());
        returnOrder = returnOrderRepository.save(returnOrder);

        for (ResolvedLine rl : resolved) {
            ReturnLine line = new ReturnLine();
            line.setTenantId(tenantId);
            line.setReturnId(returnOrder.getId());
            line.setSalesOrderLineId(rl.sol().getId());
            line.setQuantityExpected(rl.qty());
            line.setDisposition("QUARANTINE");
            line.setReasonCode(reason);
            line.setMediaObjectId(rl.mediaObjectId());
            line = returnLineRepository.save(line);
            if (rl.mediaObjectId() != null) {
                mediaAttachmentService.attach(
                        rl.mediaObjectId(), "RETURN_LINE", line.getId(), "RETURN_CONDITION", 0);
            }
        }

        String labelUrl = null;
        if (RmaApprovalEngine.STATUS_APPROVED.equals(decision.status())) {
            EasyPostGateway.LabelResult label = easyPostGateway.purchaseReturnLabel(
                    parcel, returnOrder.getNumber());
            labelUrl = label.labelRef();
            returnOrder.setReturnLabelUrl(labelUrl);
            returnOrder.setEstimatedLabelCost(label.postageAmount());
            returnOrder.setLabelPurchaseMode("SYSTEM");
            returnOrder.setStatus("APPROVED");
            returnOrder = returnOrderRepository.save(returnOrder);
        }

        return toResult(returnOrder, decision.reviewReason(), merchandiseValue, customerId);
    }

    @Transactional(readOnly = true)
    public PortalRmaResult getPortalReturn(UUID returnId) {
        UUID customerId = TenantContext.requireCustomerId();
        ReturnOrder returnOrder = returnOrderRepository.findById(returnId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return not found"));
        SalesOrder order = salesOrderRepository.findById(returnOrder.getSalesOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        if (!customerId.equals(order.getCustomerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Return not accessible");
        }
        return toResult(returnOrder, null, null, customerId);
    }

    private static PortalRmaResult toResult(ReturnOrder returnOrder,
                                            String reviewReason,
                                            BigDecimal merchandiseValue,
                                            UUID customerId) {
        String mode = returnOrder.getLabelPurchaseMode();
        String instruction = null;
        if ("APPROVED".equals(returnOrder.getStatus()) && "CUSTOMER".equalsIgnoreCase(mode)) {
            instruction = "Please ship this return at your own expense. A prepaid label was not purchased.";
        } else if ("APPROVED".equals(returnOrder.getStatus()) && returnOrder.getReturnLabelUrl() != null) {
            instruction = "Download your prepaid return label and ship the items back.";
        } else if ("PENDING_REVIEW".equals(returnOrder.getStatus())) {
            instruction = "Your return is pending office approval.";
        }
        return new PortalRmaResult(
                returnOrder.getId(),
                returnOrder.getNumber(),
                returnOrder.getStatus(),
                reviewReason,
                returnOrder.getReturnLabelUrl(),
                returnOrder.getEstimatedLabelCost(),
                merchandiseValue,
                customerId,
                mode,
                instruction);
    }

    private SalesOrder requireCustomerOrder(UUID salesOrderId) {
        UUID customerId = TenantContext.requireCustomerId();
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        if (!customerId.equals(order.getCustomerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Order not accessible");
        }
        return order;
    }

    private EasyPostGateway.ParcelSpec buildParcel(SalesOrder order, BigDecimal weightLb) {
        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        EasyPostGateway.AddressSpec to = customer != null
                ? EasyPostGateway.AddressSpec.fromMap(customer.getShippingAddress(), customer.getName())
                : null;
        EasyPostGateway.AddressSpec from = easyPostProperties.defaultFromAddress();
        return new EasyPostGateway.ParcelSpec(
                new BigDecimal("12"), new BigDecimal("10"), new BigDecimal("8"),
                weightLb, to, from, false);
    }

    private static String normalizeReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "reasonCode is required");
        }
        String reason = reasonCode.trim().toUpperCase(Locale.ROOT);
        if (!REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "reasonCode must be one of " + REASONS);
        }
        return reason;
    }

    private static BigDecimal weightLb(ProductVariant variant) {
        BigDecimal w = variant.getWeight() != null ? variant.getWeight() : new BigDecimal("0.5");
        if ("kg".equalsIgnoreCase(variant.getWeightUnit())) {
            return w.multiply(new BigDecimal("2.20462")).setScale(3, RoundingMode.HALF_UP);
        }
        return w;
    }

    public record PortalRmaLineInput(UUID salesOrderLineId, BigDecimal quantity, UUID mediaObjectId) {
    }

    public record EligibleLine(
            UUID salesOrderLineId,
            UUID variantId,
            String sku,
            String name,
            BigDecimal qtyReturnable,
            BigDecimal unitPrice,
            boolean requiresReview
    ) {
    }

    public record PortalRmaResult(
            UUID id,
            String number,
            String status,
            String reviewReason,
            String returnLabelUrl,
            BigDecimal estimatedLabelCost,
            BigDecimal merchandiseValue,
            UUID customerId,
            String labelPurchaseMode,
            String shippingInstruction
    ) {
    }

    private record ResolvedLine(
            SalesOrderLine sol,
            ProductVariant variant,
            BigDecimal qty,
            UUID mediaObjectId
    ) {
    }
}
