package com.invsys.service;

import com.invsys.core.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditArchiveDownloadServiceTest {

    @Mock AuditArchiveStorageService archiveStorageService;

    private AuditArchiveDownloadService service;
    private final UUID tenantId = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        service = new AuditArchiveDownloadService(archiveStorageService);
    }

    @Test
    void streamDecompressedArchivesConcatenatesGunzippedJsonl() throws Exception {
        String key1 = "archives/" + tenantId + "/audit/2026/01/a.jsonl.gz";
        String key2 = "archives/" + tenantId + "/audit/2026/02/b.jsonl.gz";
        when(archiveStorageService.findArchiveKeys(
                eq(tenantId), eq(YearMonth.of(2026, 1)), eq(YearMonth.of(2026, 2))))
                .thenReturn(List.of(key1, key2));
        when(archiveStorageService.openArchiveStream(key1))
                .thenReturn(new ByteArrayInputStream(gzip("{\"id\":1}\n")));
        when(archiveStorageService.openArchiveStream(key2))
                .thenReturn(new ByteArrayInputStream(gzip("{\"id\":2}\n")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int streamed = service.streamDecompressedArchives(
                tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), out);

        assertThat(streamed).isEqualTo(2);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}\n{\"id\":2}\n");
        verify(archiveStorageService).openArchiveStream(key1);
        verify(archiveStorageService).openArchiveStream(key2);
    }

    @Test
    void streamRejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.streamDecompressedArchives(
                        tenantId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), new ByteArrayOutputStream()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE_RANGE");
    }

    @Test
    void streamRejectsMissingArgs() {
        assertThatThrownBy(() -> service.streamDecompressedArchives(
                        null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new ByteArrayOutputStream()))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE");
        assertThatThrownBy(() -> service.streamDecompressedArchives(
                        tenantId, null, LocalDate.of(2026, 1, 31), new ByteArrayOutputStream()))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE_RANGE");
    }

    @Test
    void streamEmptyKeyListReturnsZero() {
        when(archiveStorageService.findArchiveKeys(
                eq(tenantId), eq(YearMonth.of(2026, 1)), eq(YearMonth.of(2026, 1))))
                .thenReturn(List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int streamed = service.streamDecompressedArchives(
                tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), out);

        assertThat(streamed).isZero();
        assertThat(out.size()).isZero();
    }

    @Test
    void streamMapsIoFailures() throws Exception {
        String key = "archives/" + tenantId + "/audit/2026/01/a.jsonl.gz";
        when(archiveStorageService.findArchiveKeys(
                eq(tenantId), eq(YearMonth.of(2026, 1)), eq(YearMonth.of(2026, 1))))
                .thenReturn(List.of(key));
        when(archiveStorageService.openArchiveStream(key))
                .thenReturn(new ByteArrayInputStream("not-gzip".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> service.streamDecompressedArchives(
                        tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new ByteArrayOutputStream()))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("AUDIT_ARCHIVE_STREAM_FAILED");
    }

    private static byte[] gzip(String jsonl) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(jsonl.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }
}
