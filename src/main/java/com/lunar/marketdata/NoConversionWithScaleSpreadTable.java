package com.lunar.marketdata;

public class NoConversionWithScaleSpreadTable implements SpreadTable {
	private final byte id;
	private final String name;
	private final int decimalPlace;
	private final int scale;
	private final SpreadTableDetails spreadTableDetails;
	
	NoConversionWithScaleSpreadTable(byte id, String name, int decimalPlace){
		this.id = id;
		this.name = name;
		this.decimalPlace = decimalPlace;
		this.scale = (int)Math.pow(10, decimalPlace);
		int from = 1 * scale;
		int to = 100_000 * scale;
		spreadTableDetails = SpreadTableDetails.of(from, to, scale);
	}

	@Override
	public int priceToTick(int price) {
		return price / scale;
	}

	@Override
	public int priceToTickSize(int price) {
		return scale;
	}

	@Override
	public int tickToPrice(int tickLevel) {
		return tickLevel * scale;
	}

	@Override
	public SpreadTableDetails detailsAtPrice(int price) {
		return spreadTableDetails;
	}

	@Override
	public int decimalPlace() {
		return decimalPlace;
	}

	@Override
	public int scale() {
		return scale;
	}
	
	@Override
	public int maxLevel() {
		return 0;
	}

	@Override
	public String toString() {
		return "id:" + this.id + ", name:" + name;
	}

	@Override
	public byte id() {
		return id;
	}
}
