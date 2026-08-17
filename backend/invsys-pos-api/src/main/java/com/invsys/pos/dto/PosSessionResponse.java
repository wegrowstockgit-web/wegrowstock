package com.invsys.pos.dto;

import java.util.UUID;

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
        String companyName,
        UUID cashierId,
        UUID tenantId
) {
}
