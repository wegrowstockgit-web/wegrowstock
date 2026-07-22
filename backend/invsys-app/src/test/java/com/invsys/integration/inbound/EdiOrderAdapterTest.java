package com.invsys.integration.inbound;

import com.invsys.core.common.ApiException;
import com.invsys.service.EdiTranslationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdiOrderAdapterTest {

    @Mock EdiTranslationEngine ediTranslationEngine;

    private EdiOrderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EdiOrderAdapter(ediTranslationEngine);
    }

    @Test
    void supportsEdiAliases() {
        assertThat(adapter.supports("EDI")).isTrue();
        assertThat(adapter.supports("as2")).isTrue();
        assertThat(adapter.supports("X12")).isTrue();
        assertThat(adapter.supports("SHOPIFY")).isFalse();
    }

    @Test
    void translateRequiresPartnerHeader() {
        assertThatThrownBy(() -> adapter.translate("ISA*~", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Trading-Partner");
    }

    @Test
    void translateMapsEngineResult() {
        UUID partnerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(ediTranslationEngine.parseInbound850(eq(partnerId), eq("X12-PAYLOAD")))
                .thenReturn(new EdiTranslationEngine.InboundOrder(
                        partnerId,
                        customerId,
                        "PO-1",
                        List.of(new EdiTranslationEngine.InboundLine("SKU-A", new BigDecimal("5")))));

        CanonicalInboundOrder order = adapter.translate(
                "X12-PAYLOAD",
                Map.of(EdiOrderAdapter.HEADER_TRADING_PARTNER_ID, partnerId.toString()));

        assertThat(order.channelSource()).isEqualTo(ChannelSource.EDI);
        assertThat(order.externalOrderRef()).isEqualTo("PO-1");
        assertThat(order.customerIdentifier()).isEqualTo(customerId.toString());
        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().getFirst().sku()).isEqualTo("SKU-A");
        assertThat(order.lines().getFirst().quantity()).isEqualByComparingTo("5");
        assertThat(order.lines().getFirst().unitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void translateRejectsBlankPayloadAndBadPartnerId() {
        assertThatThrownBy(() -> adapter.translate(" ", Map.of(
                EdiOrderAdapter.HEADER_TRADING_PARTNER_ID, UUID.randomUUID().toString())))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapter.translate("X12", Map.of(
                EdiOrderAdapter.HEADER_TRADING_PARTNER_ID, "not-a-uuid")))
                .isInstanceOf(ApiException.class);
    }
}
