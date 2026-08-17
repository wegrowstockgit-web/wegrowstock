package com.invsys.pos;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.api.InventoryOperations;
import com.invsys.pos.dto.OfflineReceiptDto;
import com.invsys.pos.dto.OfflineReceiptDto.OfflineReceiptLineDto;
import com.invsys.pos.dto.PosSyncResponse;
import com.invsys.pos.dto.PosSyncResponse.RejectedReceipt;
import com.invsys.pos.event.InventoryLevelDelta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts offline POS receipts into WMS ledger {@code POS_SALE} rows.
 * The ledger insert trigger queues negative {@code inventory_level_deltas}
 * for {@link com.invsys.service.InventoryLevelFlushWorker}.
 */
@Service
public class PosReceiptProcessor {

    private final LocationRepository locationRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryOperations inventoryOperations;
    private final JdbcTemplate tenantJdbc;

    @Autowired
    public PosReceiptProcessor(
            LocationRepository locationRepository,
            ProductVariantRepository variantRepository,
            InventoryOperations inventoryOperations,
            DataSource dataSource) {
        this.locationRepository = locationRepository;
        this.variantRepository = variantRepository;
        this.inventoryOperations = inventoryOperations;
        this.tenantJdbc = new JdbcTemplate(dataSource);
    }

    PosReceiptProcessor(
            LocationRepository locationRepository,
            ProductVariantRepository variantRepository,
            InventoryOperations inventoryOperations,
            JdbcTemplate tenantJdbc) {
        this.locationRepository = locationRepository;
        this.variantRepository = variantRepository;
        this.inventoryOperations = inventoryOperations;
        this.tenantJdbc = tenantJdbc;
    }

    @Transactional
    public PosSyncResponse sync(List<OfflineReceiptDto> receipts) {
        return processReceipts(receipts);
    }

    @Transactional
    public PosSyncResponse processReceipts(List<OfflineReceiptDto> receipts) {
        UUID tenantId = TenantContext.requireTenantId();
        int accepted = 0;
        int duplicates = 0;
        List<RejectedReceipt> rejected = new ArrayList<>();
        if (receipts == null || receipts.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_BATCH", "Receipt batch is required.");
        }
        for (OfflineReceiptDto receipt : receipts) {
            try {
                if (processOne(tenantId, receipt)) {
                    accepted++;
                } else {
                    duplicates++;
                }
            } catch (ApiException ex) {
                UUID id = receipt == null ? null : receipt.id();
                rejected.add(new RejectedReceipt(id, ex.getMessage()));
            }
        }
        return new PosSyncResponse(accepted, duplicates, List.copyOf(rejected));
    }

    boolean processOne(UUID tenantId, OfflineReceiptDto receipt) {
        if (receipt == null || receipt.id() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIPT", "Receipt id is required.");
        }
        if (receipt.storeLocationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STORE_REQUIRED", "Store location is required.");
        }
        Location store = locationRepository.findById(receipt.storeLocationId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "STORE_NOT_FOUND", "Store location was not found for this tenant."));
        if (store.getTenantId() != null && !tenantId.equals(store.getTenantId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STORE_NOT_FOUND", "Store location was not found for this tenant.");
        }
        if (receipt.lines() == null || receipt.lines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_LINES", "Receipt has no line items.");
        }

        List<InventoryLevelDelta> deltas = new ArrayList<>();
        for (OfflineReceiptLineDto line : receipt.lines()) {
            deltas.add(toSaleDelta(tenantId, store.getId(), line));
        }

        if (!tryClaim(tenantId, receipt)) {
            return false;
        }
        for (int i = 0; i < receipt.lines().size(); i++) {
            OfflineReceiptLineDto line = receipt.lines().get(i);
            InventoryLevelDelta delta = deltas.get(i);
            inventoryOperations.posSale(delta.variantId(), store.getId(), line.quantity(), receipt.id());
        }
        return true;
    }

    InventoryLevelDelta toSaleDelta(UUID tenantId, UUID storeLocationId, OfflineReceiptLineDto line) {
        if (line == null || line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QTY", "Line quantity must be greater than zero.");
        }
        ProductVariant variant = resolveVariant(tenantId, line);
        return new InventoryLevelDelta(
                tenantId,
                variant.getId(),
                storeLocationId,
                null,
                null,
                line.quantity().negate(),
                null);
    }

    private ProductVariant resolveVariant(UUID tenantId, OfflineReceiptLineDto line) {
        if (line.variantId() != null) {
            return variantRepository.findById(line.variantId())
                    .filter(v -> v.getTenantId() == null || tenantId.equals(v.getTenantId()))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST, "VARIANT_NOT_FOUND", "Catalog variant was not found."));
        }
        String upc = line.upc() == null ? "" : line.upc().trim();
        if (upc.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VARIANT_REQUIRED", "Each line needs a variantId or UPC.");
        }
        return variantRepository.findByTenantIdAndBarcode(tenantId, upc)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "VARIANT_NOT_FOUND", "No catalog item matches UPC " + upc + "."));
    }

    boolean tryClaim(UUID tenantId, OfflineReceiptDto receipt) {
        int inserted = tenantJdbc.update(
                """
                INSERT INTO pos_synced_receipts (receipt_id, tenant_id, store_location_id, tender_type)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, receipt_id) DO NOTHING
                """,
                receipt.id(),
                tenantId,
                receipt.storeLocationId(),
                receipt.tenderType());
        return inserted > 0;
    }
}
