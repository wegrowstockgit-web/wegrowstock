package com.invsys.api;

import com.invsys.api.dto.PreflightResponse;
import com.invsys.service.DataIngestionService;
import com.invsys.service.LegacyErpMigrationService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingestion")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class IngestionController {

    private final DataIngestionService dataIngestionService;
    private final LegacyErpMigrationService legacyErpMigrationService;

    public IngestionController(DataIngestionService dataIngestionService,
                               LegacyErpMigrationService legacyErpMigrationService) {
        this.dataIngestionService = dataIngestionService;
        this.legacyErpMigrationService = legacyErpMigrationService;
    }

    /**
     * Cold-start pre-flight: parse CSV and return a resolution map without ledger writes.
     */
    @PostMapping(value = "/preflight", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreflightResponse preflight(
            @RequestPart("file") MultipartFile file,
            @RequestParam("columnsMapping") String columnsMapping,
            @RequestParam(value = "locationId", required = false) UUID locationId) {
        return dataIngestionService.preflight(file, columnsMapping, locationId);
    }

    /**
     * Bulk-create catalog products for MISSING_PRODUCT rows (no inventory receive).
     */
    @PostMapping(value = "/create-missing-products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreateMissingResponse createMissingProducts(
            @RequestPart("file") MultipartFile file,
            @RequestParam("columnsMapping") String columnsMapping) {
        DataIngestionService.CreateMissingResult result =
                dataIngestionService.createMissingProducts(file, columnsMapping);
        return new CreateMissingResponse(result.created(), result.skipped(), result.errors());
    }

    /**
     * Multipart import: spreadsheet/CSV file + stringified column mapping descriptor JSON.
     * Rows with unresolved missing references are skipped (not written to inventory_ledger).
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResponse importFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("columnsMapping") String columnsMapping,
            @RequestParam(value = "locationId", required = false) UUID locationId,
            @RequestParam(value = "createMissingProducts", defaultValue = "false")
            boolean createMissingProducts,
            @RequestParam(value = "createMissingLocations", defaultValue = "false")
            boolean createMissingLocations) {
        DataIngestionService.ImportResult result = dataIngestionService.importFile(
                file, columnsMapping, locationId,
                new DataIngestionService.ImportOptions(createMissingProducts, createMissingLocations));
        return new ImportResponse(result.imported(), result.skipped(), result.errors(),
                result.fileChecksumSha256());
    }

    /**
     * Legacy ERP cutover: single-transaction bulk products/variants + INITIAL_MIGRATION receives.
     */
    @PostMapping(value = "/legacy-migration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MigrationResponse legacyMigration(
            @RequestPart("file") MultipartFile file,
            @RequestParam("columnsMapping") String columnsMapping,
            @RequestParam(value = "locationId", required = false) UUID locationId) {
        LegacyErpMigrationService.MigrationResult result =
                legacyErpMigrationService.migrate(file, columnsMapping, locationId);
        return new MigrationResponse(result.imported(), result.errors());
    }

    public record ImportResponse(int imported, int skipped, List<String> errors, String fileChecksumSha256) {
    }

    public record CreateMissingResponse(int created, int skipped, List<String> errors) {
    }

    public record MigrationResponse(int imported, List<String> errors) {
    }
}
