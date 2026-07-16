package com.invsys.media;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.common.ApiException;
import com.invsys.domain.MediaObject;
import com.invsys.repository.MediaObjectRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the S3-only media invariant: Spring must wire {@link S3ObjectStorage},
 * uploads must land in the bucket, and filesystem-style keys are rejected.
 */
class ObjectStorageS3OnlyTest extends AbstractIntegrationTest {

    @Autowired ApplicationContext applicationContext;
    @Autowired ObjectStorage objectStorage;
    @Autowired MediaUploadService mediaUploadService;
    @Autowired MediaObjectRepository mediaObjectRepository;
    @Autowired TestDataHelper testDataHelper;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void soleObjectStorageBeanIsS3Compatible() {
        Map<String, ObjectStorage> beans = applicationContext.getBeansOfType(ObjectStorage.class);
        assertThat(beans).hasSize(1);
        assertThat(beans.values().iterator().next()).isInstanceOf(S3ObjectStorage.class);
        assertThat(objectStorage).isInstanceOf(S3ObjectStorage.class);
    }

    @Test
    void multipartUploadPersistsBytesInS3NotLocalDisk() {
        UUID tenantId = testDataHelper.createTenant("S3 Only Co", "s3o-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "cam.jpg", "image/png", TestImages.PNG_1X1);
        MediaObject media = mediaUploadService.upload(file, MediaUploadService.UploadKind.AVATAR);

        assertThat(media.getStorageKey()).doesNotContain("file:");
        assertThat(media.getStorageKey()).doesNotStartWith("/");
        assertThat(media.getStorageKey()).startsWith(tenantId + "/");
        assertThat(objectStorage.exists(media.getStorageKey())).isTrue();

        MediaObject reloaded = mediaObjectRepository.findByTenantIdAndId(tenantId, media.getId()).orElseThrow();
        assertThat(reloaded.getStorageKey()).isEqualTo(media.getStorageKey());
        assertThat(objectStorage.exists(reloaded.getStorageKey())).isTrue();
    }

    @Test
    void rejectsFilesystemStyleStorageKeys() {
        assertThatThrownBy(() -> objectStorage.put("file:///tmp/evil.png", TestImages.PNG_1X1, "image/png"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("S3-compatible");
        assertThatThrownBy(() -> objectStorage.put("C:\\uploads\\x.png", TestImages.PNG_1X1, "image/png"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> objectStorage.put("https://evil.example/x.png", TestImages.PNG_1X1, "image/png"))
                .isInstanceOf(ApiException.class);
    }
}
