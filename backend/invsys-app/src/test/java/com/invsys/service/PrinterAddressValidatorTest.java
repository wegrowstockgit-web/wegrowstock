package com.invsys.service;

import com.invsys.core.common.ApiException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrinterAddressValidatorTest {

    @Test
    void allowsWarehouseLanAddresses() {
        assertThatCode(() -> PrinterAddressValidator.assertSafePrinterTarget("192.168.1.50"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PrinterAddressValidator.assertSafePrinterTarget("10.20.30.40"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksMetadataLoopbackAndLinkLocal() {
        assertThatThrownBy(() -> PrinterAddressValidator.assertSafePrinterTarget("127.0.0.1"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PrinterAddressValidator.assertSafePrinterTarget("169.254.169.254"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PrinterAddressValidator.assertSafePrinterTarget("localhost"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void classifiesBlockedInetAddresses() throws Exception {
        assertThat(PrinterAddressValidator.isBlockedSsrfTarget(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(PrinterAddressValidator.isBlockedSsrfTarget(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(PrinterAddressValidator.isBlockedSsrfTarget(InetAddress.getByName("192.168.0.20"))).isFalse();
    }
}
