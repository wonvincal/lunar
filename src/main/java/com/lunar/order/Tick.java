/**
 * 
 */
package com.lunar.order;

/**
 * @author Calvin
 *
 */
public class Tick {
	private int price;
	private int tickLevel;
	private long qty;
	private int numOrders;
	private int priceLevel; // for the sake of implementing the exchange recommended way
	
	public static Tick MIN_TICK = Tick.of(Integer.MIN_VALUE, Integer.MIN_VALUE);
	public static Tick MAX_TICK = Tick.of(Integer.MAX_VALUE, Integer.MAX_VALUE);
		
	public static Tick of(int tickLevel, int price){
		return new Tick(tickLevel, price);
	}
	
	public static Tick of(int tickLevel, int price, long qty, int numOrders){
		return new Tick(tickLevel, price, qty, numOrders);
	}

	public static Tick of(int tickLevel, int price, int priceLevel, long qty, int numOrders){
		return new Tick(tickLevel, price, priceLevel, qty, numOrders);
	}

	private Tick(int tickLevel, int price){
		this.price = price;
		this.tickLevel = tickLevel;
	}
	
	private Tick(int tickLevel, int price, long qty, int numOrders){
		this.price = price;
		this.tickLevel = tickLevel;
		this.qty = qty;
		this.numOrders = numOrders;
	}

	private Tick(int tickLevel, int price, int priceLevel, long qty, int numOrders){
		this.price = price;
		this.tickLevel = tickLevel;
		this.qty = qty;
		this.numOrders = numOrders;
		this.priceLevel = priceLevel;
	}

	public int priceLevel(){
		return priceLevel;
	}

	public int price(){
		return price;
	}

	public int tickLevel(){
		return tickLevel;
	}

	public long qty(){
		return qty;
	}

	public int numOrders(){
		return numOrders;
	}

	public Tick priceLevel(int value){
		this.priceLevel = value;
		return this;
	}

	public Tick qty(long value){
		this.qty = value;
		return this;
	}

	public Tick numOrders(int value){
		this.numOrders = value;
		return this;
	}

	public Tick tickLevel(int value){
		this.tickLevel = value;
		return this;
	}

	public Tick price(int value){
		this.price = value;
		return this;
	}
	
	@Override
	public String toString() {
		return String.format("[tickLevel:%d] %d@%d - %d [priceLevel:%d]",
				tickLevel,
				qty,
				price,
				numOrders,
				priceLevel);	
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + numOrders;
		result = prime * result + price;
		result = prime * result + (int) (qty ^ (qty >>> 32));
		result = prime * result + tickLevel;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Tick))
			return false;
		Tick other = (Tick) obj;
		if (numOrders != other.numOrders)
			return false;
		if (price != other.price)
			return false;
		if (qty != other.qty)
			return false;
		if (tickLevel != other.tickLevel)
			return false;
		return true;
	}
}
