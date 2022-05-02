package com.lunar.position;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PositionTest {

	@Test
	public void test() {
		PositionTracker p = new PositionTracker(1L);
		assertEquals(1L, p.getPosition().getSecSid());		
		
		p.handleOrder(1, 10, 500);
		assertEquals(5000, p.getPosition().getOutstandingBuyNotional(), 0.000001);
		
		p.handleOrder(2, 12, 500);
		assertEquals(11000, p.getPosition().getOutstandingBuyNotional(), 0.000001);
		
		p.handleOrder(1, 10, 400);
		p.handleTrade(1, 10, 100);
		assertEquals(10000, p.getPosition().getOutstandingBuyNotional(), 0.000001);
		assertEquals(1000, p.getPosition().getNetTradedNotional(), 0.000001);
		
		p.handleOrder(2, 12, 0);
		assertEquals(4000, p.getPosition().getOutstandingBuyNotional(), 0.000001);
		
		p.handleOrder(3, 10, -200);
		assertEquals(2000, p.getPosition().getOutstandingSellNotional(), 0.000001);
		
		p.handleOrder(3, 10, 0);
		p.handleTrade(2, 10, -200);
		assertEquals(0, p.getPosition().getOutstandingSellNotional(), 0.000001);
		assertEquals(-1000, p.getPosition().getNetTradedNotional(), 0.000001);
		
		p.handleOrder(1, 10, 0);
		p.handleTrade(3, 10, 400);
		assertEquals(0, p.getPosition().getOutstandingBuyNotional(), 0.000001);
		assertEquals(3000, p.getPosition().getNetTradedNotional(), 0.000001);
	}
}

