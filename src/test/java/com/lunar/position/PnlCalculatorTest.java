package com.lunar.position;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.lunar.util.AssertUtil;

public class PnlCalculatorTest {
	private static AtomicInteger seq = new AtomicInteger();
	PnlCalculator item;
	
	@Before
	public void setup(){
		item = new PnlCalculator();
	}
	
	@After
	public void cleanup(){
		item.clear();
	}
	
	@Test
	public void testCreate(){
		PnlCalculator item = new PnlCalculator();
		assertNotNull(item);
		assertFalse(item.isMtmBuyPriceValid());
		assertFalse(item.isMtmSellPriceValid());
		AssertUtil.assertDouble(0, item.mtmBuyPrice(), "mtmBuyPrice");
		AssertUtil.assertDouble(0, item.mtmSellPrice(), "mtmSellPrice");
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		assertEquals(0, item.realizedBuyTrades().size());
		assertEquals(0, item.realizedSellTrades().size());
		
	}
	
	@Test
	public void givenEmptyWhenAddTradeThenOK(){
		item.addBuyTrade(seq.getAndIncrement(), 10, 100);
		assertNotNull(item.partialMatchedTrade());
		assertEquals(100, item.partialMatchedOutstandingQty());
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		assertEquals(0, item.realizedBuyTrades().size());
		assertEquals(0, item.realizedSellTrades().size());
		assertEquals(0, item.unrealizedTrades().size());
	}

