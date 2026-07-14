package com.invsys.media;

import com.invsys.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlValidatorTest {

    private final MediaUrlValidator validator = new MediaUrlValidator();

    @Test
    void allowsFirstPartyMediaPath() {
        String path = "/api/v1/media/11111111-1111-1111-1111-111111111111/content";
        assertThat(validator.validateAndNormalize(path)).isEqualTo(path);
    }

    @Test
    void blocksHttpAndLoopback() {
        assertThatThrownBy(() -> validator.validateAndNormalize("http://example.com/a.png"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https://127.0.0.1/a.png"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https://localhost/a.png"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void blocksPathTraversalInMediaPath() {
        assertThatThrownBy(() -> validator.validateAndNormalize("/api/v1/media/../secret/content"))
                .isInstanceOf(ApiException.class);
    }
}
