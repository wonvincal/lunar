package com.lunar.util;

public class MathUtil {
	private static double BPS_FACTOR = 0.0001;
	
	public static double bps(double value){
		return value * BPS_FACTOR; 
	}
	
	public static int max(int...values){
		if (values.length <= 0){
			throw new IllegalArgumentException("At least one input is required");
		}
		int max = values[0];
		for (int i = 1; i < values.length; i++){
			if (max < values[i]){
				max = values[i];
			}
		}
		return max;
	}
}
