package com.lunar.fsm.cutloss.mmbidslide;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.fsm.cutloss.State;
import com.lunar.fsm.cutloss.StateTransitionEvent;
import com.lunar.fsm.cutloss.TradeContext;
import com.lunar.fsm.cutloss.Transition;
import com.lunar.message.io.sbe.TradeSbeDecoder;

public class StopState implements State {
	private static final Logger LOG = LogManager.getLogger(StopState.class);

	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		// Do whatever that needs to be done for error handling
		// Then transition to Wait state
		LOG.info("[mmbidslide] Enter StopState state [secSid:{}]", box(context.market().security().sid()));
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public State exit(TradeContext context, StateTransitionEvent exitOnEvent) {
		return this;
	}
	@Override
	public Transition onEvent(TradeContext context, StateTransitionEvent resultingEvent) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecNumAskLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecBidLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onSecAskLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay) {
		return Transitions.in_STOP_receive_WAIT;
	}
	@Override
	public Transition onUndBestLevelOrderBookUpdate(TradeContext context, long timestamp, int bestBidPrice,
			int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread) {
		return Transitions.in_STOP_receive_WAIT;
	}
	
	
}
