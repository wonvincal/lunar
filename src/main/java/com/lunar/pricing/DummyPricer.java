package com.lunar.pricing;

import com.lunar.entity.Security;

public class DummyPricer implements Pricer {
    @Override
    public boolean initialize() {
        return true;
    }

    @Override
    public boolean close() {
        return true;
    }

    @Override
    public boolean setDividendCurve(long secSid, DividendCurve curve) {
        return true;
    }

    @Override
    public boolean setYieldRate(int yieldRate) {
        return true;
    }

    @Override
    public boolean registerEuroOption(Security security) {
        return true;
    }

    @Override
    public boolean priceEuroOption(Security security, int spot, int price, int bid, int ask, Greeks greeks) {
        try {
            Thread.sleep(100);
        } catch (final InterruptedException e) {
        }
        return false;
    }

}
