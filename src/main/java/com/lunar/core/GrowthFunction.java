package com.lunar.core;

import java.time.Duration;
import java.util.function.BiFunction;

public enum GrowthFunction {
	CONSTANT((i, r) -> { return i;}),
	POW_2_0((i, r) -> { return (long) (i * Math.pow(2, r));}),
	POW_1_5((i, r) -> { return (long) (i * Math.pow(1.5, r));});
	
	private BiFunction<Long, Integer, Long> func;
	GrowthFunction(BiFunction<Long, Integer, Long> func){
		this.func = func;
	}
	public long compute(long initialDelay, int retryCount){
		return func.apply(initialDelay, retryCount);
	}
	public long computeInNs(Duration initialDelay, int retryCount){
		return func.apply((long)initialDelay.getNano(), retryCount);
	}
}

