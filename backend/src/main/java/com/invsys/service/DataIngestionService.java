package com.invsys.service;

import com.invsys.api.dto.ImportRowStatus;
import com.invsys.api.dto.PreflightResponse;
import com.invsys.api.dto.PreflightRowDto;
import com.invsys.common.ApiException;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Streaming CSV ingestion with cold-start pre-flight resolution.
 * Missing SKUs / locations are never written to the ledger unless explicitly resolved.
 */
@Service
public class DataIngestionService {

    private static final Set<String> KNOWN_UOMS = Set.of(
            "EA", "EACH", "CASE", "CS", "PALLET", "PLT", "KG", "LB", "BOX");

    private static final Map<String, String> DEFAULT_MAPPING = Map.ofEntries(
            Map.entry("sku", "sku"),
            Map.entry("name", "name"),
            Map.entry("barcode", "barcode"),
            Map.entry("qty", "qty"),
            Map.entry("unitCost", "unitCost"),
            Map.entry("hsCode", "hsCode"),
            Map.entry("lotNumber", "lotNumber"),
            Map.entry("expiry", "expiry"),
            Map.entry("palletTie", "palletTie"),
            Map.entry("palletHigh", "palletHigh"),
            Map.entry("tempZone", "tempZone"),
            Map.entry("locationPath", "location_path"),
            Map.entry("weight", "weight"),
            Map.entry("length", "length"),
            Map.entry("width", "width"),
            Map.entry("height", "height"),
            Map.entry("weightUnit", "weightUnit"),
            Map.entry("dimUnit", "dimUnit"),
            Map.entry("countryOfOrigin", "countryOfOrigin"),
            Map.entry("hazmat", "hazmat"),
            Map.entry("fragile", "fragile"),
            Map.entry("abcClassification", "abcClassification"),
            Map.entry("lifecycleStatus", "lifecycleStatus"),
            Map.entry("uom", "uom"));

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final InventoryService inventoryService;
    private final SkuMaskService skuMaskService;
    private final AuditService auditService;
    private final Executor virtualThreadExecutor;

    public DataIngestionService(ObjectMapper objectMapper,
                                ProductRepository productRepository,
                                ProductVariantRepository variantRepository,
                                LocationRepository locationRepository,
                                InventoryService inventoryService,
                                SkuMaskService skuMaskService,
                                AuditService auditService,
                                @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.inventoryService = inventoryService;
        this.skuMaskService = skuMaskService;
        this.auditService = auditService;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    public PreflightResponse preflight(MultipartFile file, String columnsMappingJson, UUID locationId) {
        return withTenantContext(file, () -> {
            try {
                byte[] bytes = file.getBytes();
                String checksum = sha256Hex(bytes);
                Map<String, String> mapping = parseMapping(columnsMappingJson);
                List<ParsedRow> parsed = parseRows(bytes, mapping);
                List<ClassifiedRow> classified = classifyRows(parsed, locationId, false, false);
                return toPreflightResponse(classified, checksum);
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PREFLIGHT_FAILED",
                        ex.getMessage() != null ? ex.getMessage() : "Pre-flight failed");
            }
        });
    }

