package com.lunar.fsm.cutloss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
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
import com.lunar.fsm.cutloss.mmbidslide.MmBidSlideStates;
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
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoardSecurityInfo;
import com.lunar.strategy.scoreboard.UnderlyingOrderBookUpdateHandler;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class MMBidSlideFsmTest {
	static final Logger LOG = LogManager.getLogger(MMBidSlideFsmTest.class);
	private static ScoreBoardSecurityInfo SECURITY;
	private static ScoreBoardSecurityInfo UNDERLYING;
	private CutLossFsm fsm;
	private static TestHelper TEST_HELPER;
	private static UserControlledTimerService TIMER_SERVICE;
	private static PutOrCall putOrCall;
	private static SpreadTable SPREADTABLE;
	private static SpreadTable UND_SPREADTABLE;
	private static int maxAllowableLossInTicks; 
	private static int expectedInitBidDownBuffer;
	private static int nullTickLevel;
	private static int nullPrice;
	private static int initialHandlerCapacity;
	private MarketContext marketContext;
	private MutableDirectBuffer buffer;
	private TradeSbeEncoder tradeEncoder;
	private TradeSbeDecoder tradeDecoder;
	private int tradeSid = 100000;
	private int orderSid = 200000;

	@Mock
	private SignalHandler signalHandler;

	@BeforeClass
	public static void setup(){
		long sid = 2222222L;
		SecurityType secType = SecurityType.WARRANT;
		String code = "26625";
		int exchangeSid = 1;
		boolean algo = true;
		Security security = Security .of(sid, secType, code, exchangeSid, algo, SpreadTableBuilder.get(SecurityType.WARRANT)) ;
		long undSid = 12345678L;
		boolean isAlgo = false;
		putOrCall = PutOrCall.CALL;
		OptionStyle style = OptionStyle.EUROPEAN;
		int strikePrice = 12345;
		int conversionRatio = 10000;
		int issuerSid = 1;
		int lotSize = 1000;
		int tickSense = 1500;
		
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
		
		GenericWrtParams params = new GenericWrtParams();
		params.tickSensitivity(tickSense);
		SECURITY.wrtParams(params);
		
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
		TradeContext tradeContext = TradeContext.of("test", marketContext, expectedInitBidDownBuffer, maxAllowableLossInTicks);
		fsm = new CutLossFsm(tradeContext, MmBidSlideStates.READY).registerSignalHandler(signalHandler);
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
		assertEquals(MmBidSlideStates.READY, context.state());
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
		assertEquals(MmBidSlideStates.READY, fsm.state());
		assertFalse(fsm.context().hasBuyTrade());
		assertEquals(0, fsm.context().osQty());
		assertEquals(0, fsm.context().totalBoughtQuantity());
	}
	
	@Test
	public void givenReadyAndUndWideAndHasMMBidLevelsAndHasCurrentBidWhenReceiveBuyTradeThenMoveToHoldPosition(){
		fsm.init();
		assertTrue(fsm.context().market().isUndWide());
		assertFalse(fsm.context().market().hasMmBidLevels());
		secNumBidLevelChange(3, 1, TIMER_SERVICE.toNanoOfDay());
		undBestLevel(TIMER_SERVICE.toNanoOfDay(), 115000, 116000);
		// When - receive a buy trade
		int askPrice = 300; // 0.3
		int bidPrice = 295;
		int quantity = 100000;
		secBestLevelChange(bidPrice, askPrice, TIMER_SERVICE.toNanoOfDay());
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, askPrice, quantity);
		
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
		
		assertEquals(MmBidSlideStates.HOLD_POSITION, fsm.state());
	}

	/**
	 * At HOLD_POSITION
	 * ================
	 * 1.1 - from HOLD_POSITION to No Transition
	 * Normal case
	 */
	@Test
	public void givenHoldPositionWhenReceiveBuyTradeWithExistingTradePriceThenNoTransition(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		moveToHoldPositionState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, 2000);
		
		assertEquals(MmBidSlideStates.HOLD_POSITION, fsm.state());
		assertEquals(12000, fsm.context().osQty());
		assertEquals(tradePrice, fsm.context().initRefTradePrice());
	}

	/**
	 * 1.2 - from HOLD_POSITION to Fail
	 * Receive buy trade with different price (should be OK, change this later)
	 */
	@Test
	public void givenHoldPositionWhenReceiveBuyTradeWithDiffTradePriceThenFail(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		moveToHoldPositionState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice + 5, 2000);
		
		assertEquals(MmBidSlideStates.WAIT, fsm.state());
		assertEquals(12000, fsm.context().osQty());
		assertEquals(tradePrice, fsm.context().initRefTradePrice());
	}
	
	/**
	 * 1.3 - from HOLD_POSITION to UNEXPLAINABLE
	 * Receive MM best changes down, but no change to underlying
	 */
	@Test
	public void givenHoldPositionWhenReceiveUnexplainableChangeThenTransitionToUnexplainable(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		// Assume we buy at mm bid price
		moveToHoldPositionState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, 2000);

		int mmBidPrice = bidPriceAtTrade - 10;
		int mmAskPrice = tradePrice;
		// No change in underlying, but change in warrant
		secMmBestLevelChange(mmBidPrice, mmAskPrice, TIMER_SERVICE.toNanoOfDay());
		
		assertEquals(MmBidSlideStates.UNEXPLAINABLE_MOVE_DETECTED, fsm.state());
		assertEquals(1, fsm.context().detectedUnexplainableMove());		
	}

	/**
	 * 1.4 - from HOLD_POSITION to no change
	 * Receive MM best changes down but und down as well
	 */
	@Test
	public void givenHoldPositionWhenReceiveExplanableChangeThenNoTransition(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		// Assume we buy at mm bid price
		moveToHoldPositionState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, 2000);

		undBestLevel(TIMER_SERVICE.toNanoOfDay(), undBidPrice - 100, undAskPrice);
		int mmBidPrice = bidPriceAtTrade - 10;
		int mmAskPrice = tradePrice;
		
		// No change in underlying, but change in warrant
		secMmBestLevelChange(mmBidPrice, mmAskPrice, TIMER_SERVICE.toNanoOfDay());
		
		assertEquals(MmBidSlideStates.HOLD_POSITION, fsm.state());
		assertEquals(0, fsm.context().detectedUnexplainableMove());
	}

	/**
	 * At UNEXPLAINABLE
	 * ================
	 * 2.1 Receive MM best changes down and sell
	 */
	@Test
	public void givenUnexplainableWhenReceiveUnexplainableMoveThenSell(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		moveToUnexplainableState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				bidPriceAtTrade - 10,
				tradePrice,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		// Another MM move
		int nextMmBidPrice = bidPriceAtTrade - 15;
		int nextMmAskPrice = tradePrice;
		secBestLevelChange(nextMmBidPrice, nextMmAskPrice, TIMER_SERVICE.toNanoOfDay());
		secMmBestLevelChange(nextMmBidPrice, nextMmAskPrice, TIMER_SERVICE.toNanoOfDay());
		
		verify(signalHandler, times(1)).handleCutLossSignal(any(), eq(SPREADTABLE.priceToTick(nextMmBidPrice)), anyLong());
	}
	
	/**
	 * 2.2 Receive MM best changes down but exceeds max allowable loss, don't sell
	 */
	@Test
	public void givenUnexplainableWhenReceiveUnexplainableMoveButExceedMaxAllowableLossThenNotSell(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		moveToUnexplainableState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				bidPriceAtTrade - 10,
				tradePrice,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		// Another MM move
		int nextMmBidPrice = bidPriceAtTrade - 55;
		int nextMmAskPrice = tradePrice;
		secBestLevelChange(nextMmBidPrice, nextMmAskPrice, TIMER_SERVICE.toNanoOfDay());
		secMmBestLevelChange(nextMmBidPrice, nextMmAskPrice, TIMER_SERVICE.toNanoOfDay());
		
		verify(signalHandler, never()).handleCutLossSignal(any(), anyInt(), anyLong());
	}

	/**
	 * 2.3 Receive sell trade with all quantity, go to ready state
	 */
	@Test
	public void givenUnexplainableWhenReceiveOwnSellTradeThenGoToReady(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		moveToUnexplainableState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				bidPriceAtTrade - 10,
				tradePrice,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, bidPriceAtTrade - 10, tradeQuantity);
		assertEquals(MmBidSlideStates.READY, fsm.state());
	}
	
	/**
	 * 2.4 Receive sell trade with partial quantity, go to wait state
	 */
	@Test
	public void givenUnexplainableWhenReceiveOwnSellTradeThenGoToWait(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		moveToUnexplainableState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				bidPriceAtTrade - 10,
				tradePrice,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.SELL, bidPriceAtTrade - 10, tradeQuantity - 100);
		assertEquals(MmBidSlideStates.WAIT, fsm.state());
	}
	
	/**
	 * 2.5 Receive buy trade, go to stop state then wait state
	 */
	@Test
	public void givenUnexplainableWhenReceiveOwnBuyTradeThenGoToWait(){
		int numBestBidLevels = 3;
		int numNonBestBidLevels = 0; 
		int bidPriceAtTrade = 295;
		int tradePrice = 300;
		int tradeQuantity = 10000;
		boolean isSecWide = true;
		int undBidPrice = 115000;
		int undAskPrice = 115100;
		
		moveToUnexplainableState(numBestBidLevels,
				numNonBestBidLevels,
				bidPriceAtTrade,
				tradePrice,
				tradeQuantity,
				bidPriceAtTrade - 10,
				tradePrice,
				isSecWide,
				undBidPrice,
				undAskPrice);
		
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, bidPriceAtTrade - 10, tradeQuantity - 100);
		assertEquals(MmBidSlideStates.WAIT, fsm.state());
	}
	
	private void moveToUnexplainableState(int numBestBidLevels, 
			int numNonBestBidLevels,
			int bidPriceAtTrade,
			int tradePrice,
			int tradeQuantity,
			int nextMmBidPrice,
			int nextMmAskPrice,
			boolean isSecWide,
			int undBidPrice,
			int undAskPrice){
		if (bidPriceAtTrade >= tradePrice){
			throw new RuntimeException("bidPriceAtTrade must be smaller than tradePrice");
		}
		fsm.init();
		secBestLevelChange(bidPriceAtTrade, tradePrice, TIMER_SERVICE.toNanoOfDay());
		secMmBestLevelChange(bidPriceAtTrade, tradePrice, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		undBestLevel(TIMER_SERVICE.toNanoOfDay(), undBidPrice, undAskPrice);
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, tradeQuantity);
		assertEquals(MmBidSlideStates.HOLD_POSITION, fsm.state());
		assertEquals(0, fsm.context().detectedUnexplainableMove());
		
		// No change in underlying, but change in warrant
		secMmBestLevelChange(nextMmBidPrice, nextMmAskPrice, TIMER_SERVICE.toNanoOfDay());
		
		assertEquals(MmBidSlideStates.UNEXPLAINABLE_MOVE_DETECTED, fsm.state());
		assertEquals(SPREADTABLE.priceToTick(nextMmBidPrice), fsm.context().minDetectedUnexplainableBidLevel());
		assertEquals(1, fsm.context().detectedUnexplainableMove());
	}
	
	private void moveToHoldPositionState(int numBestBidLevels, 
			int numNonBestBidLevels,
			int bidPriceAtTrade,
			int tradePrice,
			int tradeQuantity,
			boolean isSecWide,
			int undBidPrice,
			int undAskPrice){
		if (bidPriceAtTrade >= tradePrice){
			throw new RuntimeException("bidPriceAtTrade must be smaller than tradePrice");
		}
		fsm.init();
		secBestLevelChange(bidPriceAtTrade, tradePrice, TIMER_SERVICE.toNanoOfDay());
		secMmBestLevelChange(bidPriceAtTrade, tradePrice, TIMER_SERVICE.toNanoOfDay());
		secNumBidLevelChange(numBestBidLevels, numNonBestBidLevels, TIMER_SERVICE.toNanoOfDay());
		undBestLevel(TIMER_SERVICE.toNanoOfDay(), undBidPrice, undAskPrice);
		secOwnTrade(TIMER_SERVICE.toNanoOfDay(), Side.BUY, tradePrice, tradeQuantity);
		assertEquals(MmBidSlideStates.HOLD_POSITION, fsm.state());
		assertEquals(0, fsm.context().detectedUnexplainableMove());
	}
	
	private void secMmBestLevelChange(int mmBidPrice, int mmAskPrice, long nanoOfDay){
		int bidTickLevel = SPREADTABLE.priceToTick(mmBidPrice);
		int askTickLevel = SPREADTABLE.priceToTick(mmAskPrice);
		boolean isWide = (askTickLevel - bidTickLevel) > 1;
		marketContext.secMMChangeListener().handleMMBestLevelChange(bidTickLevel, askTickLevel, isWide, nanoOfDay);
	}
	
	private void secBestLevelChange(int bidPrice, int askPrice, long nanoOfDay){
		int bidTickLevel = SPREADTABLE.priceToTick(bidPrice);
		int askTickLevel = SPREADTABLE.priceToTick(askPrice);
		boolean isWide = (askTickLevel - bidTickLevel) > 1;
		marketContext.secMMChangeListener().handleBestLevelChange(bidTickLevel, askTickLevel, isWide, nanoOfDay);
	}

	private void secNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay){
		marketContext.secMMChangeListener().handleNumBidLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
	}
	
	@SuppressWarnings("unused")
	private void secTrade(long time, int side, int price, int quantity){
		fsm.onSecTrade(time, side, price, quantity, 1);		
	}
	
	private void undBestLevel(long time, int bestBidPrice, int bestAskPrice){
		int askTickLevel = UND_SPREADTABLE.priceToTick(bestAskPrice);
		int bidTickLevel = UND_SPREADTABLE.priceToTick(bestBidPrice);
		final UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook uob = new UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook();
		uob.bestBidPrice = bestBidPrice;
		uob.bestAskPrice = bestAskPrice;
		uob.bestBidLevel = bidTickLevel;
		uob.bestAskLevel = askTickLevel;
		uob.spread = askTickLevel - bidTickLevel;		
		
		marketContext.undOrderBookChangeHandler().onUnderlyingOrderBookUpdated(time, uob);
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