	@Test
	public void givenTradeWhenReceivePriceThenUnrealizedUpdate(){
		// Given
		item.addBuyTrade(seq.getAndIncrement(), 10, 100);
		
		// When
		double bid = 11;
		double ask = 12;
		item.bidAsk(bid, ask);
		
		// Then
		AssertUtil.assertDouble(100, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		// When
		bid = 12;
		ask = 13;
		item.bidAsk(bid, ask);
		
		// Then
		AssertUtil.assertDouble(200, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		// When
		bid = 0;
		ask = 13;
		item.bidAsk(bid, ask);
		
		// Then
		AssertUtil.assertDouble(200, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		assertFalse(item.isMtmBuyPriceValid());
		assertTrue(item.isMtmSellPriceValid());
	}
	
	@Test
	public void givenTradeWhenAnotherTradeOfSameSideThenUnrealizedUpdate(){
		// Given
		item.addBuyTrade(seq.getAndIncrement(), 11, 100);
		double bid = 11;
		double ask = 12;
		item.bidAsk(bid, ask);
		
		// When
		item.addBuyTrade(seq.getAndIncrement(), 12, 100);
		
		// Then
		AssertUtil.assertDouble(-100, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		assertTrue(item.isMtmBuyPriceValid());
		assertTrue(item.isMtmSellPriceValid());
		assertEquals(1, item.unrealizedTrades().size());
		assertEquals(100, item.partialMatchedOutstandingQty());
		assertNotNull(item.partialMatchedTrade());
	}
	
	@Test
	public void givenBuyTradeWhenSellTradeAndFullMatchThenRealizedUpdateAndUnrealizedZero(){
		// Given
		item.addBuyTrade(seq.getAndIncrement(), 12, 100);
		double bid = 14;
		double ask = 15;
		item.bidAsk(bid, ask);

		// When
		item.addSellTrade(seq.getAndIncrement(), 14, 100);
		
		// Then
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(200, item.realizedPnl(), "realizedPnl");
		assertTrue(item.isMtmBuyPriceValid());
		assertTrue(item.isMtmSellPriceValid());
		assertEquals(0, item.unrealizedTrades().size());
		assertEquals(0, item.partialMatchedOutstandingQty());
		assertNotNull(item.partialMatchedTrade());
	}
	
	@Test
	public void givenBuyTradeWhenSellTradeAndPartialMatchThenAllPnlsUpdated(){
		// Given
		item.addBuyTrade(seq.getAndIncrement(), 12, 100);
		double bid = 15;
		double ask = 16;
		item.bidAsk(bid, ask);

		// When
		item.addSellTrade(seq.getAndIncrement(), 15, 30);
		
		// Then
		AssertUtil.assertDouble(210, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(90, item.realizedPnl(), "realizedPnl");
		assertTrue(item.isMtmBuyPriceValid());
		assertTrue(item.isMtmSellPriceValid());
		assertEquals(0, item.unrealizedTrades().size());
		assertEquals(70, item.partialMatchedOutstandingQty());
		assertNotNull(item.partialMatchedTrade());
	}
	
	@Test
	public void givenBuyTradeWhenSellTradeWithQuantityLargerThanBuyThenAllPnlsUpdate(){
		// Given
		item.addBuyTrade(seq.getAndIncrement(), 12, 100);
		double bid = 15;
		double ask = 16;
		item.bidAsk(bid, ask);
		
		// When
		item.addSellTrade(seq.getAndIncrement(), 15, 130);
		
		// Then
		AssertUtil.assertDouble(-30, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(300, item.realizedPnl(), "realizedPnl");
		assertTrue(item.isMtmBuyPriceValid());
		assertTrue(item.isMtmSellPriceValid());
		assertEquals(0, item.unrealizedTrades().size());
		assertEquals(30, item.partialMatchedOutstandingQty());
		assertNotNull(item.partialMatchedTrade());
		
		// When
		item.addBuyTrade(seq.getAndIncrement(), 18, 60);

		// Then
		AssertUtil.assertDouble(-90, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(210, item.realizedPnl(), "realizedPnl");
		assertEquals(30, item.partialMatchedOutstandingQty());
		
		// When
//		item.addTrade(createTrade(Side.SELL, 30, 16000));
		item.addSellTrade(seq.getAndIncrement(), 16, 30);

		// Then
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(150, item.realizedPnl(), "realizedPnl");
		assertEquals(0, item.partialMatchedOutstandingQty());
	}
	
	@Test
	public void givenMultiBuyTradesWhenSellTradeWithPartialMatchThenAllPnlsUpdated(){
		// Given
		item.addBuyTrade(seq.getAndIncrement(), 12, 100);
		item.addBuyTrade(seq.getAndIncrement(), 13, 50);
		item.addBuyTrade(seq.getAndIncrement(), 14, 20);
		
		double bid = 15;
		double ask = 16;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(420, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		// When
		item.addSellTrade(seq.getAndIncrement(), 15, 130);
		
		// Then
		AssertUtil.assertDouble(60, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(360, item.realizedPnl(), "realizedPnl");
		assertTrue(item.isMtmBuyPriceValid());
		assertTrue(item.isMtmSellPriceValid());
		assertEquals(1, item.unrealizedTrades().size());
		assertEquals(20, item.partialMatchedOutstandingQty());
		assertNotNull(item.partialMatchedTrade());
		
		// When - change in mtm
		bid = 16;
		ask = 17;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(100, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(360, item.realizedPnl(), "realizedPnl");
		assertEquals(20, item.partialMatchedOutstandingQty());
	}
	
	@Test
	public void givenTypicalOrderFlows(){
		// Given
		long quantity = 300_000;
		item.addBuyTrade(seq.getAndIncrement(), 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		item.addSellTrade(seq.getAndIncrement(), 0.215, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-600, item.realizedPnl(), "realizedPnl");
		
		item.addBuyTrade(seq.getAndIncrement(), 0.2, quantity);
		AssertUtil.assertDouble(4500, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-600, item.realizedPnl(), "realizedPnl");

		item.addSellTrade(seq.getAndIncrement(), 0.198, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-1200, item.realizedPnl(), "realizedPnl");

		item.addBuyTrade(seq.getAndIncrement(), 0.201, quantity);
		AssertUtil.assertDouble(4200, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-1200, item.realizedPnl(), "realizedPnl");
		
		item.addSellTrade(seq.getAndIncrement(), 0.204, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-300, item.realizedPnl(), "realizedPnl");

		item.addBuyTrade(seq.getAndIncrement(), 0.205, quantity);
		AssertUtil.assertDouble(3000, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-300, item.realizedPnl(), "realizedPnl");
		
		item.addSellTrade(seq.getAndIncrement(), 0.206, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		item.addBuyTrade(seq.getAndIncrement(), 0.205, quantity);
		AssertUtil.assertDouble(3000, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		item.addSellTrade(seq.getAndIncrement(), 0.207, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(600, item.realizedPnl(), "realizedPnl");
	}
	
	@Test
	public void givenTypicalOrderFlowsWithPriceMovements(){
		// Given
		long quantity = 300_000;
		item.addBuyTrade(seq.getAndIncrement(), 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		bid = 0.216;
		ask = 0.218;
		item.bidAsk(bid, ask);
		item.addSellTrade(seq.getAndIncrement(), 0.215, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-600, item.realizedPnl(), "realizedPnl");
		
		bid = 0.217;
		ask = 0.219;
		item.bidAsk(bid, ask);
		item.addBuyTrade(seq.getAndIncrement(), 0.2, quantity);
		AssertUtil.assertDouble(5100, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-600, item.realizedPnl(), "realizedPnl");

		item.addSellTrade(seq.getAndIncrement(), 0.198, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-1200, item.realizedPnl(), "realizedPnl");

		item.addBuyTrade(seq.getAndIncrement(), 0.201, quantity);
		AssertUtil.assertDouble(4800, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-1200, item.realizedPnl(), "realizedPnl");
		
		bid = 0.216;
		ask = 0.218;
		item.bidAsk(bid, ask);
		item.addSellTrade(seq.getAndIncrement(), 0.204, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-300, item.realizedPnl(), "realizedPnl");

		item.addBuyTrade(seq.getAndIncrement(), 0.205, quantity);
		AssertUtil.assertDouble(3300, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-300, item.realizedPnl(), "realizedPnl");
		
		item.addSellTrade(seq.getAndIncrement(), 0.206, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		item.addBuyTrade(seq.getAndIncrement(), 0.205, quantity);
		AssertUtil.assertDouble(3300, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		bid = 0.215;
		ask = 0.217;
		item.bidAsk(bid, ask);
		item.addSellTrade(seq.getAndIncrement(), 0.207, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(600, item.realizedPnl(), "realizedPnl");
	}

	
	@Test
	public void givenBuyTradeExistWhenRemoveBuyTradeThenPnlZeros(){
		// Given
		long quantity = 300_000;
		int tradeSid = seq.getAndIncrement();
		item.addBuyTrade(tradeSid, 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		item.removeBuyTrade(tradeSid);

		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		assertEquals(0, item.partialMatchedOutstandingQty());
		assertEquals(PartialMatchedTradeInfo.NULL_INSTANCE.trade(), item.partialMatchedTrade());
	}
	
	@Test
	public void givenTwoBuyTradesExistWhenRemoveFirstBuyTradeThenPnlReversed(){
		// Given
		long quantity = 300_000;
		int tradeSid1 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid1, 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		int tradeSid2 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid2, 0.218, quantity);

		AssertUtil.assertDouble(-1500, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		item.removeBuyTrade(tradeSid1);
		AssertUtil.assertDouble(-900, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
	}
	
	@Test
	public void givenTwoBuyTradesExistWhenRemoveSecondBuyTradeThenPnlReversed(){
		// Given
		long quantity = 300_000;
		int tradeSid1 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid1, 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		int tradeSid2 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid2, 0.218, quantity);

		AssertUtil.assertDouble(-1500, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		item.removeBuyTrade(tradeSid2);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
	}
	
	@Test
	public void givenBuyAndSellTradesAndFullyRealizedWhenRemoveBuyThenPnlReversed(){
		// Given
		long quantity = 300_000;
		int tradeSid1 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid1, 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		int tradeSid2 = seq.getAndIncrement();
		item.addSellTrade(tradeSid2, 0.218, quantity);

		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(300, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		
		item.removeBuyTrade(tradeSid1);
		AssertUtil.assertDouble(300, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");		
	}
	
	@Test
	public void givenTwoSetOfBuyAndSellTradesAndFullyRealizedWhenRemoveBuysThenPnlReversed(){
		// Given
		long quantity = 300_000;
		int tradeSid1 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid1, 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		int tradeSid2 = seq.getAndIncrement();
		item.addSellTrade(tradeSid2, 0.218, quantity);

		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(300, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		assertFalse(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());
		
		quantity = 150_000;
		int tradeSid3 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid3, 0.216, quantity);

		AssertUtil.assertDouble(-150, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(300, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		assertTrue(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());

		int tradeSid4 = seq.getAndIncrement();
		item.addSellTrade(tradeSid4, 0.215, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(150, item.realizedPnl(), "realizedPnl");
		assertEquals(2, item.realizedBuyTrades().size());
		assertEquals(2, item.realizedSellTrades().size());
		assertFalse(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());
		
		// Remove first BUY
		item.removeBuyTrade(tradeSid1);
		AssertUtil.assertDouble(300, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-150, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		assertTrue(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());
		
		// Remove all BUY
		item.removeBuyTrade(tradeSid3);
		assertEquals(0, item.realizedBuyTrades().size());
		assertEquals(0, item.realizedSellTrades().size());
		assertTrue(item.hasPartialMatchedTrade());
		assertEquals(1, item.unrealizedTrades().size());
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
	}

	@Test
	public void givenTwoSetOfBuyAndSellTradesAndFullyRealizedWhenRemoveSellsThenPnlReversed(){
		// Given
		long quantity = 300_000;
		int tradeSid1 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid1, 0.217, quantity);

		double bid = 0.215;
		double ask = 0.217;
		item.bidAsk(bid, ask);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		int tradeSid2 = seq.getAndIncrement();
		item.addSellTrade(tradeSid2, 0.218, quantity);

		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(300, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		assertFalse(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());
		
		quantity = 150_000;
		int tradeSid3 = seq.getAndIncrement();
		item.addBuyTrade(tradeSid3, 0.216, quantity);

		AssertUtil.assertDouble(-150, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(300, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		assertTrue(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());

		int tradeSid4 = seq.getAndIncrement();
		item.addSellTrade(tradeSid4, 0.215, quantity);
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(150, item.realizedPnl(), "realizedPnl");
		assertEquals(2, item.realizedBuyTrades().size());
		assertEquals(2, item.realizedSellTrades().size());
		assertFalse(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());
		
		// Remove first SELL
		item.removeSellTrade(tradeSid2);
		AssertUtil.assertDouble(-600, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(-150, item.realizedPnl(), "realizedPnl");
		assertEquals(1, item.realizedBuyTrades().size());
		assertEquals(1, item.realizedSellTrades().size());
		assertTrue(item.hasPartialMatchedTrade());
		assertEquals(0, item.unrealizedTrades().size());
		
		// Remove all SELLs
		item.removeSellTrade(tradeSid4);
		assertEquals(0, item.realizedBuyTrades().size());
		assertEquals(0, item.realizedSellTrades().size());
		assertTrue(item.hasPartialMatchedTrade());
		assertEquals(1, item.unrealizedTrades().size());
		AssertUtil.assertDouble(-750, item.unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
	}
		   
	@Test
	public void givenMultipleBuyBeforeFirstSell(){
	    // Given
	    long quantity = 300_000;
	    item.addBuyTrade(seq.getAndIncrement(), 0.201, quantity);

	    double bid = 0.215;
	    double ask = 0.217;
	    item.bidAsk(bid, ask);
	    AssertUtil.assertDouble(4200, item.unrealizedPnl(), "unrealizedPnl");
	    AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

	    item.addBuyTrade(seq.getAndIncrement(), 0.205, quantity);
	    AssertUtil.assertDouble(7200, item.unrealizedPnl(), "unrealizedPnl");
	    AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

	    item.addSellTrade(seq.getAndIncrement(), 0.204, quantity);
	    AssertUtil.assertDouble(3000, item.unrealizedPnl(), "unrealizedPnl");
	    AssertUtil.assertDouble(900, item.realizedPnl(), "realizedPnl");

	    item.addSellTrade(seq.getAndIncrement(), 0.206, quantity);
	    AssertUtil.assertDouble(0, item.unrealizedPnl(), "unrealizedPnl");
	    AssertUtil.assertDouble(1200, item.realizedPnl(), "realizedPnl");
	}

	private static void addTrades(PnlCalculator item, int tradeSid){
		item.addBuyTrade(tradeSid, 720, 40000);
		item.addSellTrade(tradeSid + 1, 719, 40000);
		item.addBuyTrade(tradeSid + 2, 720, 20000);
		item.addSellTrade(tradeSid + 3, 720, 20000);
		item.addBuyTrade(tradeSid + 4, 720, 20000);
		item.addSellTrade(tradeSid + 5, 720, 20000);
		item.addBuyTrade(tradeSid + 6, 719, 60000);
		item.addSellTrade(tradeSid + 7, 720, 60000);		
//		AssertUtil.assertDouble(20000, item.realizedPnl(), "realizedPnl");
	}
	
	private static void addTradesSet2(PnlCalculator item, int tradeSid){
		item.addBuyTrade(tradeSid, 720, 40000);
		item.addSellTrade(tradeSid + 1, 719, 20000);
		item.addBuyTrade(tradeSid + 2, 720, 3000);
		item.addSellTrade(tradeSid + 3, 715, 20000);
		item.addBuyTrade(tradeSid + 4, 710, 5000);
		item.addSellTrade(tradeSid + 5, 720, 3000);
		item.addBuyTrade(tradeSid + 6, 719, 60000);
		item.addSellTrade(tradeSid + 7, 720, 60000);
		item.addBuyTrade(tradeSid + 8, 719, 7000);
//		AssertUtil.assertDouble(20000, item.realizedPnl(), "realizedPnl");
	}
	
	@Test
	public void givenAddedTradesWhenReverseInAnyOrderThenPnlIsZero(){
		int tradeSid = 1000000;

		addTrades(item, tradeSid);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid + 2);
		item.removeSellTrade(tradeSid + 3);
		item.removeBuyTrade(tradeSid + 4);
		item.removeSellTrade(tradeSid + 5);
		item.removeBuyTrade(tradeSid + 6);
		item.removeSellTrade(tradeSid + 7);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		addTrades(item, tradeSid);
		item.removeBuyTrade(tradeSid + 6);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 7);
		item.removeBuyTrade(tradeSid + 2);
		item.removeSellTrade(tradeSid + 5);
		item.removeSellTrade(tradeSid + 3);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid + 4);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		addTrades(item, tradeSid);
		item.removeSellTrade(tradeSid + 7);
		item.removeBuyTrade(tradeSid + 6);
		item.removeSellTrade(tradeSid + 5);
		item.removeBuyTrade(tradeSid + 4);
		item.removeSellTrade(tradeSid + 3);
		item.removeBuyTrade(tradeSid + 2);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");

		addTrades(item, tradeSid);
		item.removeSellTrade(tradeSid + 1);
		item.removeSellTrade(tradeSid + 7);
		item.removeSellTrade(tradeSid + 3);
		item.removeSellTrade(tradeSid + 5);
		item.removeBuyTrade(tradeSid);
		item.removeBuyTrade(tradeSid + 4);
		item.removeBuyTrade(tradeSid + 6);
		item.removeBuyTrade(tradeSid + 2);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		
		addTrades(item, tradeSid);
		item.removeSellTrade(tradeSid + 7);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid + 6);
		item.removeBuyTrade(tradeSid + 2);
		item.removeBuyTrade(tradeSid + 4);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 5);
		item.removeSellTrade(tradeSid + 3);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
	}
	
	@Test
	public void givenAddedTradesSet2WhenReverseInAnyOrderThenPnlIsZero(){
		int tradeSid = 1000000;

		addTradesSet2(item, tradeSid);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid + 2);
		item.removeSellTrade(tradeSid + 3);
		item.removeBuyTrade(tradeSid + 4);
		item.removeSellTrade(tradeSid + 5);
		item.removeBuyTrade(tradeSid + 6);
		item.removeSellTrade(tradeSid + 7);
		item.removeSellTrade(tradeSid + 8);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "realizedPnl");

		addTradesSet2(item, tradeSid);
		item.removeBuyTrade(tradeSid + 6);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 7);
		item.removeBuyTrade(tradeSid + 2);
		item.removeSellTrade(tradeSid + 5);
		item.removeSellTrade(tradeSid + 3);
		item.removeSellTrade(tradeSid + 8);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid + 4);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "realizedPnl");
		
		addTradesSet2(item, tradeSid);
		item.removeSellTrade(tradeSid + 7);
		item.removeBuyTrade(tradeSid + 6);
		item.removeSellTrade(tradeSid + 5);
		item.removeSellTrade(tradeSid + 8);
		item.removeBuyTrade(tradeSid + 4);
		item.removeSellTrade(tradeSid + 3);
		item.removeBuyTrade(tradeSid + 2);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "realizedPnl");
		
		addTradesSet2(item, tradeSid);
		item.removeSellTrade(tradeSid + 1);
		item.removeSellTrade(tradeSid + 7);
		item.removeSellTrade(tradeSid + 3);
		item.removeSellTrade(tradeSid + 5);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 8);
		item.removeBuyTrade(tradeSid + 4);
		item.removeBuyTrade(tradeSid + 6);
		item.removeBuyTrade(tradeSid + 2);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "realizedPnl");
		
		addTradesSet2(item, tradeSid);
		item.removeSellTrade(tradeSid + 7);
		item.removeSellTrade(tradeSid + 1);
		item.removeBuyTrade(tradeSid + 6);
		item.removeBuyTrade(tradeSid + 2);
		item.removeBuyTrade(tradeSid + 4);
		item.removeBuyTrade(tradeSid);
		item.removeSellTrade(tradeSid + 5);
		item.removeSellTrade(tradeSid + 8);
		item.removeSellTrade(tradeSid + 3);
		AssertUtil.assertDouble(0, item.realizedPnl(), "realizedPnl");
		AssertUtil.assertDouble(0, item.unrealizedPnl(), "realizedPnl");
	}
}
