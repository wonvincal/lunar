package com.lunar.order;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Exposure {
	private static final Logger LOG = LogManager.getLogger(Exposure.class);
	/**
	 * Purchasing power in dollar
	 */
	private long initial;
	private long change;
	private long total;
	public static Exposure of(){
		return new Exposure(0);
	}
	Exposure(long initial){
		this.initial = initial;
		this.change = 0;
		this.total = this.initial + this.change;
	}
	
	public long initialPurchasingPower(){
		return initial;
	}
	
	public long purchasingPower(){
		return total;
	}
	
	public Exposure initialPurchasingPower(long value){
		this.initial = value;
		this.total = this.initial + this.change;
		return this;
	}

	public Exposure incPurchasingPower(long value){
		this.change += value;
		this.total = this.initial + this.change;
//		LOG.debug("Purchasing power updated [change:{}, now:{}]", value, total);
		return this;
	}

	public Exposure decPurchasingPower(long value){
		this.change -= value;
		this.total = this.initial + this.change;
//		LOG.debug("Purchasing power updated [change:{}, now:{}]", value, total);
		return this;
	}

	public boolean okToBuy(long notional){
		return (total) >= notional;
	}
	
	public boolean okToSell(long notional){
		return true;
	}

	public void clear(){
		this.change = 0;
		LOG.debug("Purchasing power updated [now:0]");
	}
	
	@Override
	public String toString() {
		return "purchasingPower: initial:" + initial + ", change:" + change + ", total:" + total;
	}
}
