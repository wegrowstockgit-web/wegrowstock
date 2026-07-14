package com.invsys.media;

import com.invsys.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageContentValidatorTest {

    private final ImageContentValidator validator = new ImageContentValidator();

    @Test
    void acceptsPngMagicBytes() {
        byte[] png = TestImages.PNG_1X1;
        assertThat(validator.detectAndValidate(png, "image/png")).isEqualTo("image/png");
    }

    @Test
    void rejectsSpoofedContentType() {
        assertThatThrownBy(() -> validator.detectAndValidate(TestImages.PNG_1X1, "image/jpeg"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void rejectsNonImagePayload() {
        assertThatThrownBy(() -> validator.detectAndValidate("not-an-image".getBytes(), "image/png"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void detectsJpegGifAndWebp() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(ImageContentValidator.detect(jpeg)).isEqualTo("image/jpeg");

        byte[] gif = new byte[] {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
        assertThat(ImageContentValidator.detect(gif)).isEqualTo("image/gif");

        byte[] webp = new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(ImageContentValidator.detect(webp)).isEqualTo("image/webp");
    }
}
