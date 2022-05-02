package com.lunar.marketdata;

public class NoConversionSpreadTable implements SpreadTable {
	private final byte id;
	private final String name;
	private final SpreadTableDetails spreadTableDetails;
	
	NoConversionSpreadTable(byte id, String name){
		this.id = id;
		this.name = name;
		int from = 1;
		int to = 100_000;
		spreadTableDetails = SpreadTableDetails.of(from, to, 1);
	}

	@Override
	public int priceToTick(int price) {
		return price;
	}

	@Override
	public int priceToTickSize(int price) {
		return 1;
	}

	@Override
	public int tickToPrice(int tickLevel) {
		return tickLevel;
	}

	@Override
	public SpreadTableDetails detailsAtPrice(int price) {
		return spreadTableDetails;
	}

	@Override
	public int decimalPlace() {
		return 0;
	}

	@Override
	public int scale() {
		return 1;
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
