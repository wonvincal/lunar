package com.lunar.order;

/**
 * Position class to hold different information for order execution validation
 * @author wongca
 *
 */
public class Position {
	/**
	 * This volatile long position will be 'write' by one thread and
	 * 'read' by another.
	 * 
	 * Since all trades will be sent back to OMES,
	 * this object will no longer
	 * 
	 * TODO: Use Unsafe.putOrderedLong / lazySet to get better performance
	 */
	private volatile long longPosition;
	
	public static Position of(){ return new Position(); }
	
	Position(){
		this.longPosition = 0;
	}
	
	public long volatileLongPosition(){
		return longPosition;
	}
	
	public Position longPosition(long value){
		this.longPosition = value;
		return this;
	}
	
	public Position incPosition(long value){
		this.longPosition += value;
		return this;
	}
	
	public Position decPosition(long value){
		this.longPosition -= value;
		return this;
	}

	public boolean okToBuy(long value){
		return true;
	}
	
	public boolean okToSell(long value){
		return this.longPosition >= value;
	}
	
	public void clear(){
		this.longPosition = 0;
	}
	
	public boolean isClear(){
		return this.longPosition == 0;
	}
	
	@Override
	public String toString() {
		return "position: " + longPosition;
	}
}
