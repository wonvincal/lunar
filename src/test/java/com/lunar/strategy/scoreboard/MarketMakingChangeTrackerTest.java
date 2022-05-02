package com.lunar.strategy.scoreboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.UserControlledTimerService;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class MarketMakingChangeTrackerTest {
	static final Logger LOG = LogManager.getLogger(MarketMakingChangeTrackerTest.class);
	private static ScoreBoardSecurityInfo SECURITY;
	private static SpreadTable SPREADTABLE;
	private static int BOOK_DEPTH = 5;
	private static TestHelper TEST_HELPER;
	private static UserControlledTimerService TIMER_SERVICE;
	private MarketMakingChangeTracker tracker = MarketMakingChangeTracker.of(SECURITY);

	@Mock
	private MarketMakingChangeHandler changeHandler;

	@BeforeClass
	public static void setup(){
		long sid = 2222222L;
		SecurityType secType = SecurityType.WARRANT;
		String code = "26625";
		int exchangeSid = 1;
		boolean algo = true;
		Security security = Security .of(sid, secType, code, exchangeSid, algo, SpreadTableBuilder.get(SecurityType.WARRANT));
		long undSid = 12345678L;
		boolean isAlgo = false;
		PutOrCall putOrCall = PutOrCall.CALL;
		OptionStyle style = OptionStyle.EUROPEAN;
		int strikePrice = 12345;
		int conversionRatio = 10000;
		int issuerSid = 1;
		int lotSize = 1000;
		
		SECURITY = new ScoreBoardSecurityInfo(security.sid(),
				SecurityType.WARRANT,
				code, 
                security.exchangeSid(),
                undSid,
                putOrCall,
                style,
                strikePrice,
                conversionRatio,
                issuerSid,
                lotSize,                            
                isAlgo,
                SpreadTableBuilder.get(SecurityType.WARRANT));
		
		
		SPREADTABLE = SECURITY.spreadTable();
		TEST_HELPER = TestHelper.of();
		TIMER_SERVICE = TEST_HELPER.timerService(); 
	}
	
	@Before
	public void before(){
		tracker = MarketMakingChangeTracker.of(SECURITY);
		tracker.registerChangeHandler(changeHandler);
	}

	@Test
	public void testCreate(){
		assertNotNull(tracker);
		assertFalse(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
	}

	@Test
	public void givenEmptyWhenObserveFirstTimeThenFirstTickIsNotValid(){
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 
				250, 600_000L,
				249, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		assertFalse(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
		assertEquals(0L, tracker.getTwaBestBidMmSize());
		assertEquals(0L, tracker.getTwaNonBestBidMmSize());
		verify(changeHandler, never()).handleNumBidLevelChange(anyInt(), anyInt(), anyLong());
		verify(changeHandler, never()).handleNumAskLevelChange(anyInt(), anyInt(), anyLong());
		assertEquals(0L, tracker.getTwaMmBidLevels());
		assertEquals(0L, tracker.getTwaMmAskLevels());
	}
	
	@Test
	public void testAll(){
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 
				200, 1_000_000L,
				199, 500_000L);
		ob = addAskPriceLevels(ob, 
				201, 1_000_000L,
				202, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertTrue(tracker.isTwaNonBestBidMmSizeValid());

		ob = addBidPriceLevels(ob, 
				199, 200_000L,
				197, 1_000_000L);
		ob = addAskPriceLevels(ob, 
				202, 200_000L,
				205, 1_000_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		
		ArgumentCaptor<Integer> bestBidLevelArguments = ArgumentCaptor.forClass(int.class);
		ArgumentCaptor<Integer> bestAskLevelArguments = ArgumentCaptor.forClass(int.class);
		ArgumentCaptor<Integer> mmBestBidLevelArguments = ArgumentCaptor.forClass(int.class);
		ArgumentCaptor<Integer> mmBestAskLevelArguments = ArgumentCaptor.forClass(int.class);
		verify(changeHandler, atLeast(1)).handleBestLevelChange(bestBidLevelArguments.capture(), bestAskLevelArguments.capture(), anyBoolean(), anyLong());
		verify(changeHandler, atLeast(1)).handleMMBestLevelChange(mmBestBidLevelArguments.capture(), mmBestAskLevelArguments.capture(), anyBoolean(), anyLong());

		List<Integer> capturedBestBidLevels = bestBidLevelArguments.getAllValues();
		List<Integer> capturedBestAskLevels = bestAskLevelArguments.getAllValues();
		assertEquals(2, capturedBestBidLevels.size());
		assertEquals(SPREADTABLE.priceToTick(200), capturedBestBidLevels.get(0).intValue());
		assertEquals(SPREADTABLE.priceToTick(199), capturedBestBidLevels.get(1).intValue());
		assertEquals(SPREADTABLE.priceToTick(201), capturedBestAskLevels.get(0).intValue());
		assertEquals(SPREADTABLE.priceToTick(202), capturedBestAskLevels.get(1).intValue());
		
		List<Integer> capturedMMBestBidLevels = mmBestBidLevelArguments.getAllValues();
		List<Integer> capturedMMBestAskLevels = mmBestAskLevelArguments.getAllValues();
		assertEquals(2, capturedMMBestBidLevels.size());
		assertEquals(SPREADTABLE.priceToTick(200), capturedMMBestBidLevels.get(0).intValue());
		assertEquals(SPREADTABLE.priceToTick(197), capturedMMBestBidLevels.get(1).intValue());
		assertEquals(SPREADTABLE.priceToTick(201), capturedMMBestAskLevels.get(0).intValue());
		assertEquals(SPREADTABLE.priceToTick(205), capturedMMBestAskLevels.get(1).intValue());
	}
	
	@Test
	public void givenFirstTickObservedWhenTimeThresholdHasPassedThenTwaEventuallyWillBeAvail(){
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 
				250, 600_000L,
				249, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() / 2 - 1));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		assertFalse(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());		

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() / 2 + 1));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());		
		assertEquals(600_000L, tracker.getTwaBestBidMmSize());
		assertEquals(0L, tracker.getTwaNonBestBidMmSize());

		assertEquals(0L, tracker.getTwaMmBidLevels());
		assertEquals(0L, tracker.getTwaMmAskLevels());

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() + 1));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		assertEquals(2L, tracker.getTwaMmBidLevels());
		assertEquals(0L, tracker.getTwaMmAskLevels());
		
		verify(changeHandler, times(1)).handleNumBidLevelChange(eq(2), eq(0), anyLong());
		verify(changeHandler, never()).handleNumAskLevelChange(anyInt(), anyInt(), anyLong());
	}

	@Test
	public void givenFirstTickObservedAskWhenTimeThresholdHasPassedThenTwaEventuallyWillBeAvail(){
		MarketOrderBook ob = createOrderBook();
		ob = addAskPriceLevels(ob,
				249, 500_000L,
				250, 600_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() / 2 - 1));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		assertFalse(tracker.isTwaBestAskMmSizeValid());
		assertFalse(tracker.isTwaNonBestAskMmSizeValid());		

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() / 2 + 1));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		assertTrue(tracker.isTwaBestAskMmSizeValid());
		assertFalse(tracker.isTwaNonBestAskMmSizeValid());		
		assertEquals(600_000L, tracker.getTwaBestAskMmSize());
		assertEquals(0, tracker.getTwaNonBestAskMmSize());
	}
	
	@Test
	public void givenEmptyWhenObserveFirstTimeWithQtyLessThanMinThenFirstTickIsNotValid(){
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 
				250, 490_000L,
				249, 490_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		assertFalse(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
	}
	
	@Test
	public void givenEmptyWhenObserveFirstTimeWithValidQtyBelowBestThenRegisterOnlyBest(){
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 
				250, 490_000L,
				249, 500_000L);

		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
		assertEquals(500_000L, tracker.getTwaBestBidMmSize());
	}
	
	@Test
	public void givenEmptyWhenObserveFirstTimeWithValidQty(){
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 
				250, 490_000L,
				249, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
		assertEquals(500_000L, tracker.getTwaBestBidMmSize());
	}
	
	private static MarketOrderBook createOrderBook(){
		MarketOrderBook ob = MarketOrderBook.of(SECURITY.sid(), 
				BOOK_DEPTH, 
				SPREADTABLE, 
				Integer.MIN_VALUE,
				Integer.MIN_VALUE);
		return ob;
	}
	
	private static MarketOrderBook addBidPriceLevels(MarketOrderBook ob, long ...priceThenQty){
		ob.bidSide().clear();
		for (int i = 0; i < priceThenQty.length; i+=2){
			ob.bidSide().create((int)priceThenQty[i], priceThenQty[i+1]); 
		}
		return ob;
	}
	
	private static MarketOrderBook addAskPriceLevels(MarketOrderBook ob, long ...priceThenQty){
		ob.askSide().clear();
		for (int i = 0; i < priceThenQty.length; i+=2){
			ob.askSide().create((int)priceThenQty[i], priceThenQty[i+1]); 
		}
		return ob;
	}
	
	@Test
	public void givenObservedOnceWhenObserveAgainThenTwaIsAvailable(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 250, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
		
		// When
		TIMER_SERVICE.advance(2, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertFalse(tracker.isTwaNonBestBidMmSizeValid());
	}
	
	@Test
	public void givenObservedOnceWhenObserveAgainWithSameQtyButAtDifferentPriceThenSameResult(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 				
				250, 1_000_000L,
				248, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		long totalTimeElasped = tracker.mmHoldThresholdInNs();
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertTrue(tracker.isTwaNonBestBidMmSizeValid());
		assertEquals(1_000_000L, tracker.getTwaBestBidMmSize());
		assertEquals(500_000L, tracker.getTwaNonBestBidMmSize());
		
		// When
		TIMER_SERVICE.advance(1, TimeUnit.SECONDS);
		totalTimeElasped += TimeUnit.SECONDS.toNanos(1L);
		ob = createOrderBook();
		ob = addBidPriceLevels(ob, 				
				249, 1_000_000L,
				248, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob); 
		assertTrue(tracker.isTwaBestBidMmSizeValid());
		assertTrue(tracker.isTwaNonBestBidMmSizeValid());
		assertEquals(1_000_000L, tracker.getTwaBestBidMmSize());
		assertEquals(500_000L, tracker.getTwaNonBestBidMmSize());
		assertEquals(totalTimeElasped / 1000000, tracker.twaBestBidMmSize().totalCount());
		assertEquals(totalTimeElasped / 1000000, tracker.twaNonBestBidMmSize().totalCount());
	}
	
	@Test
	public void givenObservedOnceWhenObserveDifferentQtyEventuallyThenCorrectResult(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 				
				250, 1_000_000L,
				248, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		// When
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addBidPriceLevels(ob, 				
				250, 970_000L,
				248, 500_000L,
				247, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// Then
		assertEquals(1_000_000L, tracker.getTwaBestBidMmSize());
		assertEquals(500_000L, tracker.getTwaNonBestBidMmSize());

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		assertEquals(3L, tracker.getTwaMmBidLevels());
		assertEquals(0L, tracker.getTwaMmAskLevels());
		

		// When there is another change
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));

		assertEquals(3L, tracker.getTwaMmBidLevels());
		assertEquals(0L, tracker.getTwaMmAskLevels());
	}
	
	@Test
	public void givenObservedNormalBehavioursForAsk(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addAskPriceLevels(ob, 				
				201, 1_000_000L,
				202, 500_000L);

		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When #1
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addAskPriceLevels(ob, 				
				201, 1_000_000L,
				202, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When #2
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addAskPriceLevels(ob, 				
				201, 950_000L,
				202, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 3));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addAskPriceLevels(ob,
				201, 1_000_000L,
				202, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 4));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 4));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		assertEquals(980555, tracker.twaBestAskMmSize().averageValue());
		assertEquals(500000, tracker.twaNonBestAskMmSize().averageValue());
	}
	
	@Test
	public void givenObservedNormalBehaviours(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob, 				
				210, 1_000_000L,
				208, 500_000L,
				207, 500_000L);
		ob = addAskPriceLevels(ob, 				
				211, 1_000_000L,
				212, 500_000L,
				213, 500_000L);

		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When #1
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addBidPriceLevels(ob, 				
				210, 970_000L,
				208, 500_000L,
				207, 500_000L);
		ob = addAskPriceLevels(ob, 				
				211, 1_000_000L,
				212, 500_000L,
				213, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When #2
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addBidPriceLevels(ob,
				210, 950_000L,
				208, 500_000L,
				207, 500_000L);
		ob = addAskPriceLevels(ob, 				
				211, 950_000L,
				212, 500_000L,
				213, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 3));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		ob = createOrderBook();
		ob = addBidPriceLevels(ob,
				210, 1_000_000L,
				208, 500_000L,
				207, 500_000L);
		ob = addAskPriceLevels(ob,
				211, 1_000_000L,
				212, 500_000L,
				213, 500_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 4));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 4));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		assertEquals(977647, tracker.twaBestBidMmSize().averageValue());
		assertEquals(500000, tracker.twaNonBestBidMmSize().averageValue());
		assertEquals(980555, tracker.twaBestAskMmSize().averageValue());
		assertEquals(500000, tracker.twaNonBestAskMmSize().averageValue());
		LOG.info("{}", tracker);
	}
	
	@Test
	public void givenObservedWhenRemoveAllPricesAndAddThemBackThenZeroShouldNotBeRegistered(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob,
				250, 1_000_000L,
				248, 500_000L,
				247, 450_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		// When #1
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		// When nothing
		ob = createOrderBook();
		TIMER_SERVICE.advance(1, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When nothing again
		TIMER_SERVICE.advance(60, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When back to normal
		ob = addBidPriceLevels(ob,
				250, 1_000_000L,
				248, 500_000L,
				247, 500_000L);
		TIMER_SERVICE.advance(1, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		TIMER_SERVICE.advance(2, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		assertEquals(1_000_000L, tracker.getTwaBestBidMmSize());
		assertEquals(500_000L, tracker.getTwaNonBestBidMmSize());
		assertEquals(false, tracker.twaBestAskMmSize().isValidAverage());
		assertEquals(false, tracker.twaNonBestAskMmSize().isValidAverage());
	}
	
	@Test
	public void givenObservedAskWhenRemoveAllPricesAndAddThemBackThenZeroShouldNotBeRegistered(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addAskPriceLevels(ob,
				247, 1_000_000L,
				248, 500_000L,
				250, 450_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		// When #1
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());
		
		// When nothing
		ob = createOrderBook();
		TIMER_SERVICE.advance(1, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When nothing again
		TIMER_SERVICE.advance(60, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When back to normal
		ob = addAskPriceLevels(ob,
				247, 1_000_000L,
				248, 500_000L,
				250, 500_000L);
		TIMER_SERVICE.advance(1, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		TIMER_SERVICE.advance(2, TimeUnit.SECONDS);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);

		assertEquals(1_000_000L, tracker.getTwaBestAskMmSize());
		assertEquals(500_000L, tracker.getTwaNonBestAskMmSize());
		assertEquals(false, tracker.twaBestBidMmSize().isValidAverage());
		assertEquals(false, tracker.twaNonBestBidMmSize().isValidAverage());
	}
	
	@Test
	public void givenObservedWhenBestBidDecreaseSignficantlyThenNewBestShouldBeRegistered(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addBidPriceLevels(ob,
				250, 1_000_000L,
				248, 500_000L,
				247, 450_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When #1
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 10));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		// When #2
		ob = addBidPriceLevels(createOrderBook(),
				250, 500_000L,
				248, 500_000L,
				247, 500_000L);
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		LOG.info("{}", tracker);
		
		// When #3 - less than mmBigChangeTakeEffectTimeInNs has passed
		// Best bid should be lower, Non best bid stays the same
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		LOG.info("{}", tracker);
		assertTrue(tracker.getTwaBestBidMmSize() < 1_000_000L);
		assertTrue(tracker.getTwaNonBestBidMmSize() == 500_000L);
		
		// When #4 - mmBigChangeTakeEffectTimeInNs has passed
		// Reset to latest
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmBigChangeTakeEffectTimeInNs()));
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		assertTrue(tracker.getTwaBestBidMmSize() == 500_000L);
		assertEquals(0, tracker.getTwaNonBestBidMmSize());
		assertFalse(tracker.twaBestAskMmSize().isValidAverage());
		assertFalse(tracker.twaNonBestAskMmSize().isValidAverage());	
	}
	
	@Test
	public void givenObservedWhenBestAskDecreaseSignficantlyThenNewBestShouldBeRegistered(){
		// Given
		MarketOrderBook ob = createOrderBook();
		ob = addAskPriceLevels(ob,
				247, 1_000_000L,
				248, 500_000L,
				250, 450_000L);
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		
		// When #1
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs() * 10));
		tracker.observeNanoOfDay(TIMER_SERVICE.toNanoOfDay());

		// When #2
		ob = addAskPriceLevels(createOrderBook(),
				247, 500_000L,
				248, 500_000L,
				250, 500_000L);
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		LOG.info("{}", tracker);
		
		// When #3 - less than mmBigChangeTakeEffectTimeInNs has passed
		// Best bid should be lower, Non best bid stays the same
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmHoldThresholdInNs()));
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		LOG.info("{}", tracker);
		assertTrue(tracker.getTwaBestAskMmSize() < 1_000_000L);
		assertTrue(tracker.getTwaNonBestAskMmSize() == 500_000L);
		
		// When #4 - mmBigChangeTakeEffectTimeInNs has passed
		// Reset to latest
		TIMER_SERVICE.advance(Duration.ofNanos(tracker.mmBigChangeTakeEffectTimeInNs()));
		tracker.observeOrderBook(TIMER_SERVICE.toNanoOfDay(), ob);
		assertEquals(500_000L, tracker.getTwaBestAskMmSize());
		assertEquals(0, tracker.getTwaNonBestAskMmSize());	
		assertFalse(tracker.twaBestBidMmSize().isValidAverage());
		assertFalse(tracker.twaNonBestBidMmSize().isValidAverage());	
	}

}
