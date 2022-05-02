package com.lunar.fsm.cutloss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.UserControlledTimerService;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.OrderSender;
import com.lunar.order.Trade;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.scoreboard.ScoreBoardSecurityInfo;
import com.lunar.strategy.scoreboard.UnderlyingOrderBookUpdateHandler;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class VanillaCutLossStateMachineTest {
	static final Logger LOG = LogManager.getLogger(VanillaCutLossStateMachineTest.class);
	private static ScoreBoardSecurityInfo SECURITY;
	private static ScoreBoardSecurityInfo UNDERLYING;
	private CutLossFsm fsm;
	private MarketContext marketContext;
	private MutableDirectBuffer buffer;
	private TradeSbeEncoder tradeEncoder;
	private TradeSbeDecoder tradeDecoder;
	private int tradeSid = 100000;
	private int orderSid = 200000;
	private static PutOrCall putOrCall;
	private static SpreadTable SPREADTABLE;
	private static SpreadTable UND_SPREADTABLE;
	private static int maxAllowableLossInTicks; 
	private static int expectedInitBidDownBuffer;
	private static int nullTickLevel;
	private static int nullPrice;
	private static int initialHandlerCapacity;
	private static TestHelper TEST_HELPER;
	private static UserControlledTimerService TIMER_SERVICE;
	
	@Mock
	private SignalHandler signalHandler;

	@BeforeClass
	public static void setup(){
		long sid = 2222222L;
		SecurityType secType = SecurityType.WARRANT;
		String code = "26625";
		int exchangeSid = 1;
		boolean algo = true;
		Security security = Security.of(sid, secType, code, exchangeSid, algo, SpreadTableBuilder.get(secType)) ;
		long undSid = 12345678L;
		boolean isAlgo = false;
		putOrCall = PutOrCall.CALL;
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
                security.spreadTable());
		
		UNDERLYING = new ScoreBoardSecurityInfo(undSid,
				SecurityType.STOCK,
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
                SpreadTableBuilder.get(SecurityType.STOCK));
		
		SPREADTABLE = SECURITY.spreadTable();
		UND_SPREADTABLE = UNDERLYING.spreadTable();
		maxAllowableLossInTicks = 10;
		expectedInitBidDownBuffer = 2;
		nullTickLevel = Integer.MIN_VALUE;
		nullPrice = Integer.MIN_VALUE;
		initialHandlerCapacity = 2;
		
		TEST_HELPER = TestHelper.of();
		TIMER_SERVICE = TEST_HELPER.timerService(); 

	}
	
	@Before
	public void before(){
		buffer = TEST_HELPER.createDirectBuffer();
		marketContext = MarketContext.of(SECURITY, UNDERLYING, putOrCall, SPREADTABLE, UND_SPREADTABLE, nullTickLevel, nullPrice, initialHandlerCapacity);
		TradeContext tradeContext = TradeContext.of("cutloss", marketContext, expectedInitBidDownBuffer, maxAllowableLossInTicks);
		fsm = new CutLossFsm(tradeContext, States.READY);
		fsm.registerSignalHandler(signalHandler);
		marketContext.addChangeHandler(fsm.marketContextChangeListener());
		tradeDecoder = new TradeSbeDecoder();
		tradeEncoder = new TradeSbeEncoder();
	}
	
	@Test
	public void testCreate(){
		assertNotNull(fsm);
	}
	
	@Test
	public void testInitialSettings(){
		TradeContext context = fsm.context();
		assertEquals(2, context.bidTickLevelBuffer());
		assertFalse(context.hasBuyTrade());
		assertEquals(0, context.totalBoughtQuantity());
		assertEquals(0, context.initRefNumMmBidLevels());
		assertEquals(nullPrice, context.initRefTradePrice());
		assertEquals(nullTickLevel, context.initRefTradeTickLevel());
		assertEquals(nullPrice, context.initRefUndAskPrice());
		assertEquals(nullPrice, context.initRefUndBidPrice());
		assertEquals(Integer.MAX_VALUE, context.initRefUndSpreadInTick());
		assertEquals(maxAllowableLossInTicks, context.maxAllowableLossInTicks());
		
		fsm.init();
		assertEquals(States.READY, context.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}

	@Test
	public void givenReadyAndUndWideAndNoMMBidLevelsWhenReceiveBuyTradeThenFailAndGoBackToReady(){
		fsm.init();
		assertTrue(fsm.context().market().isUndWide());
		assertFalse(fsm.context().market().hasMmBidLevels());
		
		int price = 300; // 0.3
		int quantity = 100000;
		int numActualTrades = 1;
		// When - receive a buy trade
		fsm.onSecTrade(TIMER_SERVICE.toNanoOfDay(), ServiceConstant.TRADE_BUY, price, quantity, numActualTrades);
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
		assertEquals(States.READY, fsm.state());
		assertFalse(fsm.context().hasBuyTrade());
		assertEquals(0, fsm.context().osQty());
		assertEquals(0, fsm.context().totalBoughtQuantity());
	}
	
	@Test
	public void givenReadyAndUndWideAndHasMMBidLevelsAndHasCurrentBidWhenReceiveBuyTradeThenMoveToUndWideNoSecMMChange(){
		fsm.init();
		assertTrue(fsm.context().market().isUndWide());
		assertFalse(fsm.context().market().hasMmBidLevels());
		secNumBidLevelChange(3, 1, TIMER_SERVICE.toNanoOfDay());
		
		// When - receive a buy trade
		int askPrice = 300; // 0.3
		int bidPrice = 295;
		int quantity = 100000;
		secBestLevelChange(bidPrice, askPrice, false, TIMER_SERVICE.toNanoOfDay());
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, askPrice, quantity);
		
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
	}

	@Test
	public void givenUndWideNoSecMMChangeWhenDetectsMMChangeThenMove(){
		fsm.init();
		assertTrue(fsm.context().market().isUndWide());
		assertFalse(fsm.context().market().hasMmBidLevels());
		secNumBidLevelChange(3, 1, TIMER_SERVICE.toNanoOfDay());
		
		int askPrice = 300; // 0.3
		int bidPrice = 295;
		int quantity = 100000;
		secBestLevelChange(bidPrice, askPrice, false, TIMER_SERVICE.toNanoOfDay());
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, askPrice, quantity);
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
		
		// When
		secNumBidLevelChange(2, 0, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}
	
	@Test
	public void givenReadyWhenDetectsTradeWithNoValidMarketDataThenFailed(){
		fsm.init();
		assertTrue(fsm.context().market().isUndWide());
		assertFalse(fsm.context().market().hasMmBidLevels());
		secNumBidLevelChange(3, 1, TIMER_SERVICE.toNanoOfDay());
		
		int price = 300; // 0.3
		int quantity = 100000;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, price, quantity);
		assertEquals(States.WAIT, fsm.state());
		assertEquals(quantity, fsm.context().osQty());
	}
	
	@Test
	public void givenUndWideSecMMChangeInProgressWhenDetectsEndOfMMChangeThenMoveBackToNoMMChangeState(){
		fsm.init();
		assertTrue(fsm.context().market().isUndWide());
		assertFalse(fsm.context().market().hasMmBidLevels());
		secNumBidLevelChange(3, 1, TIMER_SERVICE.toNanoOfDay());
		
		int askPrice = 300; // 0.3
		int bidPrice = 295;
		int quantity = 100000;
		secBestLevelChange(bidPrice, askPrice, false, TIMER_SERVICE.toNanoOfDay());
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, askPrice, quantity);
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
		
		secNumBidLevelChange(2, 0, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, fsm.state());
		
		// When
		secNumBidLevelChange(2, 2, TIMER_SERVICE.toNanoOfDay());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
	}
	
	// At UndWideSecNoMMChange
	// =======================
	// 4.1 - from UndWideNoSecMMChange to UndTightNoSecMMChange
	@Test
	public void givenUndWideNoSecMMChangeWhenUndTightThenMoveToUndTightNoSecMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);
		
		int bidPrice = 70_000;
		int askPrice = 70_050;
		undBestLevelChange(bidPrice, askPrice);
		
		assertEquals(States.UND_TIGHT_SEC_NO_MM_CHANGE, fsm.state());
	}
	
	// 4.2 - from UndWideNoSecMMChange to UndWideSecMMChangeInProgress	
	@Test
	public void givenUndWideNoSecMMChangeWhenDetectsMMChangeThenMoveToUndWideSecMMChangeInProgress(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);
		
		secNumBidLevelChange(2, 0, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(1, 0, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(0, 0, TIMER_SERVICE.toNanoOfDay());
		
		assertEquals(States.UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, fsm.state());
	}
	
	// 4.3 - from UndWideNoSecMMChange to Wait
	@Test
	public void givenUndWideNoSecMMChangeWhenSellDetectedAndOutstandingIsNonZeroThenComplete(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);

		int soldPrice = 290;
		int remaining = 1000;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, soldPrice, tradeQuantity - remaining);
		assertEquals(States.WAIT, fsm.state());
		assertEquals(remaining, fsm.context().osQty());
	}
	
	// 4.4 - from UndWideNoSecMMChange to Wait to Ready
	@Test
	public void givenUndWideNoSecMMChangeWhenSellDetectedAndNoOutstandingThenComplete(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);

		int soldPrice = 290;
		int remaining = 0;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, soldPrice, tradeQuantity - remaining);
		assertEquals(States.READY, fsm.state());
		assertEquals(remaining, fsm.context().osQty());
	}
	
	// 4.5 - from UndWideNoSecMMChange to Stop to Wait
	@Test
	public void givenUndWideNoSecMMChangeWhenBuyDetectedThenStopAndWait(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);

		int buyPrice = 290;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, buyPrice, tradeQuantity);
		assertEquals(States.WAIT, fsm.state());
		assertEquals(tradeQuantity*2, fsm.context().osQty());
	}
	
	// At UndTightSecNoMMChange
	// =======================
	// 5.1 - from UndTightSecNoMMChange to UndWideSecNoMMChange
	@Test
	public void givenUndTightNoSecMMChangeWhenUndWideThenMoveToUndWideNoSecMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 150000;
		int undAskPrice = 150100;
		moveToUndTightSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide, undBidPrice, undAskPrice);
		
		int bidPrice = 70_000;
		int askPrice = 70_050;
		undBestLevelChange(bidPrice, askPrice);
		
		assertEquals(States.UND_TIGHT_SEC_NO_MM_CHANGE, fsm.state());
	}

	// 5.2 - from UndTightNoSecMMChange to UndTightSecMMChangeInProgress	
	@Test
	public void givenUndTightNoSecMMChangeWhenDetectsMMChangeThenMoveToUndTightSecMMChangeInProgress(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 150000;
		int undAskPrice = 150100;
		moveToUndTightSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide, undBidPrice, undAskPrice);
		
		secNumBidLevelChange(2, 0, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(1, 0, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(0, 0, TIMER_SERVICE.toNanoOfDay());
		
		assertEquals(States.UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS, fsm.state());
	}
	
	// 5.3 - from UndTightNoSecMMChange to Wait
	@Test
	public void givenUndTightNoSecMMChangeWhenSellDetectedAndOutstandingIsNonZeroThenComplete(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 150000;
		int undAskPrice = 150100;
		moveToUndTightSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide, undBidPrice, undAskPrice);

		int soldPrice = 290;
		int remaining = 1000;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, soldPrice, tradeQuantity - remaining);
		assertEquals(States.WAIT, fsm.state());
		assertEquals(remaining, fsm.context().osQty());
	}
	
	// 5.4 - from UndTightNoSecMMChange to Wait to Ready
	@Test
	public void givenUndTightNoSecMMChangeWhenSellDetectedAndNoOutstandingThenComplete(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 150000;
		int undAskPrice = 150100;
		moveToUndTightSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide, undBidPrice, undAskPrice);

		int soldPrice = 290;
		int remaining = 0;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, soldPrice, tradeQuantity - remaining);
		assertEquals(States.READY, fsm.state());
		assertEquals(remaining, fsm.context().osQty());
	}
	
	// 5.5 - from UndTightNoSecMMChange to Stop to Wait
	@Test
	public void givenUndTightNoSecMMChangeWhenBuyDetectedThenStopAndWait(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 300;
		int tradePrice = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 150000;
		int undAskPrice = 150100;
		moveToUndTightSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide, undBidPrice, undAskPrice);

		int buyPrice = 290;
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, buyPrice, tradeQuantity);
		assertEquals(States.WAIT, fsm.state());
		assertEquals(tradeQuantity*2, fsm.context().osQty());
	}
	
	// At UndWideSecMMChangeInProgress
	// ===============================
	// 6.1 - from UndWideSecMMChangeInProgress to UndWideSecNoMMChange (With No Sell Signal when our loss is < expectedInitBidDownBuffer)
	@Test
	public void givenUndWideSecMMChangeInProgressWhenMMChangeDoneAndLessThanDownBidBufferThenDontSellAndGoToUndWideSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		int loss = fsm.context().initRefBidTickLevel() - fsm.context().market().currentBidTickLevel();  
		assertTrue(loss < fsm.context().bidTickLevelBuffer());
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}

	// 6.2 - from UndWideSecMMChangeInProgress to UndWideSecNoMMChange (With No Sell Signal when our loss exceeds max)
	@Test
	public void givenUndWideSecMMChangeInProgressWhenMMChangeDoneAndExceedsMaxLossThenDontSellAndGoToUndWideSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		int tickSize = SPREADTABLE.priceToTickSize(bidPriceAtTrade);
		int bidPrice = tradePrice - tickSize * (fsm.context().maxAllowableLossInTicks() + 1);
		int askPrice = bidPrice + tickSize;
		isSecWide = false;
		secBestLevelChange(bidPrice, askPrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}
	
	// 6.3 - from UndWideSecMMChangeInProgress to UndWideSecNoMMChange (With No Sell Signal when sec is wide)
	@Test
	public void givenUndWideSecMMChangeInProgressWhenMMChangeDoneAndSecIsWideThenDontSellAndGoToUndWideSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		int tickSize = SPREADTABLE.priceToTickSize(bidPriceAtTrade);
		int bidPrice = tradePrice - tickSize * (fsm.context().maxAllowableLossInTicks() - 1);
		int askPrice = bidPrice + 2*tickSize;
		isSecWide = true;
		secBestLevelChange(bidPrice, askPrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}
	
	// 6.4 - from UndWideSecMMChangeInProgress to Wait (With Sell Signal)
	@Test
	public void givenUndWideSecMMChangeInProgressWhenMMChangeDoneAndCriteriaAreMetThenSellAndGoToUndWideSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		int tickSize = SPREADTABLE.priceToTickSize(bidPriceAtTrade);
		int bidPrice = tradePrice - tickSize * (fsm.context().maxAllowableLossInTicks() - 1);
		int bidLevel = SPREADTABLE.priceToTick(bidPrice);
		int askPrice = bidPrice + tickSize;
		isSecWide = false;
		secBestLevelChange(bidPrice, askPrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.WAIT, fsm.state());
		verify(signalHandler, times(1)).handleCutLossSignal(any(), eq(bidLevel), anyLong());
	}
	
	// 6.5 - from UndWideSecMMChangeInProgress to Wait 
	@Test
	public void givenUndWideSecMMChangeInProgressWhenDetectSellTradeThenWaitToReady(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndWideSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);

		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, bidPriceAtTrade, tradeQuantity);
		assertEquals(States.READY, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}
	
	// At UndTightSecMMChangeInProgress
	// ===============================
	// 7.1 - from UndTightSecMMChangeInProgress to UndWideSecNoMMChange (With No Sell Signal when our loss is < expectedInitBidDownBuffer)
	@Test
	public void givenUndTightSecMMChangeInProgressWhenMMChangeDoneAndLessThanDownBidBufferThenDontSellAndGoToUndWideSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndTightSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		int loss = fsm.context().initRefBidTickLevel() - fsm.context().market().currentBidTickLevel();  
		assertTrue(loss < fsm.context().bidTickLevelBuffer());
		assertEquals(States.UND_TIGHT_SEC_NO_MM_CHANGE, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}

	// 7.2 - from UndTightSecMMChangeInProgress to UndTightSecNoMMChange (With No Sell Signal when our loss exceeds max)
	@Test
	public void givenUndTightSecMMChangeInProgressWhenMMChangeDoneAndExceedsMaxLossThenDontSellAndGoToUndTightSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndTightSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		int tickSize = SPREADTABLE.priceToTickSize(bidPriceAtTrade);
		int bidPrice = tradePrice - tickSize * (fsm.context().maxAllowableLossInTicks() + 1);
		int askPrice = bidPrice + tickSize;
		isSecWide = false;
		secBestLevelChange(bidPrice, askPrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_TIGHT_SEC_NO_MM_CHANGE, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}
	
	// 7.3 - from UndTightSecMMChangeInProgress to Wait (With Sell Signal although sec is wide)
	@Test
	public void givenUndTightSecMMChangeInProgressWhenMMChangeDoneAndSecIsWideThenDontSellAndGoToUndTightSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndTightSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		int tickSize = SPREADTABLE.priceToTickSize(bidPriceAtTrade);
		int bidPrice = tradePrice - tickSize * (fsm.context().maxAllowableLossInTicks() - 1);
		int askPrice = bidPrice + 2*tickSize;
		isSecWide = true;
		secBestLevelChange(bidPrice, askPrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.WAIT, fsm.state());
		verify(signalHandler, times(1)).handleCutLossSignal(any(), eq(SPREADTABLE.priceToTick(bidPrice)), anyLong());
	}
	
	// 7.4 - from UndTightSecMMChangeInProgress to Wait (With Sell Signal)
	@Test
	public void givenUndTightSecMMChangeInProgressWhenMMChangeDoneAndCriteriaAreMetThenSellAndGoToUndTightSecNoMMChange(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndTightSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);
		
		int tickSize = SPREADTABLE.priceToTickSize(bidPriceAtTrade);
		int bidPrice = tradePrice - tickSize * (fsm.context().maxAllowableLossInTicks() - 1);
		int bidLevel = SPREADTABLE.priceToTick(bidPrice);
		int askPrice = bidPrice + tickSize;
		isSecWide = false;
		secBestLevelChange(bidPrice, askPrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.WAIT, fsm.state());
		verify(signalHandler, times(1)).handleCutLossSignal(anyObject(), eq(bidLevel), anyLong());
	}
	
	// 7.5 - from UndTightSecMMChangeInProgress to Wait 
	@Test
	public void givenUndTightSecMMChangeInProgressWhenDetectSellTradeThenWaitToReady(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int toNumBestBidLevels = 1;
		int toNumNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToUndTightSecMMChangeInProgress(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide,
				toNumBestBidLevels,
				toNumNonBestBidLevels);

		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, bidPriceAtTrade, tradeQuantity);
		assertEquals(States.READY, fsm.state());
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}
	
	// At WaitState
	// 8.1 - from WaitState to Ready
	@Test
	public void givenWaitWhenReceiveTradeSuchThatOSIsZeroThenMoveToReady(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int tradePrice = 300;
		int bidPriceAtTrade = 295;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		moveToWaitState(numBestBidLevels, 
				numNonBestBidLevels, 
				bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);

		for (int i = 1; i < 10; i++){
			secOwnTrade(TIMER_SERVICE.nanoTime(), Side.SELL, tradePrice, 10);			
			assertEquals(States.WAIT, fsm.state());
		}
		secOwnTrade(TIMER_SERVICE.nanoTime(), Side.SELL, tradePrice, 10);			
		assertEquals(States.READY, fsm.state());
	}
	
	private void moveToWaitState(int numBestBidLevels,
		int numNonBestBidLevels, 
		int bidPriceAtTrade,
		int tradePrice,
		int tradeQuantity,
		boolean isSecWide){
		moveToUndWideSecNoMMChange(numBestBidLevels, numNonBestBidLevels, bidPriceAtTrade, tradePrice, tradeQuantity, isSecWide);		
		secOwnTrade(TIMER_SERVICE.nanoTime(), Side.SELL, tradePrice, tradeQuantity - 100);
		assertEquals(States.WAIT, fsm.state());
	}
	
	private void moveToUndTightSecMMChangeInProgress(int fromNumBestBidLevels, 
			int fromNumNonBestBidLevels,
			int bidPriceAtTrade,
			int tradePrice,
			int tradeQuantity,
			boolean isSecWide,
			int toNumBestBidLevels, 
			int toNumNonBestBidLevels){
		assertTrue("toNumBidLevels must be < fromNumBidLevels", (toNumBestBidLevels + toNumNonBestBidLevels) < (fromNumBestBidLevels + fromNumNonBestBidLevels));
		int undBidPrice = 250000;
		int undAskPrice = SPREADTABLE.priceToTickSize(undBidPrice) + undBidPrice;
		moveToUndTightSecNoMMChange(fromNumBestBidLevels, 
				fromNumNonBestBidLevels, 
				bidPriceAtTrade, 
				tradePrice, 
				tradeQuantity, 
				isSecWide,
				undBidPrice,
				undAskPrice);
		secNumBidLevelChange(toNumBestBidLevels, toNumNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS, fsm.state());
	}
	
	private void moveToUndWideSecMMChangeInProgress(int fromNumBestBidLevels, 
			int fromNumNonBestBidLevels,
			int bidPriceAtTrade,
			int tradePrice,
			int tradeQuantity,
			boolean isSecWide,
			int toNumBestBidLevels, 
			int toNumNonBestBidLevels){
		assertTrue("toNumBidLevels must be < fromNumBidLevels", (toNumBestBidLevels + toNumNonBestBidLevels) < (fromNumBestBidLevels + fromNumNonBestBidLevels));
		moveToUndWideSecNoMMChange(fromNumBestBidLevels, 
				fromNumNonBestBidLevels, 
				bidPriceAtTrade, 
				tradePrice, 
				tradeQuantity, 
				isSecWide);
		secNumBidLevelChange(toNumBestBidLevels, toNumNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		assertEquals(States.UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, fsm.state());
	}
	
	private void moveToUndWideSecNoMMChange(int numBestBidLevels, 
			int numNonBestBidLevels,
			int bidPriceAtTrade,
			int tradePrice,
			int tradeQuantity,
			boolean isSecWide){
		fsm.init();
		secBestLevelChange(bidPriceAtTrade, tradePrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, tradeQuantity);
		assertEquals(States.UND_WIDE_SEC_NO_MM_CHANGE, fsm.state());		
	}
	
	private void moveToUndTightSecNoMMChange(int numBestBidLevels, 
			int numNonBestBidLevels,
			int bidPriceAtTrade,
			int tradePrice,
			int tradeQuantity,
			boolean isSecWide,
			int undBidPrice,
			int undAskPrice){
		fsm.init();
		undBestLevelChange(undBidPrice, undAskPrice);
		secBestLevelChange(bidPriceAtTrade, tradePrice, isSecWide, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, tradeQuantity);
		assertEquals(States.UND_TIGHT_SEC_NO_MM_CHANGE, fsm.state());		
	}
	
	private void undBestLevelChange(int bidPrice, int askPrice){
		int bidLevel = SPREADTABLE.priceToTick(bidPrice);
		int askLevel = SPREADTABLE.priceToTick(askPrice);
		final UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook uob = new UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook();
		uob.bestBidPrice = bidPrice;
		uob.bestAskPrice = askPrice;
		uob.bestBidLevel = bidLevel;
		uob.bestAskLevel = askLevel;
		uob.spread = askLevel - bidLevel;
		marketContext.undOrderBookChangeHandler().onUnderlyingOrderBookUpdated(
				TIMER_SERVICE.toNanoOfDay(), 
				uob);
	}
	
	private void secBestLevelChange(int bidPrice, int askPrice, boolean isWide, long nanoOfDay){		
		marketContext.secMMChangeListener().handleBestLevelChange(SPREADTABLE.priceToTick(bidPrice), SPREADTABLE.priceToTick(askPrice), isWide, nanoOfDay);
	}

	private void secNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay){
		marketContext.secMMChangeListener().handleNumBidLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
	}
	
	@SuppressWarnings("unused")
	private void secTrade(long time, int side, int price, int quantity){
		fsm.onSecTrade(time, side, price, quantity, 1);		
	}
	
	private void secOwnTrade(long time, Side side, int price, int quantity){
		int currentSid = tradeSid;
		tradeSid++;
		Trade trade = Trade.of(currentSid,
				orderSid++, 
				orderSid, 
				SECURITY.sid(), 
				side,
				0, 
				quantity, 
				"123456789012345678901",
				price, 
				quantity, 
				OrderStatus.FILLED, 
				TradeStatus.NEW, 
				time, 
				time);
		OrderSender.encodeTradeOnly(buffer, 0, tradeEncoder, 123, 1, trade.sid(), trade.orderSid(), trade.orderId(), OrderStatus.NEW, 
	    		trade.tradeStatus(), 
	    		trade.side(), 
	    		trade.executionId().getBytes(),
	    		trade.executionPrice(), 
	    		trade.executionQty(), 
	    		888);
		tradeDecoder.wrap(buffer, 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
		fsm.onOwnSecTrade(tradeDecoder);
	}
}
