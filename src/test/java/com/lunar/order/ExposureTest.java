package com.lunar.order;

import static org.junit.Assert.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

public class ExposureTest {
	static final Logger LOG = LogManager.getLogger(ExposureTest.class);
	private Exposure exposure;
	
	@Before
	public void setup(){
		exposure = Exposure.of();
	}
	
	@Test
	public void test(){
		assertEquals(0, exposure.purchasingPower());
		exposure.incPurchasingPower(1000);
		assertEquals(1000, exposure.purchasingPower());
		exposure.decPurchasingPower(500);
		assertEquals(500, exposure.purchasingPower());
	}
	
	@Test
	public void testLong(){
		int quantity = 17000000;
		int price = 59000;
		long notional = (long)quantity * price;
		assertTrue(notional > 0);
		LOG.debug("quantity:{}, price:{}, notional:{}", quantity, price, notional);
		assertEquals(0, exposure.purchasingPower());
		exposure.incPurchasingPower(1000);
		assertEquals(1000, exposure.purchasingPower());
		exposure.decPurchasingPower(500);
		assertEquals(500, exposure.purchasingPower());
	}
}
