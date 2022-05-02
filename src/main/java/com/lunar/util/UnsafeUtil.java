package com.lunar.util;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class UnsafeUtil {
	public static Unsafe getUnsafe() {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (Unsafe)f.get(null);
		} catch (Exception e) { 
			throw new RuntimeException("cannot get unsafe object");
		}
	}
}
