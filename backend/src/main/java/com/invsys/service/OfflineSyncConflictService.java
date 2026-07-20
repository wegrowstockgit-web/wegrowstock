package com.invsys.service;

import com.invsys.api.dto.OfflineSyncConflictView;
import com.invsys.common.ApiException;
import com.invsys.domain.ConflictActionType;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.OfflineSyncConflict;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.User;
import com.invsys.repository.OfflineSyncConflictRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.conflict.ConflictFieldDescriptor;
import com.invsys.service.conflict.ConflictSchemaMetadataGenerator;
import com.invsys.tenancy.TenantContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OfflineSyncConflictService {

    private final OfflineSyncConflictRepository repository;
    private final ConflictSchemaMetadataGenerator schemaGenerator;
    private final InventoryService inventoryService;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    public OfflineSyncConflictService(
            OfflineSyncConflictRepository repository,
            ConflictSchemaMetadataGenerator schemaGenerator,
            @Lazy InventoryService inventoryService,
            ProductVariantRepository variantRepository,
            UserRepository userRepository
    ) {
        this.repository = repository;
        this.schemaGenerator = schemaGenerator;
        this.inventoryService = inventoryService;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
    }

    /** Backward-compatible sink used by unit tests / simple callers. */
    @Transactional
    public OfflineSyncConflict sink(Map<String, Object> payload, String errorMessage) {
        String url = payload != null ? stringOrNull(payload.get("url")) : null;
        Object body = payload != null ? payload.get("body") : null;
        ConflictActionType action = inferAction(body, url);
        return sink(payload, errorMessage, action, TenantContext.getUserId().orElse(null), url);
    }

    @Transactional
    public OfflineSyncConflict sink(
            Map<String, Object> payload,
            String errorMessage,
            ConflictActionType actionType,
            UUID pickerUserId,
            String requestUrl
    ) {
        Object body = payload != null ? payload.get("body") : null;
        ConflictActionType action = actionType != null ? actionType : inferAction(body, requestUrl);
        List<Map<String, Object>> schema = schemaGenerator.generateAsMaps(requestUrl, action, body);

        OfflineSyncConflict row = new OfflineSyncConflict();
        row.setTenantId(TenantContext.requireTenantId());
        row.setPayload(payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>());
        row.setErrorMessage(errorMessage);
        row.setStatus(OfflineSyncConflict.STATUS_PENDING);
        row.setActionType(action);
        row.setPickerUserId(pickerUserId);
        row.setRequestUrl(requestUrl != null ? requestUrl : stringOrNull(row.getPayload().get("url")));
        row.setSchemaMetadata(schema);
        return repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<OfflineSyncConflictView> listViews(String status) {
        return list(status).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<OfflineSyncConflict> list(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        if (status != null && !status.isBlank()) {
            return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status.trim().toUpperCase());
        }
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public OfflineSyncConflictView dismiss(UUID id) {
        OfflineSyncConflict row = require(id);
        row.setStatus(OfflineSyncConflict.STATUS_DISCARDED);
        row.setResolvedByUserId(TenantContext.getUserId().orElse(null));
        row.setResolvedAt(Instant.now());
        return toView(repository.save(row));
    }

    /**
     * Marks the conflict for client-side re-enqueue (new Idempotency-Key).
     */
    @Transactional
    public OfflineSyncConflictView forceRetry(UUID id) {
        OfflineSyncConflict row = require(id);
        if (!OfflineSyncConflict.STATUS_PENDING.equals(row.getStatus())
                && !OfflineSyncConflict.STATUS_RETRY_REQUESTED.equals(row.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT_NOT_RETRYABLE",
                    "Only pending conflicts can be force-retried");
        }
        row.setStatus(OfflineSyncConflict.STATUS_RETRY_REQUESTED);
        return toView(repository.save(row));
    }

    @Transactional
    public OfflineSyncConflictView markResolved(UUID id) {
        OfflineSyncConflict row = require(id);
        row.setStatus(OfflineSyncConflict.STATUS_RESOLVED_AND_REPLAYED);
        row.setResolvedByUserId(TenantContext.getUserId().orElse(null));
        row.setResolvedAt(Instant.now());
        return toView(repository.save(row));
    }

    /**
     * Manager applies glove-friendly corrections, patches {@code payload_json}, replays through
     * {@link InventoryService} stamped as the manager with {@code OFFLINE_CONFLICT_OVERRIDE}.
     */
    @Transactional
    public OfflineSyncConflictView resolveConflict(UUID conflictId, Map<String, Object> manualCorrections) {
        OfflineSyncConflict row = require(conflictId);
        if (!OfflineSyncConflict.STATUS_PENDING.equals(row.getStatus())
                && !OfflineSyncConflict.STATUS_RETRY_REQUESTED.equals(row.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT_NOT_RESOLVABLE",
                    "Only pending conflicts can be approved and re-processed");
        }

        UUID managerId = TenantContext.requireUserId();
        Map<String, Object> payload = new LinkedHashMap<>(row.getPayload());
        Map<String, Object> body = extractBody(payload);
        applyMutableCorrections(body, manualCorrections, row.getSchemaMetadata());
        payload.put("body", body);
        row.setPayload(payload);

        InventoryLedger ledger = replayAsManagerOverride(row, body);
        // Compliance: ledger actor must be the resolving manager (JWT context), not the picker.
        if (ledger.getCreatedBy() == null || !managerId.equals(ledger.getCreatedBy())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OVERRIDE_ACTOR_MISMATCH",
                    "Conflict override must be stamped with the resolving manager identity");
        }
        if (!OfflineSyncConflict.REASON_OFFLINE_CONFLICT_OVERRIDE.equals(ledger.getReasonCode())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OVERRIDE_REASON_MISMATCH",
                    "Conflict override must use OFFLINE_CONFLICT_OVERRIDE reason code");
        }

        row.setStatus(OfflineSyncConflict.STATUS_RESOLVED_AND_REPLAYED);
        row.setResolvedByUserId(managerId);
        row.setResolvedAt(Instant.now());
        return toView(repository.save(row));
    }

    private InventoryLedger replayAsManagerOverride(OfflineSyncConflict row, Map<String, Object> body) {
        UUID tenantId = TenantContext.requireTenantId();
        String barcode = stringOrNull(body.get("barcode"));
        if (barcode == null || barcode.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BARCODE_REQUIRED",
                    "Corrected transaction is missing a barcode");
        }
        ProductVariant variant = variantRepository.findByTenantIdAndBarcode(tenantId, barcode.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VARIANT_NOT_FOUND",
                        "No item master matches the scanned barcode"));

        UUID locationId = uuidOrNull(body.get("warehouseId"));
        if (locationId == null) {
            locationId = uuidOrNull(body.get("locationId"));
        }
        if (locationId == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOCATION_REQUIRED",
                    "Corrected transaction is missing a warehouse / bin location");
        }

        BigDecimal quantity = decimalOrNull(body.get("quantity"));
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        if (quantity.signum() == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUANTITY_REQUIRED",
                    "Corrected quantity must not be zero");
        }

        ConflictActionType action = row.getActionType() != null
                ? row.getActionType()
                : ConflictActionType.fromScanMode(stringOrNull(body.get("mode")));
        BigDecimal delta = switch (action != null ? action : ConflictActionType.CYCLE_COUNT) {
            case OUTBOUND_PICK -> quantity.abs().negate();
            case INBOUND_RECEIVE -> quantity.abs();
            case CYCLE_COUNT -> quantity;
        };

        String lotNumber = stringOrNull(body.get("lotNumber"));
        String serial = stringOrNull(body.get("serialNumber"));
        return inventoryService.adjust(
                variant.getId(),
                locationId,
                null,
                lotNumber,
                delta,
                OfflineSyncConflict.REASON_OFFLINE_CONFLICT_OVERRIDE,
                serial,
                Map.of(
                        "offlineConflictId", row.getId().toString(),
                        "pickerUserId", row.getPickerUserId() != null ? row.getPickerUserId().toString() : "",
                        "resolvedByUserId", TenantContext.requireUserId().toString()));
    }

    private void applyMutableCorrections(
            Map<String, Object> body,
            Map<String, Object> corrections,
            List<Map<String, Object>> schema
    ) {
        if (corrections == null || corrections.isEmpty()) {
            return;
        }
        Map<String, ConflictFieldDescriptor> byKey = new LinkedHashMap<>();
        if (schema != null) {
            for (Map<String, Object> raw : schema) {
                ConflictFieldDescriptor d = ConflictFieldDescriptor.fromMap(raw);
                if (d != null && d.key() != null && !d.key().isBlank()) {
                    byKey.put(d.key(), d);
                }
            }
        }
        for (Map.Entry<String, Object> entry : corrections.entrySet()) {
            String key = entry.getKey();
            ConflictFieldDescriptor descriptor = byKey.get(key);
            if (descriptor == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_CORRECTION_KEY",
                        "Cannot correct unknown field: " + key);
            }
            if (!descriptor.mutable()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IMMUTABLE_FIELD",
                        "Field is read-only: " + descriptor.label());
            }
            Object value = entry.getValue();
            if ("number".equalsIgnoreCase(descriptor.type()) && value != null) {
                BigDecimal n = decimalOrNull(value);
                if (n == null) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NUMBER",
                            descriptor.label() + " must be a number");
                }
                Object minObj = descriptor.constraints().get("min");
                if (minObj != null) {
                    BigDecimal min = decimalOrNull(minObj);
                    if (min != null && n.compareTo(min) < 0) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BELOW_MINIMUM",
                                descriptor.label() + " must be at least " + min);
                    }
                }
                body.put(key, n);
            } else {
                body.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractBody(Map<String, Object> payload) {
        Object body = payload.get("body");
        if (body instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private OfflineSyncConflictView toView(OfflineSyncConflict row) {
        String display = "Floor Operator";
        if (row.getPickerUserId() != null) {
            display = userRepository.findByTenantIdAndId(row.getTenantId(), row.getPickerUserId())
                    .map(User::getDisplayName)
                    .filter(n -> n != null && !n.isBlank())
                    .or(() -> userRepository.findByTenantIdAndId(row.getTenantId(), row.getPickerUserId())
                            .map(User::getEmail))
                    .orElse(display);
        }
        return OfflineSyncConflictView.from(row, display);
    }

    private OfflineSyncConflict require(UUID id) {
        return repository.findByTenantIdAndId(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONFLICT_NOT_FOUND",
                        "Offline sync conflict not found"));
    }

    private static ConflictActionType inferAction(Object body, String url) {
        if (body instanceof Map<?, ?> map && map.get("mode") != null) {
            ConflictActionType fromMode = ConflictActionType.fromScanMode(String.valueOf(map.get("mode")));
            if (fromMode != null) {
                return fromMode;
            }
        }
        if (url != null && url.toLowerCase().contains("count")) {
            return ConflictActionType.CYCLE_COUNT;
        }
        return null;
    }

    private static String stringOrNull(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static UUID uuidOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof UUID u) {
            return u;
        }
        try {
            return UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal decimalOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            return BigDecimal.valueOf(((Number) v).longValue());
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
