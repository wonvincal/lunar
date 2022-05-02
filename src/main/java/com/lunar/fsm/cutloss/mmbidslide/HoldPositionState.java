package com.lunar.fsm.cutloss.mmbidslide;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.fsm.cutloss.State;
import com.lunar.fsm.cutloss.StateTransitionEvent;
import com.lunar.fsm.cutloss.TradeContext;
import com.lunar.fsm.cutloss.Transition;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;

class HoldPositionState implements State {
	private static final Logger LOG = LogManager.getLogger(HoldPositionState.class);

	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		LOG.trace("[mmbidslide] Enter HoldPositionState state [secSid:{}]", box(context.market().security().sid()));
		context.resetDetectedUnexplainableMove();
		return Transitions.NO_TRANSITION;
	}

	@Override
	public State exit(TradeContext context, StateTransitionEvent exitOnEvent) {
		return this;
	}

	@Override
	public Transition onEvent(TradeContext context, StateTransitionEvent resultingEvent) {
		LOG.error("[mmbidslide] Received unexpected event [secSid:{}, event:{}]", box(context.market().security().sid()), resultingEvent);
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	@Override
	public Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade) {
		if (trade.side() == Side.BUY){
			// Receive another buy trade
			if (trade.executionPrice() == context.initRefTradePrice()){
				LOG.debug("[mmbidslide] Received own buy trade [secSid:{}, {}]", box(context.market().security().sid()), context);
				return Transitions.NO_TRANSITION;
			}
			else {
				LOG.error("[mmbidslid] Received own buy trade with a different price [secSid:{}, {}, tradeSid:{}, executionPrice:{}]", 
						box(context.market().security().sid()), 
						context,
						box(trade.tradeSid()),
						box(trade.executionPrice()));
				return Transitions.in_ANY_STATE_receive_FAIL;
			}
		}
		else {
			// If sell trade is detected here, it may be an aftermath of SELL, go to wait state
			LOG.warn("[mmbidslide] Received sell trade in HoldPositionState state [secSid:{}, quantity:{}]", context.market().security().sid(), context.osQty());
			return Transitions.in_HOLD_POSITION_receive_SELL_DETECTED;
		}
	}

	@Override
	public Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
		// NOOP - We only worry asbout MM best level change
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		if (bidLevel != context.market().nullTickLevel() && bidLevel < context.initRefBidTickLevel() && MmBidSlide.isUnexplainableDownwardMoveDetected(context)){
			context.minDetectedUnexplainableBidLevel(context.market().currentMMBidTickLevel());
			context.addDetectedUnexplainableMove();
			return Transitions.in_HOLD_POSITION_receive_UNEXPLAINABLE;
		}
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecNumAskLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecBidLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecAskLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onUndBestLevelOrderBookUpdate(TradeContext context, long timestamp, int bestBidPrice, int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public String toString() {
		return "HoldPositionState";
	}
}
