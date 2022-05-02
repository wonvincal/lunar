package com.lunar.marketdata;

public class SpreadTableDetails {
	private final int fromPrice;
	private final int toPriceExclusive;
	private final int spread;
	private final SpreadTableDetails previous;
	
	private static final SpreadTableDetails NULL_DETAILS = new SpreadTableDetails(-1, 0, 1, null);
	
	public SpreadTableDetails(int fromPrice, int toPriceExclusive, int spread, SpreadTableDetails previous){
		if (fromPrice > toPriceExclusive){
			throw new IllegalArgumentException("fromPrice(" + fromPrice + ") must be less than toPrice(" + toPriceExclusive + ")");
		}
		if (spread <= 0){
			throw new IllegalArgumentException("spread(" + spread + ") must be greater than zero");
		}
		this.fromPrice = fromPrice;
		this.toPriceExclusive = toPriceExclusive;
		this.spread = spread;
		this.previous = previous;
	}
	
	public static SpreadTableDetails of(int fromPrice, int toPriceExclusive, int spread){
		return new SpreadTableDetails(fromPrice, toPriceExclusive, spread, NULL_DETAILS);
	}

	public static SpreadTableDetails of(int fromPrice, int toPriceExclusive, int spread, SpreadTableDetails previous){
		return new SpreadTableDetails(fromPrice, toPriceExclusive, spread, previous);
	}

	public int fromPrice() {
		return fromPrice;
	}

	public int toPriceExclusive() {
		return toPriceExclusive;
	}

	public int spread() {
		return spread;
	}

	public SpreadTableDetails previous(){
		return previous;
	}
}
