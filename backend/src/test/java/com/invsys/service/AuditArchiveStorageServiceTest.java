package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.media.MediaStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditArchiveStorageServiceTest {

    @Mock S3Client s3Client;

    private AuditArchiveStorageService service;
    private final UUID tenantId = UUID.fromString("b0000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        MediaStorageProperties props = new MediaStorageProperties();
        props.setBucket("invsys-media-test");
        service = new AuditArchiveStorageService(s3Client, props);
    }

    @Test
    void uploadArchiveUsesTenantIsolatedKey() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(successfulPut());

        String key = service.uploadArchive(tenantId, "batch.jsonl.gz", new byte[]{1, 2, 3});

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String expectedPrefix = "archives/" + tenantId + "/audit/"
                + String.format("%04d", today.getYear()) + "/"
                + String.format("%02d", today.getMonthValue()) + "/";
        assertThat(key).isEqualTo(expectedPrefix + "batch.jsonl.gz");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("invsys-media-test");
        assertThat(captor.getValue().key()).isEqualTo(key);
        assertThat(captor.getValue().contentType()).isEqualTo("application/gzip");
    }

    @Test
    void uploadArchiveRejectsPathTraversalFilename() {
        assertThatThrownBy(() -> service.uploadArchive(tenantId, "../evil.jsonl.gz", new byte[]{1}))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE");
    }

    @Test
    void uploadArchivePropagatesS3Failure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        assertThatThrownBy(() -> service.uploadArchive(tenantId, "batch.jsonl.gz", new byte[]{1}))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("AUDIT_ARCHIVE_UPLOAD_FAILED");
    }

    @Test
    void findArchiveKeysListsOnlyJsonlGzInMonthRange() {
        String janKey = "archives/" + tenantId + "/audit/2026/01/a.jsonl.gz";
        String febKey = "archives/" + tenantId + "/audit/2026/02/b.jsonl.gz";
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(listPage(List.of(
                        S3Object.builder().key(janKey).build(),
                        S3Object.builder().key("archives/" + tenantId + "/audit/2026/01/readme.txt").build())))
                .thenReturn(listPage(List.of(S3Object.builder().key(febKey).build())));

        List<String> keys = service.findArchiveKeys(
                tenantId, YearMonth.of(2026, 1), YearMonth.of(2026, 2));

        assertThat(keys).containsExactly(janKey, febKey);
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ListObjectsV2Request::prefix)
                .containsExactly(
                        "archives/" + tenantId + "/audit/2026/01/",
                        "archives/" + tenantId + "/audit/2026/02/");
    }

    @Test
    void findArchiveKeysRejectsInvertedRange() {
        assertThatThrownBy(() -> service.findArchiveKeys(
                        tenantId, YearMonth.of(2026, 3), YearMonth.of(2026, 1)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE_RANGE");
    }

    @Test
    void openArchiveStreamReturnsS3Body() throws Exception {
        String key = "archives/" + tenantId + "/audit/2026/07/batch.jsonl.gz";
        byte[] payload = "line\n".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream(payload));

        try (InputStream in = service.openArchiveStream(key)) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("invsys-media-test");
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void openArchiveStreamRejectsNonArchiveKey() {
        assertThatThrownBy(() -> service.openArchiveStream("tenants/evil.bin"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE_KEY");
    }

    @Test
    void findArchiveKeysRequiresTenantAndMonths() {
        assertThatThrownBy(() -> service.findArchiveKeys(null, YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE");
        assertThatThrownBy(() -> service.findArchiveKeys(tenantId, null, YearMonth.of(2026, 1)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE_RANGE");
    }

    @Test
    void findArchiveKeysFollowsContinuationToken() {
        String key1 = "archives/" + tenantId + "/audit/2026/01/a.jsonl.gz";
        String key2 = "archives/" + tenantId + "/audit/2026/01/b.jsonl.gz";
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key(key1).build())
                        .isTruncated(true)
                        .nextContinuationToken("tok-2")
                        .build())
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key(key2).build())
                        .isTruncated(false)
                        .build());

        assertThat(service.findArchiveKeys(tenantId, YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
                .containsExactly(key1, key2);

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(captor.capture());
        assertThat(captor.getAllValues().get(1).continuationToken()).isEqualTo("tok-2");
    }

    @Test
    void findArchiveKeysPropagatesListFailure() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("down").build());

        assertThatThrownBy(() -> service.findArchiveKeys(tenantId, YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("AUDIT_ARCHIVE_LIST_FAILED");
    }

    @Test
    void openArchiveStreamMapsMissingKey() {
        String key = "archives/" + tenantId + "/audit/2026/07/missing.jsonl.gz";
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("gone").build());

        assertThatThrownBy(() -> service.openArchiveStream(key))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("AUDIT_ARCHIVE_NOT_FOUND");
    }

    @Test
    void openArchiveStreamMapsS3Failure() {
        String key = "archives/" + tenantId + "/audit/2026/07/batch.jsonl.gz";
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        assertThatThrownBy(() -> service.openArchiveStream(key))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("AUDIT_ARCHIVE_READ_FAILED");
    }

    @Test
    void uploadArchiveRejectsEmptyPayloadAndNullTenant() {
        assertThatThrownBy(() -> service.uploadArchive(tenantId, "a.jsonl.gz", new byte[0]))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE");
        assertThatThrownBy(() -> service.uploadArchive(null, "a.jsonl.gz", new byte[]{1}))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_ARCHIVE");
    }

    private static PutObjectResponse successfulPut() {
        SdkHttpResponse http = SdkHttpFullResponse.builder().statusCode(200).build();
        return (PutObjectResponse) PutObjectResponse.builder()
                .sdkHttpResponse(http)
                .build();
    }

    private static ListObjectsV2Response listPage(List<S3Object> objects) {
        return ListObjectsV2Response.builder()
                .contents(objects)
                .isTruncated(false)
                .build();
    }

    private static ResponseInputStream<GetObjectResponse> responseStream(byte[] bytes) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    }
}
