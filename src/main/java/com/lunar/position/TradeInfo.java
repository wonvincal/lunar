package com.lunar.position;

import com.lunar.message.io.sbe.Side;

public class TradeInfo {
	private final long quantity;
	private final double price;
	private final double fees;
	private final double commission;
	private final Side side;
	private final int tradeSid;
	
	static TradeInfo of(int tradeSid, double price, long quantity, Side side, double fees, double commission){
		return new TradeInfo(tradeSid, price, quantity, side, fees, commission);
	}
	
	TradeInfo(int tradeSid, double price, long quantity, Side side, double fees, double commission){
		this.tradeSid = tradeSid;
		this.price = price;
		this.quantity = quantity;
		this.side = side;
		this.fees = fees;
		this.commission = commission;
	}
	
	@Override
	public String toString() {
		return "tradeSid: " + tradeSid + ", price: " + price + ", qty: " + quantity + ", side: " + side.name();
	}
	
	public long quantity(){
		return quantity;
	}
	
	public double price(){
		return price;
	}
	
	public Side side(){
		return side;
	}
	
	public int tradeSid(){
		return tradeSid;
	}
	
	public double fees(){
		return fees;
	}
	
	public double commission(){
		return commission;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + tradeSid;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TradeInfo other = (TradeInfo) obj;
		if (tradeSid != other.tradeSid)
			return false;
		return true;
	}
	
}
