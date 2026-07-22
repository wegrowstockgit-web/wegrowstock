package com.invsys.integration.inbound;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelSourceTest {

    @Test
    void fromPathNormalizesCase() {
        assertThat(ChannelSource.fromPath("shopify")).isEqualTo(ChannelSource.SHOPIFY);
        assertThat(ChannelSource.fromPath("EDI")).isEqualTo(ChannelSource.EDI);
        assertThat(ChannelSource.fromPath(" amazon ")).isEqualTo(ChannelSource.AMAZON);
    }

    @Test
    void fromPathRejectsBlank() {
        assertThatThrownBy(() -> ChannelSource.fromPath(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChannelSource.fromPath(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
