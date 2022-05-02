package com.lunar.marketdata.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.marketdata.archive.SingleSideMarketOrderBook;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.order.Tick;

public class SingleSidedMarketOrderBookTest {
	private static final Logger LOG = LogManager.getLogger(SingleSidedMarketOrderBookTest.class);
	private static SpreadTable spreadTable = SpreadTableBuilder.get(SecurityType.WARRANT);
	final int bookDepth = 10;
	final int nullTickLevel = -1;
	final int nullPrice = Integer.MIN_VALUE;
	
	@Before
	public void setup(){
		
	}
	
	@Test
	public void testCreate(){
		SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice);
		SingleSideMarketOrderBook.forAsk(bookDepth, spreadTable, nullTickLevel, nullPrice);
		SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice);
		new BidMarketOrderBookWithNoDeleteProxy(bookDepth, spreadTable, nullTickLevel, nullPrice);
		new BidMarketOrderBookWithBranchDelete(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

	@Test
	public void givenOneOutstandingWhenUpdateToSameLevelThenOK(){
		givenOneOutstandingWhenUpdateToSameLevelThenOK("ask", SingleSideMarketOrderBook.forAsk(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenUpdateToSameLevelThenOK("bid", SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenUpdateToSameLevelThenOK("bid", SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenUpdateToSameLevelThenOK("bidWithNoProxy", new BidMarketOrderBookWithNoDeleteProxy(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenUpdateToSameLevelThenOK("bidWithBranchDelete", new BidMarketOrderBookWithBranchDelete(bookDepth, spreadTable, nullTickLevel, nullPrice));
	}
	

	private void givenOneOutstandingWhenUpdateToSameLevelThenOK(String message, SingleSideMarketOrderBook ob){
		// given
		int price = 100;
		int tickLevel = spreadTable.priceToTick(price);
		int priceLevel = 1;
		int numOrder = 1;
		long qty = 1000;
		
		ob.create(tickLevel, price, priceLevel, qty, numOrder);
		assertTrue(compare(ob, nullTickLevel, 
				Tick.of(tickLevel, price, qty, numOrder)));
		
		assertFalse(ob.isEmpty());
		int bestTickLevel = ob.bestOrNullIfEmpty().tickLevel();
		
		// when
		ob.update(tickLevel, price, priceLevel, qty * 3, numOrder + 1);

		// then
		assertTrue(message, compare(ob, nullTickLevel, 
				Tick.of(tickLevel, price, qty * 3, numOrder + 1)));
		assertEquals(bestTickLevel, ob.bestOrNullIfEmpty().tickLevel());
	}
	
	@Test
	public void givenOneOutstandingWhenDeleteTheSameLevelThenOK(){
		givenOneOutstandingWhenDeleteTheSameLevelThenOK("ask", SingleSideMarketOrderBook.forAsk(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenDeleteTheSameLevelThenOK("bid", SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenDeleteTheSameLevelThenOK("bidWithNoProxy", new BidMarketOrderBookWithNoDeleteProxy(bookDepth, spreadTable, nullTickLevel, nullPrice));
		givenOneOutstandingWhenDeleteTheSameLevelThenOK("priceLevelBased", SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice));
	}
	
	private void givenOneOutstandingWhenDeleteTheSameLevelThenOK(String message, SingleSideMarketOrderBook ob){
		// given
		int price = 100;
		int tickLevel = spreadTable.priceToTick(price);
		int priceLevel = 1;
		int numOrder = 1;
		long qty = 1000;
		
		ob.create(tickLevel, price, priceLevel, qty, numOrder);
		assertTrue(compare(ob, nullTickLevel, 
				Tick.of(tickLevel, price, qty, numOrder)));
		
		assertFalse(ob.isEmpty());

		// when
		ob.delete(tickLevel, price, priceLevel);

		// then
		assertTrue(message, compare(ob, nullTickLevel, new Tick[0]));
		
		assertTrue(ob.isEmpty());
		assertEquals(null, ob.bestOrNullIfEmpty());
	}

	@Test
	public void givenEmptyAskBookWhenUpdateThenOK(){
		givenEmptyAskBookWhenUpdateThenOK("ask", SingleSideMarketOrderBook.forAsk(bookDepth, spreadTable, nullTickLevel, nullPrice));
//		givenEmptyAskBookWhenUpdateThenOK("priceLevelBased", SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice));
	}

	private void givenEmptyAskBookWhenUpdateThenOK(String message, SingleSideMarketOrderBook ob){
		int price = 100;
		int tickLevel = spreadTable.priceToTick(price);
		int priceLevel = 1;
		int numOrder = 1;
		long qty = 1000;
		
		assertTrue(ob.isEmpty());
		ob.create(tickLevel, price, priceLevel, qty, numOrder);
		assertTrue(compare(ob, nullTickLevel, 
				Tick.of(tickLevel, price, qty, numOrder)));

		ob.create(tickLevel - 1, price, priceLevel, qty * 5, numOrder);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 1, price, qty * 5, numOrder),
				Tick.of(tickLevel, price, qty, numOrder)));
		
		ob.create(tickLevel - 5, price, priceLevel, qty * 5, numOrder);

		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 5, price, qty * 5, numOrder),
				Tick.of(tickLevel - 1, price, qty * 5, numOrder),
				Tick.of(tickLevel, price, qty, numOrder)));

		ob.create(tickLevel - 8, price, priceLevel, qty * 5, numOrder);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 8, price, qty * 5, numOrder),
				Tick.of(tickLevel - 5, price, qty * 5, numOrder),
				Tick.of(tickLevel - 1, price, qty * 5, numOrder),
				Tick.of(tickLevel, price, qty, numOrder)));

		ob.update(tickLevel - 1, price, priceLevel, qty * 6, numOrder + 1);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 8, price, qty * 5, numOrder),
				Tick.of(tickLevel - 5, price, qty * 5, numOrder),
				Tick.of(tickLevel - 1, price, qty * 6, numOrder + 1),
				Tick.of(tickLevel, price, qty, numOrder)));

		// order outside of 10 ticks from the best gets deleted automatically
		ob.create(tickLevel - 10, price, priceLevel, qty * 4, numOrder + 1);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 10, price, qty * 4, numOrder + 1),
				Tick.of(tickLevel - 8, price, qty * 5, numOrder),
				Tick.of(tickLevel - 5, price, qty * 5, numOrder),
				Tick.of(tickLevel - 1, price, qty * 6, numOrder + 1)));

		ob.delete(tickLevel, price, priceLevel); // noop
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 10, price, qty * 4, numOrder + 1),
				Tick.of(tickLevel - 8, price, qty * 5, numOrder),
				Tick.of(tickLevel - 5, price, qty * 5, numOrder),
				Tick.of(tickLevel - 1, price, qty * 6, numOrder + 1)));

		ob.delete(tickLevel - 1, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 10, price, qty * 4, numOrder + 1),
				Tick.of(tickLevel - 8, price, qty * 5, numOrder),
				Tick.of(tickLevel - 5, price, qty * 5, numOrder)));

		ob.delete(tickLevel - 10, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 8, price, qty * 5, numOrder),
				Tick.of(tickLevel - 5, price, qty * 5, numOrder)));

		ob.delete(tickLevel - 8, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel - 5, price, qty * 5, numOrder)));

		assertFalse(ob.isEmpty());
		assertEquals(ob.bestOrNullIfEmpty(), Tick.of(tickLevel - 5, price, qty * 5, numOrder));
		ob.delete(tickLevel - 5, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel, new Tick[0]));
		
		assertTrue(ob.isEmpty());

	}

	@Test
	public void givenEmptyBidBookWhenUpdateThenOK(){
		SingleSideMarketOrderBook ob = SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice);
		int price = 100;
		int tickLevel = spreadTable.priceToTick(price);
		int priceLevel = 2;
		int numOrder = 1;
		long qty = 1000;

		assertTrue(ob.isEmpty());

		ob.create(tickLevel, price, priceLevel, qty, numOrder);
		assertTrue(compare(ob, nullTickLevel, 
				Tick.of(tickLevel, price, qty, numOrder)));
		
		ob.create(tickLevel + 1, price, priceLevel, qty * 5, numOrder);
		assertTrue(compare(ob, nullTickLevel, 
				Tick.of(tickLevel + 1, price, qty * 5, numOrder),
				Tick.of(tickLevel, price, qty, numOrder)));

		ob.create(tickLevel + 5, price, priceLevel, qty * 5, numOrder);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 5, price, qty * 5, numOrder),
				Tick.of(tickLevel + 1, price, qty * 5, numOrder),
				Tick.of(tickLevel, price, qty, numOrder)));

		ob.create(tickLevel + 8, price, priceLevel, qty * 5, numOrder);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 8, price, qty * 5, numOrder),
				Tick.of(tickLevel + 5, price, qty * 5, numOrder),
				Tick.of(tickLevel + 1, price, qty * 5, numOrder),
				Tick.of(tickLevel, price, qty, numOrder)));
		
		ob.create(tickLevel + 10, price, priceLevel, qty * 4, numOrder + 1);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 10, price, qty * 4, numOrder + 1),
				Tick.of(tickLevel + 8, price, qty * 5, numOrder),
				Tick.of(tickLevel + 5, price, qty * 5, numOrder),
				Tick.of(tickLevel + 1, price, qty * 5, numOrder)));
		
		ob.update(tickLevel + 10, price, priceLevel, qty * 5, numOrder + 2);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 10, price, qty * 5, numOrder + 2),
				Tick.of(tickLevel + 8, price, qty * 5, numOrder),
				Tick.of(tickLevel + 5, price, qty * 5, numOrder),
				Tick.of(tickLevel + 1, price, qty * 5, numOrder)));

		ob.delete(tickLevel, price, priceLevel); // noop
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 10, price, qty * 5, numOrder + 2),
				Tick.of(tickLevel + 8, price, qty * 5, numOrder),
				Tick.of(tickLevel + 5, price, qty * 5, numOrder),
				Tick.of(tickLevel + 1, price, qty * 5, numOrder)));

		ob.delete(tickLevel + 1, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 10, price, qty * 5, numOrder + 2),
				Tick.of(tickLevel + 8, price, qty * 5, numOrder),
				Tick.of(tickLevel + 5, price, qty * 5, numOrder)));
		
		ob.delete(tickLevel + 10, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 8, price, qty * 5, numOrder),
				Tick.of(tickLevel + 5, price, qty * 5, numOrder)));

		ob.delete(tickLevel + 8, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(tickLevel + 5, price, qty * 5, numOrder)));

		assertFalse(ob.isEmpty());
		assertEquals(ob.bestOrNullIfEmpty(), Tick.of(tickLevel + 5, price, qty * 5, numOrder));
		ob.delete(tickLevel + 5, price, priceLevel);
		assertTrue(compare(ob, nullTickLevel, new Tick[0]));
		
		assertTrue(ob.isEmpty());
	}

	@Test
	public void testSpecForBid(){
		testSpecForBid(SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice));
		testSpecForBid(SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice));
	}


	private void testSpecForBid(SingleSideMarketOrderBook ob){
		ob.create(spreadTable.priceToTick(9730), 9730, 1, 700, 1);
		ob.create(spreadTable.priceToTick(9720), 9720, 2, 350, 1);
		ob.create(spreadTable.priceToTick(9710), 9710, 3, 150, 1);
		ob.create(spreadTable.priceToTick(9700), 9700, 4, 250, 1);
		ob.create(spreadTable.priceToTick(9690), 9690, 5, 100, 1);
		ob.create(spreadTable.priceToTick(9680), 9680, 6, 150, 1);
		ob.create(spreadTable.priceToTick(9670), 9670, 7, 50, 1);
		ob.create(spreadTable.priceToTick(9660), 9660, 8, 200, 1);
		ob.create(spreadTable.priceToTick(9650), 9650, 9, 100, 1);
		
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9730), 9730, 1, 700, 1),
				Tick.of(spreadTable.priceToTick(9720), 9720, 2, 350, 1),
				Tick.of(spreadTable.priceToTick(9710), 9710, 3, 150, 1),
				Tick.of(spreadTable.priceToTick(9700), 9700, 4, 250, 1),
				Tick.of(spreadTable.priceToTick(9690), 9690, 5, 100, 1),
				Tick.of(spreadTable.priceToTick(9680), 9680, 6, 150, 1),
				Tick.of(spreadTable.priceToTick(9670), 9670, 7, 50, 1),
				Tick.of(spreadTable.priceToTick(9660), 9660, 8, 200, 1),
				Tick.of(spreadTable.priceToTick(9650), 9650, 9, 100, 1)));
		
		ob.create(spreadTable.priceToTick(9740), 9740, 1, 50, 1);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9740), 9740, 1, 50, 1),
				Tick.of(spreadTable.priceToTick(9730), 9730, 2, 700, 1),
				Tick.of(spreadTable.priceToTick(9720), 9720, 3, 350, 1),
				Tick.of(spreadTable.priceToTick(9710), 9710, 4, 150, 1),
				Tick.of(spreadTable.priceToTick(9700), 9700, 5, 250, 1),
				Tick.of(spreadTable.priceToTick(9690), 9690, 6, 100, 1),
				Tick.of(spreadTable.priceToTick(9680), 9680, 7, 150, 1),
				Tick.of(spreadTable.priceToTick(9670), 9670, 8, 50, 1),
				Tick.of(spreadTable.priceToTick(9660), 9660, 9, 200, 1),
				Tick.of(spreadTable.priceToTick(9650), 9650, 10, 100, 1)));

		ob.create(spreadTable.priceToTick(9750), 9750, 1, 250, 1);
		ob.update(spreadTable.priceToTick(9660), 9660, 10, 150, 1);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9750), 9750, 1, 250, 1),
				Tick.of(spreadTable.priceToTick(9740), 9740, 2, 50, 1),
				Tick.of(spreadTable.priceToTick(9730), 9730, 3, 700, 1),
				Tick.of(spreadTable.priceToTick(9720), 9720, 4, 350, 1),
				Tick.of(spreadTable.priceToTick(9710), 9710, 5, 150, 1),
				Tick.of(spreadTable.priceToTick(9700), 9700, 6, 250, 1),
				Tick.of(spreadTable.priceToTick(9690), 9690, 7, 100, 1),
				Tick.of(spreadTable.priceToTick(9680), 9680, 8, 150, 1),
				Tick.of(spreadTable.priceToTick(9670), 9670, 9, 50, 1),
				Tick.of(spreadTable.priceToTick(9660), 9660, 10, 150, 1)));
		
		ob.delete(spreadTable.priceToTick(9750), 9750, 1);
		ob.create(spreadTable.priceToTick(9650), 9650, 10, 100, 1);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9740), 9740, 1, 50, 1),
				Tick.of(spreadTable.priceToTick(9730), 9730, 2, 700, 1),
				Tick.of(spreadTable.priceToTick(9720), 9720, 3, 350, 1),
				Tick.of(spreadTable.priceToTick(9710), 9710, 4, 150, 1),
				Tick.of(spreadTable.priceToTick(9700), 9700, 5, 250, 1),
				Tick.of(spreadTable.priceToTick(9690), 9690, 6, 100, 1),
				Tick.of(spreadTable.priceToTick(9680), 9680, 7, 150, 1),
				Tick.of(spreadTable.priceToTick(9670), 9670, 8, 50, 1),
				Tick.of(spreadTable.priceToTick(9660), 9660, 9, 150, 1),
				Tick.of(spreadTable.priceToTick(9650), 9650, 10, 100, 1)
				));
		
		ob.clear();
		assertTrue(compare(ob, nullTickLevel, new Tick[0]));
	}

	@Test
	public void testSpecForBidExplicitAndImplcitDeletions(){
		testSpecForBidExplicitAndImplcitDeletions(SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice));
		testSpecForBidExplicitAndImplcitDeletions(SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice));
	}

	private void testSpecForBidExplicitAndImplcitDeletions(SingleSideMarketOrderBook ob){
		ob.create(spreadTable.priceToTick(9800), 9800, 1, 700, 1);
		ob.create(spreadTable.priceToTick(9790), 9790, 2, 350, 1);
		ob.create(spreadTable.priceToTick(9780), 9780, 3, 150, 1);
		ob.create(spreadTable.priceToTick(9760), 9760, 4, 250, 1);
		ob.create(spreadTable.priceToTick(9750), 9750, 5, 100, 1);
		ob.create(spreadTable.priceToTick(9730), 9730, 6, 400, 1);
		ob.create(spreadTable.priceToTick(9720), 9720, 7, 200, 1);
		ob.create(spreadTable.priceToTick(9710), 9710, 8, 300, 1);
		
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9800), 9800, 1, 700, 1),
				Tick.of(spreadTable.priceToTick(9790), 9790, 2, 350, 1),
				Tick.of(spreadTable.priceToTick(9780), 9780, 3, 150, 1),
				Tick.of(spreadTable.priceToTick(9760), 9760, 4, 250, 1),
				Tick.of(spreadTable.priceToTick(9750), 9750, 5, 100, 1),
				Tick.of(spreadTable.priceToTick(9730), 9730, 6, 400, 1),
				Tick.of(spreadTable.priceToTick(9720), 9720, 7, 200, 1),
				Tick.of(spreadTable.priceToTick(9710), 9710, 8, 300, 1)));

		ob.create(spreadTable.priceToTick(9860), 9860, 1, 450, 1);
		ob.create(spreadTable.priceToTick(9850), 9850, 2, 550, 1);
		ob.create(spreadTable.priceToTick(9840), 9840, 3, 650, 1);
		ob.delete(spreadTable.priceToTick(9760), 9760, 7);
		ob.delete(spreadTable.priceToTick(9750), 9750, 7);
		ob.delete(spreadTable.priceToTick(9730), 9730, 7);
		ob.delete(spreadTable.priceToTick(9720), 9720, 7);

		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9860), 9860, 1, 450, 1),
				Tick.of(spreadTable.priceToTick(9850), 9850, 2, 550, 1),
				Tick.of(spreadTable.priceToTick(9840), 9840, 3, 650, 1),
				Tick.of(spreadTable.priceToTick(9800), 9800, 4, 700, 1),
				Tick.of(spreadTable.priceToTick(9790), 9790, 5, 350, 1),
				Tick.of(spreadTable.priceToTick(9780), 9780, 6, 150, 1)
				));
	}

	@Test
	public void testSpecForAsk(){
		testSpecForAsk(SingleSideMarketOrderBook.forAsk(bookDepth, spreadTable, nullTickLevel, nullPrice));
		testSpecForAsk(SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice));
	}

	private void testSpecForAsk(SingleSideMarketOrderBook ob){
		ob.create(spreadTable.priceToTick(9760), 9760, 1, 500, 1);
		ob.create(spreadTable.priceToTick(9770), 9770, 2, 300, 1);
		ob.create(spreadTable.priceToTick(9780), 9780, 3, 100, 1);
		ob.create(spreadTable.priceToTick(9790), 9790, 4, 150, 1);
		
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9760), 9760, 1, 500, 1),
				Tick.of(spreadTable.priceToTick(9770), 9770, 2, 300, 1),
				Tick.of(spreadTable.priceToTick(9780), 9780, 3, 100, 1),
				Tick.of(spreadTable.priceToTick(9790), 9790, 4, 150, 1)));
		
		ob.update(spreadTable.priceToTick(9770), 9770, 2, 200, 1);
		ob.create(spreadTable.priceToTick(9850), 9850, 5, 300, 1);

		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9760), 9760, 1, 500, 1),
				Tick.of(spreadTable.priceToTick(9770), 9770, 2, 200, 1),
				Tick.of(spreadTable.priceToTick(9780), 9780, 3, 100, 1),
				Tick.of(spreadTable.priceToTick(9790), 9790, 4, 150, 1),
				Tick.of(spreadTable.priceToTick(9850), 9850, 5, 300, 1)
				));

		ob.create(spreadTable.priceToTick(9750), 9750, 1, 300, 1);
		ob.delete(spreadTable.priceToTick(9850), 9850, 6);
		assertTrue(compare(ob, nullTickLevel,
				Tick.of(spreadTable.priceToTick(9750), 9750, 1, 300, 1),
				Tick.of(spreadTable.priceToTick(9760), 9760, 2, 500, 1),
				Tick.of(spreadTable.priceToTick(9770), 9770, 3, 200, 1),
				Tick.of(spreadTable.priceToTick(9780), 9780, 4, 100, 1),
				Tick.of(spreadTable.priceToTick(9790), 9790, 5, 150, 1)
				));
		
		ob.clear();
		assertTrue(compare(ob, nullTickLevel, new Tick[0]));
	}

	public static void dump(SingleSideMarketOrderBook ob){
		Iterator<Tick> it = ob.iteratorForWhole();
		while (it.hasNext()){
			LOG.debug("{}", it.next());
		}
	}
	
	public static Tick nextNonNullTick(Iterator<Tick> it, int nullTickLevel){
		while (it.hasNext()){
			Tick tick = it.next();
			if (tick.tickLevel() != nullTickLevel){
				return tick;
			}
		}
		return null;
	}
	
	public static boolean compare(SingleSideMarketOrderBook ob, int nullTickLevel, Tick... ticks){
		Iterator<Tick> iterator = ob.iteratorForWhole();

		// if there is no tick to check, we should check if the order book is empty
		if (ticks == null || ticks.length <= 0){
			while (iterator.hasNext()){
				Tick tick = iterator.next();
				assertFalse("found unexpected non-empty tick: " + tick.toString(), tick.numOrders() != 0 || tick.tickLevel() != nullTickLevel);
			}
			return true;
		}
			
		// first tick is the top of book
		// make sure we can match the first tick,
		// then we will advance to next non-null tick and check again
		// keep search the whole book to make sure there is nothing unexpected
		int index = 0;
		Tick tick = null;
		while ((tick = nextNonNullTick(iterator, nullTickLevel)) != null){
			// LOG.debug("index:{}, tick:{}", index, tick);
			assertFalse("tick is avaiable in the order book that is not covered by your input: " + tick.toString(), index >= ticks.length);
			assertTrue("found tick that cannot match your input:\nfound: " + tick.toString() + "\nexpected: " + ticks[index].toString(),tick.equals(ticks[index]));
			index++;
		}
		assertTrue("found tick(s) that is(are) in your input but not in the order book: starting from the [" + index + "] input", index == ticks.length);
		return true;
	}
}
