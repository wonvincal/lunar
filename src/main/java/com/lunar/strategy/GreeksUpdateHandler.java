package com.lunar.strategy;

import com.lunar.pricing.Greeks;

public interface GreeksUpdateHandler {
    void onGreeksUpdated(final Greeks greeks) throws Exception;
}
