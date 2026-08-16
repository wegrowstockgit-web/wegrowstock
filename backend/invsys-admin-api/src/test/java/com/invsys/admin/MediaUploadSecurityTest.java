package com.invsys.admin;

import com.invsys.core.common.ApiException;
import com.invsys.media.ImageContentValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUploadSecurityTest {

    private final ImageContentValidator validator = new ImageContentValidator();

    @Test
    void rejectsElfDisguisedAsPng() {
        byte[] elf = new byte[32];
        elf[0] = 0x7F;
        elf[1] = 'E';
        elf[2] = 'L';
        elf[3] = 'F';
        assertThatThrownBy(() -> validator.detectAndValidate(elf, "image/png"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(api.getCode()).isEqualTo("INVALID_IMAGE");
                });
    }

    @Test
    void rejectsPeDisguisedAsPng() {
        byte[] pe = new byte[32];
        pe[0] = 'M';
        pe[1] = 'Z';
        pe[2] = 0x00;
        pe[3] = 0x00;
        assertThatThrownBy(() -> validator.detectAndValidate(pe, "image/png"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsSvgWithScript() {
        byte[] svg = """
                <?xml version="1.0"?>
                <svg xmlns="http://www.w3.org/2000/svg">
                  <script>alert(1)</script>
                </svg>
                """.getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.detectAndValidate(svg, "image/svg+xml"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("UNSAFE_SVG"));
    }
}
