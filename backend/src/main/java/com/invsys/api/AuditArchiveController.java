package com.invsys.api;

import com.invsys.service.AuditArchiveDownloadService;
import com.invsys.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Administrative cold-archive retrieval for compliance auditors.
 * Streams gunzipped JSONL directly to the client (no heap buffering of full archives).
 */
@RestController
@RequestMapping("/api/v1/office/audit/archives")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AuditArchiveController {

    private final AuditArchiveDownloadService downloadService;

    public AuditArchiveController(AuditArchiveDownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping("/download")
    public void download(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         HttpServletResponse response) throws IOException {
        UUID tenantId = TenantContext.requireTenantId();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/x-ndjson");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit_archive.jsonl\"");
        response.setHeader("Cache-Control", "no-store");

        OutputStream out = response.getOutputStream();
        downloadService.streamDecompressedArchives(tenantId, startDate, endDate, out);
    }
}
