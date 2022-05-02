package com.lunar.marketdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.lunar.message.io.sbe.SecurityType;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SpreadTableTest {
	static final Logger LOG = LogManager.getLogger(SpreadTableTest.class);
	
	@Test
	public void testNewTableWithOneLevelAndArrayConverterOnly(){
		byte id = 1;
		String name = "hkex";
		SpreadTable spreadTable = SpreadTable.of(
				id,
				name,
				3,
				15,
				SpreadTableDetails.of(10, 15, 1),
				SpreadTableDetails.of(15, 29, 5));
		assertEquals(8, spreadTable.maxLevel());

		assertEquals(1, spreadTable.priceToTick(10));
		assertEquals(2, spreadTable.priceToTick(11));
		assertEquals(3, spreadTable.priceToTick(12));
		assertEquals(4, spreadTable.priceToTick(13));
		assertEquals(5, spreadTable.priceToTick(14));
		assertEquals(6, spreadTable.priceToTick(15));
		assertEquals(7, spreadTable.priceToTick(20));
		assertEquals(8, spreadTable.priceToTick(25));
		
		assertExpectExceptionForPrice(spreadTable, 16);
		assertExpectExceptionForPrice(spreadTable, 17);
		assertExpectExceptionForPrice(spreadTable, 18);
		assertExpectExceptionForPrice(spreadTable, 19);
		assertExpectExceptionForPrice(spreadTable, 21);
		assertExpectExceptionForPrice(spreadTable, 22);
		assertExpectExceptionForPrice(spreadTable, 23);
		assertExpectExceptionForPrice(spreadTable, 24);
		assertExpectExceptionForPrice(spreadTable, 26);
		assertExpectExceptionForPrice(spreadTable, 27);
		assertExpectExceptionForPrice(spreadTable, 28);
		assertExpectExceptionForPrice(spreadTable, 29);
	}
	
	public static void assertExpectExceptionForPrice(SpreadTable spreadTable, int price){
		try {
			spreadTable.priceToTick(price);
			assertFalse("Expect exception when price = " + price, true);
		}
		catch (IllegalArgumentException ex){}
	}
	
	@Test
	public void testNewTableWithOneLevel(){
		byte id = 1;
		String name = "hkex";
		SpreadTable spreadTable = SpreadTable.of(
				id,
				name,
				3,
				100,
				SpreadTableDetails.of(10, 250, 1));
		assertEquals(SpreadTableTest.expectedNumLevels(10, 250, 1), spreadTable.maxLevel());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void whenFromPriceGreaterThanToPriceThenThrowException(){
		SpreadTableDetails.of(1000, 250, 1);
	}

	@Test(expected=IllegalArgumentException.class)
	public void whenSpreadIsNegativeThenThrowException(){
		SpreadTableDetails.of(1, 250, -1);
	}

	@Test (expected=IllegalArgumentException.class)
	public void whenCreateTableWithTwoSpreadDetailsEntryWithGapThenThrowException(){
		byte id = 1;
		String name = "hkex";
		SpreadTable.of(
				id,
				name,
				3,
				250,
				SpreadTableDetails.of(10, 250, 1),
				SpreadTableDetails.of(260, 1000, 1));
	}
	
	@Test
	public void givenTableCreatedWhenConvertPriceToTickWithinValidRangeThenReturnValidResult(){
		byte id = 1;
		String name = "hkex";
		SpreadTable spreadTable = SpreadTable.of(
				id,
				name,
				3,
				500,
				SpreadTableDetails.of(10, 250, 1), // 10 (1) to 250 (241)
				SpreadTableDetails.of(250, 1000, 5)); // 250 (241)
		
		assertEquals(1, spreadTable.priceToTick(10));
		assertEquals(2, spreadTable.priceToTick(11));
		assertEquals(241, spreadTable.priceToTick(250));
		assertEquals(241, spreadTable.priceToTick(251));
		assertEquals(241, spreadTable.priceToTick(252));
		assertEquals(241, spreadTable.priceToTick(253));
		assertEquals(241, spreadTable.priceToTick(254));
		assertEquals(242, spreadTable.priceToTick(255));
		assertEquals(291, spreadTable.priceToTick(500));
		assertEquals(292, spreadTable.priceToTick(505));
		assertEquals(293, spreadTable.priceToTick(510));
		assertEquals((250 - 10) + (1000 - 250)/5, spreadTable.maxLevel());
		
	}
	
	@Test
	public void givenTableCreatedWhenConvertPriceToTickWithinValidBigRangeThenReturnValidResult(){
		SpreadTable spreadTable = SpreadTableBuilder.get(SecurityType.WARRANT);
		int price = 170000;
		LOG.info("price:{}, tick:{}", price, spreadTable.priceToTick(price));
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void givenTableCreatedWhenConvertPriceToTickOutsideValidRangeThenThrowException(){
		byte id = 1;
		String name = "hkex";
		SpreadTable spreadTable = SpreadTable.of(
				id,
				name,
				3,
				500,
				SpreadTableDetails.of(10, 250, 1),
				SpreadTableDetails.of(250, 1000, 10));
		spreadTable.priceToTick(509);
	}

	@Test
	public void verifyPriceToTickWithTickToPrice(){
		byte id = 1;
		String name = "hkex";
		SpreadTable spreadTable = SpreadTable.of(
				id,
				name,
				3,
				500,
				SpreadTableDetails.of(10, 250, 1), // 10 (1) to 250 (241)
				SpreadTableDetails.of(250, 1000, 5)); // 250 (241)

		for (int i = 1; i < spreadTable.maxLevel(); i++){
			int price = spreadTable.tickToPrice(i);
			int tick = spreadTable.priceToTick(price);
			assertEquals(i, tick);
		}
	}
	
	private static int expectedNumLevels(int from, int to, int spread){
		return (to - from) / spread;
	}
}
