package com.lunar.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.lunar.service.ServiceConstant;

public class BitUtilTest {
	@Test
	public void testMax(){
		int value1 = ServiceConstant.NULL_CHART_PRICE;
		int value2 = 200;
		assertEquals(value2, BitUtil.max(value1, value2));
	}
	
	@Test
	public void testIsLessThanSubAndShift(){
		int a = 10;
		int b = 11;
		assertEquals(0, BitUtil.isGreaterThanSubAndShift(a, b));

		a = 10;
		b = 1;
		assertEquals(1, BitUtil.isGreaterThanSubAndShift(a, b));
	}
	@Test
	public void testXor(){
		int a = 100;
		int b = 100;
		assertEquals(0, a ^ b);
	}
	
	@Test
	public void testAnd(){
		int a = 100;
		int b = 100;
		int deletedTop = (a == b) ? 0 : -1;
		int result = 200;
		assertEquals(0, result & deletedTop);
		
		int c = 100;
		int d = 101;
		assertEquals(200, ((c == d) ? 0 : -1) & 200);
	}

	@Test
	public void testIntCompare(){
		int a = 100;
		int b = 101;
		assertEquals(-1, BitUtil.compare(a, b));
		
		b = 100;
		assertEquals(0, BitUtil.compare(a, b));

		b = 99;
		assertEquals(1, BitUtil.compare(a, b));

		b = 90;
		assertEquals(1, BitUtil.compare(a, b));

		b = 120;
		assertEquals(-1, BitUtil.compare(a, b));
	}
}
