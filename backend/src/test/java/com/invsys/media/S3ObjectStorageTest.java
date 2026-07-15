package com.invsys.media;

import com.invsys.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class S3ObjectStorageTest extends AbstractIntegrationTest {

    @Autowired ObjectStorage objectStorage;

    @Test
    void putOpenAndExistsAgainstMinio() throws Exception {
        String key = "tenants/" + UUID.randomUUID() + "/sample.png";
        objectStorage.put(key, TestImages.PNG_1X1, "image/png");
        assertThat(objectStorage.exists(key)).isTrue();
        try (var in = objectStorage.open(key)) {
            assertThat(in.readAllBytes()).isEqualTo(TestImages.PNG_1X1);
        }
        assertThat(objectStorage.exists("tenants/missing-" + UUID.randomUUID() + ".png")).isFalse();
        objectStorage.delete(key);
        assertThat(objectStorage.exists(key)).isFalse();
    }
}
