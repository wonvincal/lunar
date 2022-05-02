package com.lunar.util;

import java.lang.reflect.Field;

import org.agrona.DirectBuffer;

public class AccessUtil {
	public static Field SBE_DIRECT_BUFFER_ADDRESS_OFFSET;
	
	static {
		try {
			SBE_DIRECT_BUFFER_ADDRESS_OFFSET = DirectBuffer.class.getDeclaredField("addressOffset");
			SBE_DIRECT_BUFFER_ADDRESS_OFFSET.setAccessible(true);
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
