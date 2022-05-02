package com.lunar.pricing;

import com.lunar.util.LongInterval;

public class ExtrapolatingInterval {
	private int data;
	private long currentBegin;
	private long currentEndExcl;

	ExtrapolatingInterval(){}
	
	int data(){ return data;}
	long currentBegin(){ return currentBegin;}
	long currentEndExcl(){return currentEndExcl;}
	
	void init(int data){
		this.data = data;
		this.currentBegin = LongInterval.NULL_INTERVAL_BEGIN_VALUE;
		this.currentEndExcl = LongInterval.NULL_INTERVAL_END_VALUE;
	}
	
	ExtrapolatingInterval data(int value){ 
		this.data = value;
		return this;
	}
	ExtrapolatingInterval currentBegin(long value){ 
		this.currentBegin = value;
		return this;
	}
	ExtrapolatingInterval currentEndExcl(long value){ 
		this.currentEndExcl = value;
		return this;
	}
}
