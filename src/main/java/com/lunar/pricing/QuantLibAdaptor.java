package com.lunar.pricing;

public class QuantLibAdaptor {
	static {
		System.loadLibrary("quantlib");		
	}
	
	public static final int FAIR_INDEX = 0;
	public static final int DELTA_INDEX = 1;
	public static final int VEGA_INDEX = 2;
	public static final int GAMMA_INDEX = 3;
	public static final int IVOL_INDEX = 4;
	
	public native int initialize(long valueDate);
	
	public native int close();
	
	public native int createDividendCurve(long secSid, int[] amounts, long[] dates);
	
	public native int createYieldCurve(int yieldRate);
	
	public native int addEuroOption(long secSid, long undSid, long maturityDate, boolean isCall, int strike);
	
	public native int[] priceEuroOption(long secSid, long undSid, int spot, int price);

}
