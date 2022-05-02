package com.lunar.util;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import com.google.common.math.DoubleMath;

public class AssertUtil {
	private static final long DEFAULT_TIMEOUT_IN_NS = TimeUnit.MILLISECONDS.toNanos(100);
	private static final long WAIT_IN_NS_BETWEEN_TEST = 500_000;

	
	public static void assertTrueWithinPeriod(String message, Supplier<Boolean> supplier){
		assertTrueWithinPeriod(message, supplier, DEFAULT_TIMEOUT_IN_NS);
	}

	public static void assertFalseWithinPeriod(String message, Supplier<Boolean> supplier){
		assertTrueWithinPeriod(message, () -> { return !supplier.get(); }, DEFAULT_TIMEOUT_IN_NS);
	}

	public static void assertTrueWithinPeriod(String message, Supplier<Boolean> supplier, long timeoutNs){
		long expiry = System.nanoTime() + timeoutNs;
		while (!supplier.get()){
			if (System.nanoTime() > expiry){
				assertTrue(message, false);
				return;
			}
			LockSupport.parkNanos(WAIT_IN_NS_BETWEEN_TEST);
		}
	}
	
	public static void assertTrueWithinPeriod(String message, Supplier<Boolean> supplier, long timeoutNs, long waitBetweenTestNs){
		long expiry = System.nanoTime() + timeoutNs;
		while (!supplier.get()){
			if (System.nanoTime() > expiry){
				assertTrue(message, false);
				return;
			}
			LockSupport.parkNanos(waitBetweenTestNs);
		}
	}
	
	public static void assertDouble(double expected, double actual, String item){
		if (Double.isNaN(expected)){
			if (Double.isNaN(actual)){
				return;
			}
			assertTrue(item + ": expected NaN, but is: " + actual + " instead", false);
			return;
		}
		if (Double.isNaN(actual)){
			assertTrue(item + ": expected: " + expected + ", but is NaN instead", false);
			return;
		}
		assertTrue(item + ": expected: " + expected + " but is: " + actual + " instead", DoubleMath.fuzzyEquals(expected, actual, 0.00001));
	}

}
