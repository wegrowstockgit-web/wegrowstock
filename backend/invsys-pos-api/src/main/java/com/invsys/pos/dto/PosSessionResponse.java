package com.invsys.pos.dto;

public record PosSessionResponse(
        boolean posEnabled,
        String module,
        String tier,
        String language,
        String languageSource,
        String currency,
        String currencySource,
        String placeLanguage,
        String placeCurrency,
        String localeTag,
        String taxRegionHint,
        String timezone,
        String companyName
) {
}
