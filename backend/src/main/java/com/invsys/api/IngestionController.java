package com.invsys.api;

import com.invsys.service.DataIngestionService;
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

    public IngestionController(DataIngestionService dataIngestionService) {
        this.dataIngestionService = dataIngestionService;
    }

    /**
     * Multipart import: spreadsheet/CSV file + stringified column mapping descriptor JSON.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResponse importFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("columnsMapping") String columnsMapping,
            @RequestParam(value = "locationId", required = false) UUID locationId) {
        DataIngestionService.ImportResult result =
                dataIngestionService.importFile(file, columnsMapping, locationId);
        return new ImportResponse(result.imported(), result.skipped(), result.errors());
    }

    public record ImportResponse(int imported, int skipped, List<String> errors) {
    }
}
