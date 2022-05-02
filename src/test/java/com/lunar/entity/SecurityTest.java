package com.lunar.entity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SecurityTest {
	@Test
	public void testBuildSid(){
		byte exchangeSid = 7;
		int exchangeSpecSecSid = 255;
		long secSid = Security.buildSid(exchangeSid, exchangeSpecSecSid);
		assertEquals(29, Long.numberOfLeadingZeros(secSid));
		assertEquals(11, Long.bitCount(secSid));
		assertEquals(exchangeSid, Security.getExchangeId(secSid));
		assertEquals(exchangeSpecSecSid, Security.getExchangeSpecificId(secSid));
	}
}
