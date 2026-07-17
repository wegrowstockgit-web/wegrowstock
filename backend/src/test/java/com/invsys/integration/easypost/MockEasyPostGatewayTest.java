package com.invsys.integration.easypost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockEasyPostGatewayTest {

    private final MockEasyPostGateway gateway = new MockEasyPostGateway();

    @Test
    void shopAndBuyCheapestPicksLowestRate() {
        EasyPostGateway.ShopResult result = gateway.shopAndBuyCheapest(
                new EasyPostGateway.ParcelSpec(
                        new BigDecimal("14"), new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("3.5")),
                "SO-TEST");

        assertThat(result.rates()).hasSizeGreaterThanOrEqualTo(2);
        BigDecimal min = result.rates().stream()
                .map(EasyPostGateway.RateQuote::rate)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        assertThat(result.purchased().postageAmount()).isEqualByComparingTo(min);
        assertThat(result.purchased().carrier()).isEqualTo("USPS");
        assertThat(result.purchased().trackingNumber()).startsWith("LBL-");
        assertThat(result.purchased().labelRef()).contains("easypost_mock_");
        assertThat(result.purchased().labelFileType()).isEqualTo("PDF");
    }

    @Test
    void shopAndBuyCheapestRequestsZplWhenWorkstationIsZpl() {
        EasyPostGateway.ShopResult result = gateway.shopAndBuyCheapest(
                new EasyPostGateway.ParcelSpec(
                        new BigDecimal("14"), new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("3.5")),
                "SO-ZPL",
                EasyPostGateway.LabelOptions.fromWorkstation("ZPL", "4x6"));

        assertThat(result.purchased().labelFileType()).isEqualTo("ZPL");
        assertThat(result.purchased().labelRef()).startsWith("^XA");
        assertThat(result.purchased().labelRef()).contains("^XZ");
    }

    @Test
    void estimateCheapestRateDoesNotPurchaseLabel() {
        EasyPostGateway.ParcelSpec parcel = new EasyPostGateway.ParcelSpec(
                new BigDecimal("14"), new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("3.5"));
        BigDecimal estimate = gateway.estimateCheapestRate(parcel, "RMA-EST");
        EasyPostGateway.ShopResult bought = gateway.shopAndBuyCheapest(parcel, "RMA-BUY");

        assertThat(estimate).isNotEqualByComparingTo(bought.purchased().postageAmount());
        assertThat(gateway.shopRates(parcel, "RMA-RATES", EasyPostGateway.LabelOptions.pdfDefault()))
                .isNotEmpty();
    }
}
