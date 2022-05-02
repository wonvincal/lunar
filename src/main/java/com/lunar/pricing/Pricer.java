package com.lunar.pricing;

import com.lunar.entity.Security;

public interface Pricer {
    public boolean initialize();
    public boolean close();
    
    public boolean setDividendCurve(final long secSid, final DividendCurve curve);
    public boolean setYieldRate(final int yieldRate);
    public boolean registerEuroOption(final Security security);
    public boolean priceEuroOption(final Security security, final int spot, final int price, final int bid, final int ask, final Greeks greeks);

}
