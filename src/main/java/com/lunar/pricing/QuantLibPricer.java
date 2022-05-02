package com.lunar.pricing;

import java.time.LocalDate;

import com.lunar.core.SystemClock;
import com.lunar.entity.Security;
import com.lunar.message.io.sbe.PutOrCall;

public class QuantLibPricer implements Pricer {
	private SystemClock systemClock;
	private QuantLibAdaptor quantlibJni;
	
	public QuantLibPricer(final SystemClock systemClock) {
		this.systemClock = systemClock;
		quantlibJni = new QuantLibAdaptor();
	}
	
	@Override
	public boolean initialize() {
		final int result = quantlibJni.initialize(localDateToLong(systemClock.date()));
		return result == 0;
	}

	@Override
	public boolean close() {
		final int result = quantlibJni.close();
		return result == 0;
	}

	@Override
	public boolean setDividendCurve(final long secSid, final DividendCurve curve) {
		final int result = quantlibJni.createDividendCurve(secSid, curve.amounts(), curve.dates());
		return result == 0;
	}

	@Override
	public boolean setYieldRate(final int yieldRate) {
		final int result = quantlibJni.createYieldCurve(yieldRate);
		return result == 0;
	}

	@Override
	public boolean registerEuroOption(final Security security) {
		final int result = quantlibJni.addEuroOption(security.sid(), security.underlyingSid(), localDateToLong(security.maturity().get()), security.putOrCall() == PutOrCall.CALL, security.strikePrice());
		return result == 0;
	}

	@Override
	public boolean priceEuroOption(final Security security, final int spot, final int price, final int bid, final int ask, final Greeks greeks) {
		final int[] results = quantlibJni.priceEuroOption(security.sid(), security.underlyingSid(), spot, price);
		if (results == null) {
			return false;
		}
		greeks.refSpot(spot);
		greeks.bid(bid);
		greeks.ask(ask);
		greeks.delta(results[QuantLibAdaptor.DELTA_INDEX]);
		greeks.vega(results[QuantLibAdaptor.VEGA_INDEX]);
		greeks.gamma(results[QuantLibAdaptor.GAMMA_INDEX]);
		greeks.impliedVol(results[QuantLibAdaptor.IVOL_INDEX]);
		if (greeks.vega() != 0) {
	        greeks.bidImpliedVol(greeks.impliedVol() + (bid - price) * 100000 / greeks.vega());
	        greeks.askImpliedVol(greeks.impliedVol() + (ask - price) * 100000 / greeks.vega());
		}
		else {
		    greeks.bidImpliedVol(greeks.impliedVol());
		    greeks.askImpliedVol(greeks.impliedVol());
		}
		return true;
	}
	
	private long localDateToLong(final LocalDate date) {
		return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
	}
	
}