    /**
     * Creates catalog products/variants for MISSING_PRODUCT rows that pass dimension validation.
     * Does not write inventory_ledger rows.
     */
    public CreateMissingResult createMissingProducts(MultipartFile file, String columnsMappingJson) {
        return withTenantContext(file, () -> {
            try {
                byte[] bytes = file.getBytes();
                Map<String, String> mapping = parseMapping(columnsMappingJson);
                List<ParsedRow> parsed = parseRows(bytes, mapping);
                int created = 0;
                int skipped = 0;
                List<String> errors = new ArrayList<>();
                for (ParsedRow row : parsed) {
                    if (row.blank()) {
                        continue;
                    }
                    Optional<ProductVariant> existing = findVariant(row.sku);
                    if (existing.isPresent()) {
                        skipped++;
                        continue;
                    }
                    String dimError = validateRequiredDimensions(row);
                    if (dimError != null) {
                        skipped++;
                        errors.add("Row " + row.rowNumber() + ": " + dimError);
                        continue;
                    }
                    if (row.uom() != null && !KNOWN_UOMS.contains(row.uom().toUpperCase(Locale.ROOT))) {
                        skipped++;
                        errors.add("Row " + row.rowNumber() + ": unknown UOM '" + row.uom() + "'");
                        continue;
                    }
                    try {
                        createVariant(row);
                        created++;
                    } catch (Exception ex) {
                        skipped++;
                        errors.add("Row " + row.rowNumber() + ": " + ex.getMessage());
                    }
                }
                return new CreateMissingResult(created, skipped, errors);
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CREATE_MISSING_FAILED",
                        ex.getMessage() != null ? ex.getMessage() : "Create missing products failed");
            }
        });
    }

    public ImportResult importFile(MultipartFile file, String columnsMappingJson, UUID locationId) {
        return importFile(file, columnsMappingJson, locationId, ImportOptions.defaults());
    }

    public ImportResult importFile(MultipartFile file, String columnsMappingJson, UUID locationId,
                                   ImportOptions options) {
        ImportOptions opts = options != null ? options : ImportOptions.defaults();
        return withTenantContext(file, () -> {
            try {
                return streamImport(file.getBytes(), parseMapping(columnsMappingJson), locationId, opts,
                        file.getOriginalFilename());
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INGESTION_FAILED",
                        ex.getMessage() != null ? ex.getMessage() : "Import failed");
            }
        });
    }

    private <T> T withTenantContext(MultipartFile file, ThrowingSupplier<T> work) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Upload file is required");
        }
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId().orElse(null);
        UUID warehouseId = TenantContext.getWarehouseId().orElse(null);

        try {
            return CompletableFuture.supplyAsync(() -> {
                TenantContext.setTenantId(tenantId);
                if (userId != null) {
                    TenantContext.setUserId(userId);
                }
                if (warehouseId != null) {
                    TenantContext.setWarehouseId(warehouseId);
                }
                try {
                    return work.get();
                } catch (ApiException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INGESTION_FAILED",
                            ex.getMessage() != null ? ex.getMessage() : "Ingestion failed");
                } finally {
                    TenantContext.clear();
                }
            }, virtualThreadExecutor).join();
        } catch (java.util.concurrent.CompletionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof ApiException api) {
                throw api;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INGESTION_FAILED",
                    cause.getMessage() != null ? cause.getMessage() : "Ingestion failed");
        }
    }

    private ImportResult streamImport(byte[] bytes, Map<String, String> mapping, UUID locationId,
                                      ImportOptions options, String fileName) throws Exception {
        String checksum = sha256Hex(bytes);
        List<ParsedRow> parsed = parseRows(bytes, mapping);
        List<ClassifiedRow> classified = classifyRows(
                parsed, locationId, options.createMissingProducts(), options.createMissingLocations());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (ClassifiedRow row : classified) {
            if (row.parsed().blank()) {
                continue;
            }
            if (row.status() != ImportRowStatus.READY_TO_IMPORT) {
                skipped++;
                errors.add("Row " + row.parsed().rowNumber() + ": " + row.status()
                        + (row.detail() != null ? " — " + row.detail() : ""));
                if (errors.size() >= 50) {
                    break;
                }
                continue;
            }
            try {
                ProductVariant variant = row.matchedVariantId() != null
                        ? variantRepository.findById(row.matchedVariantId()).orElseThrow()
                        : createVariant(row.parsed());
                variant = applyEnterpriseFields(variant, row.parsed());

                UUID receiveLocationId = row.matchedLocationId();
                if (receiveLocationId == null && options.createMissingLocations()
                        && row.parsed().locationPath() != null) {
                    receiveLocationId = resolveOrCreateLocationPath(row.parsed().locationPath(), true)
                            .map(Location::getId)
                            .orElse(null);
                }
                if (receiveLocationId == null) {
                    skipped++;
                    errors.add("Row " + row.parsed().rowNumber() + ": MISSING_LOCATION");
                    continue;
                }

                BigDecimal qty = row.parsed().qty();
                if (qty.signum() > 0) {
                    Map<String, Object> meta = new HashMap<>();
                    if (row.parsed().expiry() != null && !row.parsed().expiry().isBlank()) {
                        meta.put("lot_expires_at", row.parsed().expiry().trim());
                    }
                    inventoryService.receive(variant.getId(), receiveLocationId, null,
                            row.parsed().lotNumber(), qty, "DATA_IMPORT", "DATA_IMPORT", null,
                            row.parsed().unitCost(), null, meta.isEmpty() ? null : meta);
                }
                imported++;
            } catch (Exception rowEx) {
                skipped++;
                errors.add("Row " + row.parsed().rowNumber() + ": " + rowEx.getMessage());
                if (errors.size() >= 50) {
                    break;
                }
            }
        }

        if (imported > 0) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("imported", imported);
            diff.put("skipped", skipped);
            diff.put("fileName", fileName != null ? fileName : "upload.csv");
            diff.put("checksumSha256", checksum);
            diff.put("actorUserId", TenantContext.getUserId().map(UUID::toString).orElse(null));
            // entity_id is NOT NULL — use a deterministic batch id derived from the file checksum.
            UUID batchId = UUID.nameUUIDFromBytes(checksum.getBytes(StandardCharsets.UTF_8));
            auditService.record("DATA_IMPORT", "INGESTION", batchId, diff);
        }

        return new ImportResult(imported, skipped, errors, checksum);
    }

    private List<ClassifiedRow> classifyRows(List<ParsedRow> parsed, UUID fallbackLocationId,
                                             boolean treatCreateableProductsAsReady,
                                             boolean treatCreateableLocationsAsReady) {
        UUID defaultWarehouse = resolveDefaultWarehouseId(fallbackLocationId);
        List<ClassifiedRow> out = new ArrayList<>();
        for (ParsedRow row : parsed) {
            if (row.blank()) {
                continue;
            }
            if (row.parseError() != null) {
                out.add(new ClassifiedRow(row, ImportRowStatus.VALIDATION_ERROR, row.parseError(),
                        null, null));
                continue;
            }
            if (row.uom() != null && !KNOWN_UOMS.contains(row.uom().toUpperCase(Locale.ROOT))) {
                out.add(new ClassifiedRow(row, ImportRowStatus.MISSING_UOM,
                        "Unknown UOM '" + row.uom() + "'", null, null));
                continue;
            }

            Optional<ProductVariant> variant = findVariant(row.sku);
            UUID variantId = variant.map(ProductVariant::getId).orElse(null);
            boolean productMissing = variant.isEmpty();

            String dimError = null;
            if (productMissing || treatCreateableProductsAsReady) {
                // New products always require dimensions; existing may omit (keep catalog values).
                if (productMissing) {
                    dimError = validateRequiredDimensions(row);
                }
            }
            if (dimError != null) {
                out.add(new ClassifiedRow(row, ImportRowStatus.VALIDATION_ERROR, dimError, null, null));
                continue;
            }

            UUID locationId = null;
            boolean locationPendingCreate = false;
            String locationDetail = null;
            if (row.locationPath() != null && !row.locationPath().isBlank()) {
                Optional<Location> byPath = findLocationByPath(row.locationPath());
                if (byPath.isPresent()) {
                    locationId = byPath.get().getId();
                } else if (treatCreateableLocationsAsReady) {
                    locationPendingCreate = true;
                } else {
                    locationDetail = "location_path not found: " + normalizePath(row.locationPath());
                }
            } else if (defaultWarehouse != null) {
                locationId = defaultWarehouse;
            } else {
                locationDetail = "No location_path and no warehouse available";
            }
            boolean locationMissing = locationId == null && !locationPendingCreate;

            // Prefer MISSING_PRODUCT for cold-start bulk create; location issues surface next.
            if (productMissing && !treatCreateableProductsAsReady) {
                String detail = "SKU not found in catalog";
                if (locationMissing && locationDetail != null) {
                    detail = detail + "; " + locationDetail;
                }
                out.add(new ClassifiedRow(row, ImportRowStatus.MISSING_PRODUCT, detail, null, locationId));
                continue;
            }

            if (locationMissing) {
                out.add(new ClassifiedRow(row, ImportRowStatus.MISSING_LOCATION, locationDetail,
                        variantId, null));
                continue;
            }

            if (productMissing) {
                out.add(new ClassifiedRow(row, ImportRowStatus.READY_TO_IMPORT,
                        locationPendingCreate
                                ? "Will create product and location path"
                                : "Will create product",
                        null, locationId));
                continue;
            }

            out.add(new ClassifiedRow(row, ImportRowStatus.READY_TO_IMPORT,
                    locationPendingCreate ? "Will create location path" : "Matched existing SKU",
                    variantId, locationId));
        }
        return out;
    }

    private PreflightResponse toPreflightResponse(List<ClassifiedRow> classified, String checksum) {
        List<PreflightRowDto> rows = new ArrayList<>();
        Map<ImportRowStatus, Long> counts = new EnumMap<>(ImportRowStatus.class);
        for (ImportRowStatus s : ImportRowStatus.values()) {
            counts.put(s, 0L);
        }
        Set<String> missingSkus = new LinkedHashSet<>();
        Set<String> missingPaths = new LinkedHashSet<>();
        for (ClassifiedRow row : classified) {
            counts.merge(row.status(), 1L, Long::sum);
            rows.add(new PreflightRowDto(
                    row.parsed().rowNumber(),
                    row.parsed().sku(),
                    row.parsed().name(),
                    row.parsed().locationPath(),
                    row.status(),
                    row.detail(),
                    row.matchedVariantId(),
                    row.matchedLocationId()));
            if (row.status() == ImportRowStatus.MISSING_PRODUCT && row.parsed().sku() != null) {
                missingSkus.add(row.parsed().sku());
            }
            if (row.status() == ImportRowStatus.MISSING_LOCATION && row.parsed().locationPath() != null) {
                missingPaths.add(normalizePath(row.parsed().locationPath()));
            }
        }
        return new PreflightResponse(rows, counts, List.copyOf(missingSkus), List.copyOf(missingPaths),
                checksum);
    }

    private List<ParsedRow> parseRows(byte[] bytes, Map<String, String> mapping) throws Exception {
        List<ParsedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "File is empty");
            }
            String[] headers = splitCsv(headerLine);
            Map<String, Integer> headerIndex = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerIndex.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = splitCsv(line);
                String sku = cell(cols, headerIndex, mapping.get("sku"));
                String name = cell(cols, headerIndex, mapping.get("name"));
                String barcode = cell(cols, headerIndex, mapping.get("barcode"));
                String qtyRaw = cell(cols, headerIndex, mapping.get("qty"));
                String costRaw = cell(cols, headerIndex, mapping.get("unitCost"));
                String hsCode = cell(cols, headerIndex, mapping.get("hsCode"));
                String lotNumber = cell(cols, headerIndex, mapping.get("lotNumber"));
                String expiry = cell(cols, headerIndex, mapping.get("expiry"));
                String palletTieRaw = cell(cols, headerIndex, mapping.get("palletTie"));
                String palletHighRaw = cell(cols, headerIndex, mapping.get("palletHigh"));
                String tempZone = cell(cols, headerIndex, mapping.get("tempZone"));
                String locationPath = cell(cols, headerIndex, mapping.get("locationPath"));
                if (locationPath == null) {
                    locationPath = cell(cols, headerIndex, "location_path");
                }
                String weightRaw = cell(cols, headerIndex, mapping.get("weight"));
                String lengthRaw = cell(cols, headerIndex, mapping.get("length"));
                String widthRaw = cell(cols, headerIndex, mapping.get("width"));
                String heightRaw = cell(cols, headerIndex, mapping.get("height"));
                String weightUnit = cell(cols, headerIndex, mapping.get("weightUnit"));
                String dimUnit = cell(cols, headerIndex, mapping.get("dimUnit"));
                String countryOfOrigin = cell(cols, headerIndex, mapping.get("countryOfOrigin"));
                String hazmatRaw = cell(cols, headerIndex, mapping.get("hazmat"));
                String fragileRaw = cell(cols, headerIndex, mapping.get("fragile"));
                String abc = cell(cols, headerIndex, mapping.get("abcClassification"));
                String lifecycle = cell(cols, headerIndex, mapping.get("lifecycleStatus"));
                String uom = cell(cols, headerIndex, mapping.get("uom"));

                if ((sku == null || sku.isBlank()) && (name == null || name.isBlank())) {
                    rows.add(ParsedRow.blank(rowNum));
                    continue;
                }

                String parseError = null;
                BigDecimal qty = BigDecimal.ONE;
                BigDecimal unitCost = BigDecimal.ZERO;
                BigDecimal weight = null;
                BigDecimal length = null;
                BigDecimal width = null;
                BigDecimal height = null;
                Boolean hazmat = null;
                Boolean fragile = null;
                try {
                    qty = parseDecimal(qtyRaw, BigDecimal.ONE);
                    if (qty.signum() < 0) {
                        parseError = "qty must be >= 0";
                    }
                    unitCost = parseDecimal(costRaw, BigDecimal.ZERO);
                    weight = parseDecimalNullable(weightRaw);
                    length = parseDecimalNullable(lengthRaw);
                    width = parseDecimalNullable(widthRaw);
                    height = parseDecimalNullable(heightRaw);
                    hazmat = parseBoolean(hazmatRaw);
                    fragile = parseBoolean(fragileRaw);
                    if (tempZone != null && !tempZone.isBlank()) {
                        normalizeTempZone(tempZone); // validate early
                    }
                } catch (Exception ex) {
                    parseError = ex.getMessage();
                }

                rows.add(new ParsedRow(
                        rowNum, sku, name, barcode, qty, unitCost, hsCode, lotNumber, expiry,
                        palletTieRaw, palletHighRaw, tempZone, locationPath,
                        weight, length, width, height,
                        weightUnit, dimUnit, countryOfOrigin,
                        hazmat, fragile,
                        abc, lifecycle, uom, parseError, false));
            }
        }
        return rows;
    }

    private Optional<ProductVariant> findVariant(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return variantRepository.findByTenantIdAndSku(TenantContext.requireTenantId(), sku.trim());
    }

    private static String validateRequiredDimensions(ParsedRow row) {
        if (row.length() == null || row.length().signum() <= 0
                || row.width() == null || row.width().signum() <= 0
                || row.height() == null || row.height().signum() <= 0) {
            return "length, width, and height are required for new products";
        }
        return null;
    }

    private ProductVariant createVariant(ParsedRow row) {
        UUID tenantId = TenantContext.requireTenantId();
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(row.sku() != null && !row.sku().isBlank() ? row.sku().trim() : "IMP");
        product.setName(row.name() != null && !row.name().isBlank()
                ? row.name().trim()
                : (row.sku() != null ? row.sku().trim() : "Imported"));
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku(skuMaskService.mintSku(row.sku(), null));
        return applyEnterpriseFields(variant, row);
    }

    private ProductVariant applyEnterpriseFields(ProductVariant variant, ParsedRow row) {
        if (row.barcode() != null && !row.barcode().isBlank()) {
            variant.setBarcode(row.barcode().trim());
        }
        if (row.hsCode() != null && !row.hsCode().isBlank()) {
            variant.setHsTariffCode(row.hsCode().trim());
        }
        if (row.lotNumber() != null && !row.lotNumber().isBlank()) {
            variant.setLotTracked(true);
        }
        Integer tie = parsePositiveInt(row.palletTieRaw());
        if (tie != null) {
            variant.setPalletTie(tie);
        }
        Integer high = parsePositiveInt(row.palletHighRaw());
        if (high != null) {
            variant.setPalletHigh(high);
        }
        if (row.tempZone() != null && !row.tempZone().isBlank()) {
            variant.setStorageTempZone(normalizeTempZone(row.tempZone()));
        }
        if (row.weight() != null) {
            variant.setWeight(row.weight());
        }
        if (row.weightUnit() != null && !row.weightUnit().isBlank()) {
            variant.setWeightUnit(row.weightUnit().trim());
        }
        if (row.length() != null) {
            variant.setLength(row.length());
        }
        if (row.width() != null) {
            variant.setWidth(row.width());
        }
        if (row.height() != null) {
            variant.setHeight(row.height());
        }
        if (row.dimUnit() != null && !row.dimUnit().isBlank()) {
            variant.setDimUnit(row.dimUnit().trim());
        }
        if (row.countryOfOrigin() != null && !row.countryOfOrigin().isBlank()) {
            variant.setCountryOfOrigin(row.countryOfOrigin().trim().toUpperCase(Locale.ROOT));
        }
        if (row.hazmat() != null) {
            variant.setHazmat(row.hazmat());
        }
        if (row.fragile() != null) {
            variant.setFragile(row.fragile());
        }
        if (row.abcClassification() != null && !row.abcClassification().isBlank()) {
            variant.setAbcClassification(row.abcClassification().trim().toUpperCase(Locale.ROOT));
        }
        if (row.lifecycleStatus() != null && !row.lifecycleStatus().isBlank()) {
            variant.setLifecycleStatus(row.lifecycleStatus().trim().toUpperCase(Locale.ROOT));
        }
        return variantRepository.save(variant);
    }

    private UUID resolveDefaultWarehouseId(UUID locationId) {
        if (locationId != null) {
            return locationId;
        }
        return locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId()).stream()
                .filter(l -> "WAREHOUSE".equalsIgnoreCase(l.getType()))
                .map(Location::getId)
                .findFirst()
                .orElse(null);
    }

    private Optional<Location> findLocationByPath(String rawPath) {
        String norm = normalizePath(rawPath);
        if (norm.isEmpty()) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.requireTenantId();
        Optional<Location> exact = locationRepository.findByTenantIdAndPath(tenantId, norm);
        if (exact.isPresent()) {
            return exact;
        }
        Optional<Location> withSlash = locationRepository.findByTenantIdAndPath(tenantId, "/" + norm);
        if (withSlash.isPresent()) {
            return withSlash;
        }
        // Fallback: match by normalized equality across tenant locations (handles mixed formats).
        return locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .filter(l -> normalizePath(l.getPath()).equalsIgnoreCase(norm))
                .findFirst();
    }

    /**
     * Recursively resolve or create a location tree from a slash-delimited path
     * (e.g. {@code WH-01/ZoneA/Bin01}).
     */
    Optional<Location> resolveOrCreateLocationPath(String rawPath, boolean createMissing) {
        String norm = normalizePath(rawPath);
        if (norm.isEmpty()) {
            return Optional.empty();
        }
        Optional<Location> existing = findLocationByPath(norm);
        if (existing.isPresent()) {
            return existing;
        }
        if (!createMissing) {
            return Optional.empty();
        }

        String[] segments = norm.split("/");
        UUID parentId = null;
        StringBuilder pathBuilder = new StringBuilder();
        Location leaf = null;
        UUID tenantId = TenantContext.requireTenantId();

        for (int i = 0; i < segments.length; i++) {
            String code = segments[i].trim();
            if (code.isEmpty()) {
                continue;
            }
            if (!pathBuilder.isEmpty()) {
                pathBuilder.append('/');
            }
            pathBuilder.append(code);
            String path = pathBuilder.toString();

            Optional<Location> node = findLocationByPath(path);
            if (node.isEmpty()) {
                // Also try by code under same tenant (unique constraint).
                node = locationRepository.findByTenantIdAndCode(tenantId, code);
                if (node.isPresent() && !normalizePath(node.get().getPath()).equalsIgnoreCase(path)) {
                    // Code exists but under a different path — create with path-qualified code.
                    code = path.replace('/', '-');
                    node = Optional.empty();
                }
            }
            if (node.isPresent()) {
                leaf = node.get();
                parentId = leaf.getId();
                continue;
            }

            Location created = new Location();
            created.setTenantId(tenantId);
            created.setParentLocationId(parentId);
            created.setType(typeForDepth(i, segments.length));
            created.setCode(code);
            created.setName(code);
            created.setPath(path);
            leaf = locationRepository.save(created);
            parentId = leaf.getId();
        }
        return Optional.ofNullable(leaf);
    }

    private static String typeForDepth(int index, int total) {
        if (index == 0) {
            return "WAREHOUSE";
        }
        if (index == total - 1 && total > 1) {
            return "BIN";
        }
        if (index == 1) {
            return "ZONE";
        }
        return "AISLE";
    }

    private static String normalizePath(String raw) {
        if (raw == null) {
            return "";
        }
        String p = raw.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String normalizeTempZone(String raw) {
        String zone = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (zone) {
            case "AMBIENT", "REFRIGERATED", "FROZEN", "COLD", "CHILLED" -> {
                if ("COLD".equals(zone) || "CHILLED".equals(zone)) {
                    yield "REFRIGERATED";
                }
                yield zone;
            }
            default -> throw new IllegalArgumentException(
                    "tempZone must be AMBIENT, REFRIGERATED, or FROZEN");
        };
    }

    private static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int value = Integer.parseInt(raw.trim().replace(",", ""));
        if (value <= 0) {
            throw new IllegalArgumentException("pallet Ti/Hi must be positive");
        }
        return value;
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "1", "true", "yes", "y" -> true;
            case "0", "false", "no", "n" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean: " + raw);
        };
    }

    private static BigDecimal parseDecimalNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim().replace(",", ""));
    }

    private Map<String, String> parseMapping(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>(DEFAULT_MAPPING);
            }
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "columnsMapping must be a JSON object of field→column header");
        }
    }

    private static String cell(String[] cols, Map<String, Integer> headerIndex, String mappedHeader) {
        if (mappedHeader == null || mappedHeader.isBlank()) {
            return null;
        }
        Integer idx = headerIndex.get(mappedHeader.trim().toLowerCase(Locale.ROOT));
        if (idx == null || idx < 0 || idx >= cols.length) {
            return null;
        }
        String value = cols[idx].trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal parseDecimal(String raw, BigDecimal fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return new BigDecimal(raw.trim().replace(",", ""));
    }

    private static String[] splitCsv(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(String[]::new);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record ParsedRow(
            int rowNumber,
            String sku,
            String name,
            String barcode,
            BigDecimal qty,
            BigDecimal unitCost,
            String hsCode,
            String lotNumber,
            String expiry,
            String palletTieRaw,
            String palletHighRaw,
            String tempZone,
            String locationPath,
            BigDecimal weight,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String weightUnit,
            String dimUnit,
            String countryOfOrigin,
            Boolean hazmat,
            Boolean fragile,
            String abcClassification,
            String lifecycleStatus,
            String uom,
            String parseError,
            boolean blank
    ) {
        static ParsedRow blank(int rowNumber) {
            return new ParsedRow(rowNumber, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, true);
        }
    }

    private record ClassifiedRow(
            ParsedRow parsed,
            ImportRowStatus status,
            String detail,
            UUID matchedVariantId,
            UUID matchedLocationId
    ) {
    }

    public record ImportOptions(boolean createMissingProducts, boolean createMissingLocations) {
        public static ImportOptions defaults() {
            return new ImportOptions(false, false);
        }
    }

    public record ImportResult(int imported, int skipped, List<String> errors, String fileChecksumSha256) {
        public ImportResult(int imported, int skipped, List<String> errors) {
            this(imported, skipped, errors, null);
        }
    }

    public record CreateMissingResult(int created, int skipped, List<String> errors) {
    }
}
