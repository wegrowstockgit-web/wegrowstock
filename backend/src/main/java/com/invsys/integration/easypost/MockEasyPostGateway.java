package com.invsys.integration.easypost;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic EasyPost stub for local / docker / CI. Never active under {@code prod}.
 */
@Component
@Profile({"dev", "test", "docker", "default"})
public class MockEasyPostGateway implements EasyPostGateway, EasyPostClient {

    @Override
    public LabelResult purchaseLabel(String carrier, BigDecimal weightLb, String reference) {
        return shopAndBuyCheapest(
                new ParcelSpec(new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("6"), weightLb),
                reference,
                LabelOptions.pdfDefault())
                .purchased();
    }

    @Override
    public List<RateQuote> shopRates(ParcelSpec parcel, String reference, LabelOptions labelOptions) {
        return buildRates(parcel);
    }

    @Override
    public ShopResult shopAndBuyCheapest(ParcelSpec parcel, String reference, LabelOptions labelOptions) {
        LabelOptions opts = labelOptions != null ? labelOptions : LabelOptions.pdfDefault();
        List<RateQuote> rates = buildRates(parcel);
        RateQuote cheapest = rates.stream()
                .min(Comparator.comparing(RateQuote::rate))
                .orElseThrow();
        String tracking = "LBL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String fileType = opts.normalizedFormat();
        String labelRef = "ZPL".equals(fileType)
                ? mockZpl(tracking, opts.normalizedSize())
                : "easypost_mock_" + tracking + ".pdf";
        LabelResult purchased = new LabelResult(
                labelRef,
                tracking,
                cheapest.rate(),
                cheapest.carrier(),
                cheapest.service(),
                fileType);
        return new ShopResult(rates, purchased);
    }

    @Override
    public LabelResult purchaseReturnLabel(ParcelSpec parcel, String reference) {
        BigDecimal postage = estimateCheapestRate(parcel, reference);
        String tracking = "RMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new LabelResult(
                "https://labels.easypost.mock/return/" + tracking + ".pdf",
                tracking,
                postage,
                "USPS",
                "GroundAdvantage",
                "PDF");
    }

    private static List<RateQuote> buildRates(ParcelSpec parcel) {
        BigDecimal weight = parcel == null || parcel.weightLb() == null || parcel.weightLb().signum() <= 0
                ? BigDecimal.ONE
                : parcel.weightLb();
        BigDecimal base = weight.multiply(new BigDecimal("0.75")).add(new BigDecimal("4.50"));
        return List.of(
                new RateQuote("USPS", "Priority", "mock_usps_priority",
                        base.setScale(2, RoundingMode.HALF_UP), "USD"),
                new RateQuote("UPS", "Ground", "mock_ups_ground",
                        base.add(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP), "USD"),
                new RateQuote("FedEx", "HomeDelivery", "mock_fedex_home",
                        base.add(new BigDecimal("2.10")).setScale(2, RoundingMode.HALF_UP), "USD"));
    }

    @Override
    public BigDecimal estimateCheapestRate(ParcelSpec parcel, String reference) {
        // Distinct from buy rates so tests can prove estimate ≠ purchase path
        BigDecimal weight = parcel == null || parcel.weightLb() == null || parcel.weightLb().signum() <= 0
                ? BigDecimal.ONE
                : parcel.weightLb();
        return weight.multiply(new BigDecimal("0.55")).add(new BigDecimal("3.75"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String mockZpl(String tracking, String size) {
        return "^XA"
                + "^FO50,50^A0N,40,40^FDInvSys " + size + "^FS"
                + "^FO50,110^A0N,30,30^FD" + tracking + "^FS"
                + "^FO50,160^BY2^BCN,80,Y,N,N^FD" + tracking + "^FS"
                + "^XZ";
    }
}
