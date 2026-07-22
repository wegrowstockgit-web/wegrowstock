package com.invsys.support;

import com.invsys.domain.PickingBatch;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fast read-only pre-flight checks for proactive warehouse bottleneck insights.
 */
@Service
public class SupportBottleneckService {

    private final SalesOrderRepository salesOrderRepository;
    private final PickingBatchRepository pickingBatchRepository;

    public SupportBottleneckService(
            SalesOrderRepository salesOrderRepository,
            PickingBatchRepository pickingBatchRepository
    ) {
        this.salesOrderRepository = salesOrderRepository;
        this.pickingBatchRepository = pickingBatchRepository;
    }

    @Transactional(readOnly = true)
    public String detectProactiveInsight(String route) {
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId == null) {
            return null;
        }
        String path = route == null ? "" : route.toLowerCase(Locale.ROOT);
        if (path.startsWith("/sales-orders") || path.contains("/sales-orders")) {
            long backordered = salesOrderRepository.countByTenantIdAndStatusIn(
                    tenantId, List.of("BACKORDERED"));
            long hold = salesOrderRepository.countByTenantIdAndStatusIn(
                    tenantId, List.of("HOLD", "CREDIT_HOLD"));
            if (hold > 0) {
                return "💡 " + hold + " order" + (hold == 1 ? " is" : "s are")
                        + " currently stuck on Credit Hold. Tap to review.";
            }
            if (backordered > 0) {
                return "💡 " + backordered + " sales order" + (backordered == 1 ? " is" : "s are")
                        + " BACKORDERED waiting for stock. Tap to review.";
            }
        }
        if (path.startsWith("/fulfillment") || path.contains("/fulfillment") || path.contains("/picking")) {
            long unassigned = countUnassignedReadyBatches(tenantId);
            if (unassigned > 0) {
                return "💡 " + unassigned + " high-priority picking wave"
                        + (unassigned == 1 ? " is" : "s are")
                        + " released but unassigned. Tap to claim or assign.";
            }
        }
        if (path.startsWith("/dashboard") || path.isBlank() || path.equals("/")) {
            long hold = salesOrderRepository.countByTenantIdAndStatusIn(
                    tenantId, List.of("HOLD", "CREDIT_HOLD", "BACKORDERED"));
            if (hold > 0) {
                return "💡 " + hold + " outbound order" + (hold == 1 ? " needs" : "s need")
                        + " attention (hold or backorder). Tap to open Sales Orders.";
            }
        }
        return null;
    }

    private long countUnassignedReadyBatches(UUID tenantId) {
        long total = 0;
        for (String status : List.of("RELEASED", "READY", "OPEN", "AVAILABLE")) {
            List<PickingBatch> batches = pickingBatchRepository.findByTenantIdAndStatus(tenantId, status);
            for (PickingBatch batch : batches) {
                if (batch.getAssignedUserId() == null) {
                    total++;
                }
            }
        }
        return total;
    }
}
