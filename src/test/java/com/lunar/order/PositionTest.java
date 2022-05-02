package com.lunar.order;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class PositionTest {
	private Position position;
	
	@Before
	public void setup(){
		position = Position.of();
	}
	
	@Test
	public void test(){
		position.longPosition(1000);
		
		assertFalse(position.okToSell(1001));
		assertTrue(position.okToSell(1000));
		assertTrue(position.okToSell(999));
	}
}
