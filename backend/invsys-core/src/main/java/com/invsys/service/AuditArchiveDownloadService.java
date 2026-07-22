package com.invsys.service;

import com.invsys.core.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Streams decompressed cold audit archives (JSONL) to an HTTP client with a
 * constant memory footprint — no full-file buffering on the heap.
 */
@Service
public class AuditArchiveDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveDownloadService.class);

    private final AuditArchiveStorageService archiveStorageService;

    public AuditArchiveDownloadService(AuditArchiveStorageService archiveStorageService) {
        this.archiveStorageService = archiveStorageService;
    }

    /**
     * For each S3 {@code .jsonl.gz} in the inclusive date range, gunzip on the fly
     * and {@link InputStream#transferTo(OutputStream)} into {@code out}.
     *
     * @return number of archive objects streamed
     */
    public int streamDecompressedArchives(UUID tenantId, LocalDate startDate, LocalDate endDate, OutputStream out) {
        if (tenantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE", "tenantId is required");
        }
        if (startDate == null || endDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_RANGE",
                    "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_RANGE",
                    "startDate must be on or before endDate");
        }

        YearMonth startMonth = YearMonth.from(startDate);
        YearMonth endMonth = YearMonth.from(endDate);
        List<String> keys = archiveStorageService.findArchiveKeys(tenantId, startMonth, endMonth);
        log.info("Streaming audit archives tenant={} keys={} range={}..{}",
                tenantId, keys.size(), startDate, endDate);

        int streamed = 0;
        try {
            for (String key : keys) {
                try (InputStream s3Stream = archiveStorageService.openArchiveStream(key);
                     GZIPInputStream gzip = new GZIPInputStream(s3Stream)) {
                    gzip.transferTo(out);
                }
                streamed++;
            }
            out.flush();
            return streamed;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_STREAM_FAILED",
                    "Failed while streaming decompressed audit archives")
                    .withProperty("cause", ex.getMessage());
        }
    }
}
