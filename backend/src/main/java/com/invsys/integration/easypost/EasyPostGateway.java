package com.invsys.integration.easypost;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface EasyPostGateway {

    /**
     * Legacy weight-only purchase (kept for callers that do not cartonize).
     */
    LabelResult purchaseLabel(String carrier, BigDecimal weightLb, String reference);

    /**
     * Rate-shop across carrier accounts for the given parcel, auto-buy the cheapest rate.
     */
    default ShopResult shopAndBuyCheapest(ParcelSpec parcel, String reference) {
        return shopAndBuyCheapest(parcel, reference, LabelOptions.pdfDefault());
    }

    /**
     * Rate-shop + buy with carrier label format driven by workstation print_mode.
     */
    ShopResult shopAndBuyCheapest(ParcelSpec parcel, String reference, LabelOptions labelOptions);

    /**
     * Rate-shop only — must never purchase postage. Used for RMA cost estimates.
     */
    List<RateQuote> shopRates(ParcelSpec parcel, String reference, LabelOptions labelOptions);

    /** Cheapest rate without purchasing (for RMA review cost display). */
    default BigDecimal estimateCheapestRate(ParcelSpec parcel, String reference) {
        return shopRates(parcel, reference, LabelOptions.pdfDefault()).stream()
                .min(Comparator.comparing(RateQuote::rate))
                .map(RateQuote::rate)
                .orElseThrow(() -> new IllegalStateException("EasyPost returned no rates"));
    }

    /** Purchase a prepaid PDF return label for an approved RMA. */
    LabelResult purchaseReturnLabel(ParcelSpec parcel, String reference);

    /**
     * Ship-to / ship-from address for live EasyPost. Mock gateways may ignore these.
     */
    record AddressSpec(
            String name,
            String company,
            String street1,
            String street2,
            String city,
            String state,
            String zip,
            String country,
            String phone,
            String email
    ) {
        public static AddressSpec fromMap(Map<String, ?> map, String fallbackName) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            String name = str(map, "name");
            if (name == null || name.isBlank()) {
                name = fallbackName;
            }
            String street1 = first(map, "street1", "address1", "line1", "street");
            String city = str(map, "city");
            String state = first(map, "state", "province", "region");
            String zip = first(map, "zip", "postal_code", "postalCode");
            String country = first(map, "country", "country_code");
            if (country == null || country.isBlank()) {
                country = "US";
            }
            if (street1 == null || city == null || zip == null) {
                return null;
            }
            return new AddressSpec(
                    name,
                    str(map, "company"),
                    street1,
                    first(map, "street2", "address2", "line2"),
                    city,
                    state,
                    zip,
                    country,
                    first(map, "phone", "phone_number"),
                    str(map, "email"));
        }

        private static String str(Map<String, ?> map, String key) {
            Object v = map.get(key);
            return v == null ? null : String.valueOf(v).trim();
        }

        private static String first(Map<String, ?> map, String... keys) {
            for (String key : keys) {
                String v = str(map, key);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return null;
        }
    }

    record ParcelSpec(
            BigDecimal lengthIn,
            BigDecimal widthIn,
            BigDecimal heightIn,
            BigDecimal weightLb,
            AddressSpec toAddress,
            AddressSpec fromAddress,
            boolean isReturn
    ) {
        public ParcelSpec(BigDecimal lengthIn, BigDecimal widthIn, BigDecimal heightIn, BigDecimal weightLb) {
            this(lengthIn, widthIn, heightIn, weightLb, null, null, false);
        }

        public ParcelSpec withAddresses(AddressSpec to, AddressSpec from) {
            return new ParcelSpec(lengthIn, widthIn, heightIn, weightLb, to, from, isReturn);
        }

        public ParcelSpec asReturn() {
            return new ParcelSpec(lengthIn, widthIn, heightIn, weightLb, toAddress, fromAddress, true);
        }
    }

    /**
     * @param labelFormat EasyPost options.label_format — PDF or ZPL
     * @param labelSize   EasyPost options.label_size — e.g. 4x6
     */
    record LabelOptions(String labelFormat, String labelSize) {
        public static LabelOptions pdfDefault() {
            return new LabelOptions("PDF", "4x6");
        }

        public static LabelOptions fromWorkstation(String printMode, String size) {
            String fmt = printMode != null && "ZPL".equalsIgnoreCase(printMode.trim())
                    ? "ZPL"
                    : "PDF";
            String sz = (size == null || size.isBlank()) ? "4x6" : size.trim();
            return new LabelOptions(fmt.toUpperCase(Locale.ROOT), sz);
        }

        public String normalizedFormat() {
            return labelFormat == null || labelFormat.isBlank()
                    ? "PDF"
                    : labelFormat.trim().toUpperCase(Locale.ROOT);
        }

        public String normalizedSize() {
            return labelSize == null || labelSize.isBlank() ? "4x6" : labelSize.trim();
        }
    }

    record RateQuote(
            String carrier,
            String service,
            String rateId,
            BigDecimal rate,
            String currency
    ) {
    }

    record LabelResult(
            String labelRef,
            String trackingNumber,
            BigDecimal postageAmount,
            String carrier,
            String service,
            String labelFileType
    ) {
        public LabelResult(String labelRef, String trackingNumber, BigDecimal postageAmount) {
            this(labelRef, trackingNumber, postageAmount, "EASYPOST", null, "PDF");
        }

        public LabelResult(String labelRef, String trackingNumber, BigDecimal postageAmount,
                           String carrier, String service) {
            this(labelRef, trackingNumber, postageAmount, carrier, service, "PDF");
        }
    }

    record ShopResult(List<RateQuote> rates, LabelResult purchased) {
    }
}
