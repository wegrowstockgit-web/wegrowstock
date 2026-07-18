package com.invsys.service;

import com.invsys.common.ApiException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One-shot legacy ERP items CSV migration. Entire batch runs in a single DB transaction
 * so a mid-file failure rolls back products, variants, and INITIAL_MIGRATION ledger rows.
 * Supports enterprise product attributes (HS code, lot/expiry, pallet Ti-Hi, temp zone).
 */
@Service
public class LegacyErpMigrationService {

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
            Map.entry("tempZone", "tempZone"));

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final InventoryService inventoryService;
    private final SkuMaskService skuMaskService;

    public LegacyErpMigrationService(ObjectMapper objectMapper,
                                     ProductRepository productRepository,
                                     ProductVariantRepository variantRepository,
                                     LocationRepository locationRepository,
                                     InventoryService inventoryService,
                                     SkuMaskService skuMaskService) {
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.inventoryService = inventoryService;
        this.skuMaskService = skuMaskService;
    }

    @Transactional
    public MigrationResult migrate(MultipartFile file, String columnsMappingJson, UUID locationId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Upload file is required");
        }
        Map<String, String> mapping = parseMapping(columnsMappingJson);
        UUID resolvedLocation = resolveLocation(locationId);

        int imported = 0;
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
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
                try {
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

                    if ((sku == null || sku.isBlank()) && (name == null || name.isBlank())) {
                        continue;
                    }
                    BigDecimal qty = parseDecimal(qtyRaw, BigDecimal.ZERO);
                    if (qty.signum() < 0) {
                        throw new IllegalArgumentException("qty must be >= 0");
                    }
                    BigDecimal unitCost = parseDecimal(costRaw, BigDecimal.ZERO);

                    ProductVariant variant = resolveOrCreateVariant(
                            sku, name, barcode, hsCode, lotNumber, palletTieRaw, palletHighRaw, tempZone);
                    if (qty.signum() > 0) {
                        Map<String, Object> meta = new HashMap<>();
                        if (expiry != null && !expiry.isBlank()) {
                            meta.put("lot_expires_at", expiry.trim());
                        }
                        inventoryService.receiveInitialMigration(
                                variant.getId(), resolvedLocation, qty, unitCost, lotNumber,
                                meta.isEmpty() ? null : meta);
                    }
                    imported++;
                } catch (Exception rowEx) {
                    // Fail the whole transaction — no partial ERP cutover.
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MIGRATION_FAILED",
                            "Row " + rowNum + ": " + rowEx.getMessage());
                }
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MIGRATION_FAILED",
                    ex.getMessage() != null ? ex.getMessage() : "Legacy migration failed");
        }
        return new MigrationResult(imported, errors);
    }

    private UUID resolveLocation(UUID locationId) {
        if (locationId != null) {
            return locationId;
        }
        return locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId()).stream()
                .filter(l -> "WAREHOUSE".equalsIgnoreCase(l.getType()))
                .map(Location::getId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_WAREHOUSE",
                        "No warehouse location available for migration receive"));
    }

    private ProductVariant resolveOrCreateVariant(String sku, String name, String barcode,
                                                  String hsCode, String lotNumber,
                                                  String palletTieRaw, String palletHighRaw,
                                                  String tempZone) {
        UUID tenantId = TenantContext.requireTenantId();
        if (sku != null && !sku.isBlank()) {
            var existing = variantRepository.findByTenantIdAndSku(tenantId, sku.trim());
            if (existing.isPresent()) {
                return applyEnterpriseFields(existing.get(), barcode, hsCode, lotNumber,
                        palletTieRaw, palletHighRaw, tempZone);
            }
        }
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(sku != null && !sku.isBlank() ? sku.trim() : "MIG");
        product.setName(name != null && !name.isBlank() ? name.trim() : (sku != null ? sku.trim() : "Migrated"));
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        String mintedSku = sku != null && !sku.isBlank()
                ? sku.trim()
                : skuMaskService.mintSku(null, null);
        variant.setSku(mintedSku);
        return applyEnterpriseFields(variant, barcode, hsCode, lotNumber, palletTieRaw, palletHighRaw, tempZone);
    }

    private ProductVariant applyEnterpriseFields(ProductVariant variant, String barcode, String hsCode,
                                                 String lotNumber, String palletTieRaw, String palletHighRaw,
                                                 String tempZone) {
        if (barcode != null && !barcode.isBlank()) {
            variant.setBarcode(barcode.trim());
        }
        if (hsCode != null && !hsCode.isBlank()) {
            variant.setHsTariffCode(hsCode.trim());
        }
        if (lotNumber != null && !lotNumber.isBlank()) {
            variant.setLotTracked(true);
        }
        Integer tie = parsePositiveInt(palletTieRaw);
        if (tie != null) {
            variant.setPalletTie(tie);
        }
        Integer high = parsePositiveInt(palletHighRaw);
        if (high != null) {
            variant.setPalletHigh(high);
        }
        if (tempZone != null && !tempZone.isBlank()) {
            variant.setStorageTempZone(normalizeTempZone(tempZone));
        }
        return variantRepository.save(variant);
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

    private static String cell(String[] cols, Map<String, Integer> headerIndex, String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        Integer idx = headerIndex.get(header.trim().toLowerCase(Locale.ROOT));
        if (idx == null || idx < 0 || idx >= cols.length) {
            return null;
        }
        String v = cols[idx].trim();
        return v.isEmpty() ? null : v;
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

    public record MigrationResult(int imported, List<String> errors) {
    }
}
