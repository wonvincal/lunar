package com.lunar.util;

public class ConcurrentUtil {
	public static void sleep(long ms){
		try {
			Thread.sleep(ms);
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
