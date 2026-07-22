package com.invsys.media;

import com.invsys.core.common.ApiException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

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
    void blocksRfc1918AndLinkLocalLiteralHosts() {
        assertThatThrownBy(() -> validator.validateAndNormalize("https://10.1.2.3/secret"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https://172.16.0.1/secret"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https://192.168.1.50/secret"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void blockedAddressRecognizesPrivateSubnets() throws Exception {
        assertThat(MediaUrlValidator.isBlockedAddress(InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(MediaUrlValidator.isBlockedAddress(InetAddress.getByName("172.31.255.255"))).isTrue();
        assertThat(MediaUrlValidator.isBlockedAddress(InetAddress.getByName("192.168.0.1"))).isTrue();
        assertThat(MediaUrlValidator.isBlockedAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(MediaUrlValidator.isBlockedAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(MediaUrlValidator.isBlockedAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    }

    @Test
    void blocksPathTraversalInMediaPath() {
        assertThatThrownBy(() -> validator.validateAndNormalize("/api/v1/media/../secret/content"))
                .isInstanceOf(ApiException.class);
    }
}
