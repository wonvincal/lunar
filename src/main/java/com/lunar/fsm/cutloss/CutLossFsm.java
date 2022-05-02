package com.lunar.fsm.cutloss;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.exception.StateTransitionException;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.strategy.scoreboard.MarketContextChangeHandler;

/**
 * State machine class that handles different events...
 * 
 * Listening to
 * 1) MM Change Tracker - subscribed by CutLossDetector
 * 2) Underlying Market Data - subscribed by CutLossDetector
 * 3) Security Market Data - subscribed by CutLossDetector
 * 
 * On each of these, we should call related CutLossDetector
 * 
 * @author wongca
 *
 */
public class CutLossFsm {
	private static final Logger LOG = LogManager.getLogger(CutLossFsm.class);
	private final TradeContext context;

	public static CutLossFsm createMmBidSlideFsm(MarketContext marketContext){
		final int maxAllowableLossInTicks = 10;
		final int bidTickLevelBuffer = 2;
		TradeContext tradeContext = TradeContext.of("mmbidslide", marketContext, bidTickLevelBuffer, maxAllowableLossInTicks);
		CutLossFsm fsm = new CutLossFsm(tradeContext, com.lunar.fsm.cutloss.mmbidslide.MmBidSlideStates.READY);
		marketContext.addChangeHandler(fsm.marketContextChangeListener());
		return fsm;
	}

	public static CutLossFsm createVanillaFsm(MarketContext marketContext){
		final int maxAllowableLossInTicks = 10;
		final int bidTickLevelBuffer = 2;
		TradeContext tradeContext = TradeContext.of("cutloss", marketContext, bidTickLevelBuffer, maxAllowableLossInTicks);
		CutLossFsm fsm = new CutLossFsm(tradeContext, States.READY);
		marketContext.addChangeHandler(fsm.marketContextChangeListener());
		return fsm;
	}
	
	CutLossFsm(TradeContext context, State initState){
		this.context = context;
		this.context.state(initState);
	}
	
	public void init(){
		context.state().enter(context, StateTransitionEvent.NULL);
	}

	public MarketContextChangeHandler marketContextChangeListener(){
		return marketContextChangeListener;
	}
	
	TradeContext context(){
		return context;
	}
	
	public CutLossFsm registerSignalHandler(SignalHandler handler){
		context.signalHandler(handler);
		return this;
	}
	
	/**
	 * Listener to MarketContext changes
	 */
	private final MarketContextChangeHandler marketContextChangeListener = new MarketContextChangeHandler() {
		
		@Override
		public void handleUndBestLevelChange(long nanoOfDay, int bestBidPrice, int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive und change in fsm [secSid:{}, bestBidPrice:{}, bestAskPrice:{}, spread:{}]",
							context.tag(),
							context.state(),
							box(context.market().security().sid()),
							box(bestBidPrice), box(bestAskPrice), box(spread));					
				}
				context.state().onUndBestLevelOrderBookUpdate(context, nanoOfDay, bestBidPrice, bestBidLevel, bestAskPrice, bestAskLevel, spread).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public void handleNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive num bid level change in fsm [secSid:{}, numBestLevels:{}, numNonBestLevels:{}, bestQty:{}, nonBestQty:{}]",
							context.tag(),
							context.state(),
							box(context.market().security().sid()),
							box(numBestLevels), 
							box(numNonBestLevels),
							box(context.market().currentMmBidBestQty()),
							box(context.market().currentMmBidNonBestQty()));					
				}
				context.state().onSecNumBidLevelChange(context, numBestLevels, numNonBestLevels, nanoOfDay).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public void handleNumAskLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive num ask level change in fsm [secSid:{}, numBestLevels:{}, numNonBestLevels:{}, bestQty:{}, nonBestQty:{}]",
							context.tag(),
							context.state(),
							box(context.market().security().sid()),
							box(numBestLevels), 
							box(numNonBestLevels),
							box(context.market().currentMmAskBestQty()),
							box(context.market().currentMmAskNonBestQty()));				
				}
				context.state().onSecNumAskLevelChange(context, numBestLevels, numNonBestLevels, nanoOfDay).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public void handleBidLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive bid level quantity change in fsm [secSid:{}, bestQty:{}, nonBestQty:{}]",
							context.tag(),
							context.state(),
							box(context.market().security().sid()),
							box(bestQty), 
							box(nonBestQty));					
				}
				context.state().onSecBidLevelQuantityChange(context, bestQty, nonBestQty, nanoOfDay).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public void handleBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive best level change in fsm [secSid:{}, bidPrice:{}, askPrice:{}, bidLevel:{}, askLevel:{}, isWide:{}]",
							context.tag(),
							context.state(),
							box(context.market().security().sid()),
							(bidLevel != Integer.MIN_VALUE) ? context.market().secSpreadTable().tickToPrice(bidLevel) : -1,
							(askLevel != Integer.MIN_VALUE) ? context.market().secSpreadTable().tickToPrice(askLevel) : -1,
							bidLevel, 
							askLevel, isWide);					
				}
				context.state().onSecBestLevelChange(context, bidLevel, askLevel, isWide, nanoOfDay).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public void handleAskLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive ask level quantity change in fsm [secSid:{}, bestQty:{}, nonBestQty:{}]", context.tag(), context.state(), 
							box(context.market().security().sid()),
							bestQty, nonBestQty);					
				}
				context.state().onSecAskLevelQuantityChange(context, bestQty, nonBestQty, nanoOfDay).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void handleMMBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
			try {
				if (LOG.isTraceEnabled()){
					LOG.trace("[{}:{}] Receive MM best level change in fsm [secSid:{}, bidPrice:{}, askPrice:{}, bidLevel:{}, askLevel:{}, isWide:{}]",
							context.tag(),
							context.state(),
							box(context.market().security().sid()),
							(bidLevel != Integer.MIN_VALUE) ? context.market().secSpreadTable().tickToPrice(bidLevel) : -1,
							(askLevel != Integer.MIN_VALUE) ? context.market().secSpreadTable().tickToPrice(askLevel) : -1,
							bidLevel, 
							askLevel, isWide);					
				}
				context.state().onSecMMBestLevelChange(context, bidLevel, askLevel, isWide, nanoOfDay).proceed(context);
			} catch (StateTransitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};

	
	public final void onSecTrade(long timestamp, int side, int price, int quantity, int numActualTrades){
		try {
			if (LOG.isTraceEnabled()){
				LOG.trace("[{}:{}] Receive sec trade in fsm [secSid:{}, side:{}, price:{}, quantity:{}]", context.tag(), context.state(), box(context.market().security().sid()), side, price, quantity);				
			}
   			context.state().onSecTrade(context, timestamp, side, price, quantity, numActualTrades).proceed(context);
		} 
		catch (StateTransitionException e) {
			e.printStackTrace();
		}		
	}
	
	public final void onOwnSecTrade(TradeSbeDecoder trade){
		try {
			if (trade.side() == Side.BUY){
				context.buyTrade(trade.executionPrice(), trade.executionQty());
			}
			else {
				context.sellTrade(trade.executionPrice(), trade.executionQty());
			}
			if (LOG.isTraceEnabled()){
				LOG.trace("[{}:{}] Receive own sec trade in fsm [secSid:{}, side:{}, price:{}, quantity:{}]", context.tag(), context.state(), box(context.market().security().sid()), trade.side(), trade.executionPrice(), trade.executionQty());
			}
			context.state().onOwnSecTrade(context, trade).proceed(context);
		} 
		catch (StateTransitionException e) {
			e.printStackTrace();
		}				
	}
	
	State state(){
		return context.state();
	}
}
