package com.invsys.service.landedcost;

/**
 * Even allocation by received piece count — safe fallback when dimensional data is absent.
 * Never defaults missing metrics to zero (that is the competitor margin-distortion bug).
 */
public class QuantityStrategy extends AbstractProportionStrategy {

    public QuantityStrategy() {
        super("quantity", AbstractProportionStrategy::receivedQty);
    }
}
