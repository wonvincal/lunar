package com.lunar.fsm.cutloss;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;

/**
 * Initialize different parameters
 * @author wongca
 *
 */
class UndWideSecNoMmChangeState implements State {
	private static final Logger LOG = LogManager.getLogger(UndWideSecNoMmChangeState.class);
	
	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		LOG.trace("[cutloss] Enter UndWideSecNoMmChangeState state [secSid:{}]", box(context.market().security().sid()));
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public State exit(TradeContext context, StateTransitionEvent exitOnEvent) {
		return this;
	}

	@Override
	public Transition onEvent(TradeContext context, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return Transitions.NO_TRANSITION;
		}
		else if (event == StateTransitionEvent.SELL_DETECTED){
			return Transitions.in_UND_WIDE_SEC_NO_MM_CHANGE_receive_SELL_DETECTED;
		}
		else if (event == StateTransitionEvent.DERIV_MM_CHANGE){
			return Transitions.in_UND_WIDE_SEC_NO_MM_CHANGE_receive_DERIV_MM_CHANGE;
		}
		else if (event == StateTransitionEvent.UND_TIGHT){
			return Transitions.in_UND_WIDE_SEC_NO_MM_CHANGE_receive_UND_TIGHT;
		}
		LOG.error("Unexpected event {}, treat this as FAIL [secSid:{}]", event, box(context.market().security().sid()));
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	@Override
	public Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades) {
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade) {
		if (trade.side() == Side.BUY){
			return Transitions.in_ANY_STATE_receive_FAIL;
		}
		else{
			LOG.debug("[cutloss] Received sell trade [secSid:{}, osQty:{}, price:{}, quantity:{}]", box(context.market().security().sid()), context.osQty(), trade.executionPrice(), trade.executionQty());
			return onEvent(context, StateTransitionEvent.SELL_DETECTED);
		}
	}

	@Override
	public Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
		// Warrant best bid/ask change, this is not something we need to take action on in this strategy
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		// See if the number of levels have changed, decreased or increased are both
		// considered as a change
		int total = numBestLevels + numNonBestLevels;
		if (context.initRefNumMmBidLevels() != total){
			return onEvent(context, StateTransitionEvent.DERIV_MM_CHANGE);
		}
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecNumAskLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		return Transitions.NO_TRANSITION;	
	}

	@Override
	public Transition onSecBidLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay) {
		return Transitions.NO_TRANSITION;	
	}

	@Override
	public Transition onSecAskLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay) {
		return Transitions.NO_TRANSITION;	
	}

	@Override
	public Transition onUndBestLevelOrderBookUpdate(TradeContext context, long timestamp, int bestBidPrice, int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread) {
		if (!context.market().isUndWide()){
			return onEvent(context, StateTransitionEvent.UND_TIGHT);
		}
		return Transitions.NO_TRANSITION;	
	}

	@Override
	public String toString() {
		return "UndWideSecNoMmChangeState";
	}
}
