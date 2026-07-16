package com.invsys.integration.easypost;

import java.math.BigDecimal;

public interface EasyPostGateway {
    LabelResult purchaseLabel(String carrier, BigDecimal weightLb, String reference);

    record LabelResult(String labelRef, String trackingNumber, BigDecimal postageAmount) {
    }
}
