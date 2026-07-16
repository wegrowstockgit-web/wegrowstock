package com.invsys.integration.easypost;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Profile({"dev", "test", "docker", "default"})
public class MockEasyPostGateway implements EasyPostGateway, EasyPostClient {

    @Override
    public LabelResult purchaseLabel(String carrier, BigDecimal weightLb, String reference) {
        String tracking = "LBL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal postage = weightLb == null || weightLb.signum() <= 0
                ? BigDecimal.ZERO
                : weightLb.multiply(new BigDecimal("0.75")).add(new BigDecimal("4.50"));
        return new LabelResult("easypost_mock_" + tracking, tracking, postage);
    }
}
