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
class UndWideSecMmChangeInProgressState implements State {
	private static final Logger LOG = LogManager.getLogger(UndWideSecMmChangeInProgressState.class);
	
	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		LOG.trace("[cutloss] Enter UndWideSecMmChangeInProgressState state [secSid:{}]", box(context.market().security().sid()));
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
		else if (event == StateTransitionEvent.WAIT){
			return Transitions.in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_WAIT;
		}
		else if (event == StateTransitionEvent.SELL_DETECTED){
			return Transitions.in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_SELL_DETECTED;
		}
		else if (event == StateTransitionEvent.DERIV_MM_CHANGE_COMPLETE){
			return Transitions.in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_DERIV_MM_CHANGE_COMPLETE;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	@Override
	public Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades) {
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade) {
		// In this state, we definitely do not expect a BUY
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
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		// Check if number of levels is back to what it used to be
		int numBidLevels = numBestLevels + numNonBestLevels;
		if (numBidLevels >= context.initRefNumMmBidLevels()){
			int expectedLossInTick  = context.initRefTradeTickLevel() - context.market().currentBidTickLevel();
			int dropBidInTick = context.initRefBidTickLevel() - context.market().currentBidTickLevel();
			if (expectedLossInTick <= context.maxAllowableLossInTicks() &&
				dropBidInTick >= context.bidTickLevelBuffer() &&
				(!(context.market().isSecWide() && context.market().isUndWide()))){
				LOG.debug("[cutloss] Sell now [secSid:{}, bid:{}]", box(context.market().security().sid()), box(context.market().currentBidTickLevel()));
				context.signalHandler().handleCutLossSignal(context, context.market().currentBidTickLevel(), nanoOfDay);
				return onEvent(context, StateTransitionEvent.WAIT);
			}
			// Although we don't want to sell at this moment, the fact that we've detected that number of bid levels
			// return to our initial level, we should go back to previous state.
			return onEvent(context, StateTransitionEvent.DERIV_MM_CHANGE_COMPLETE);
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
		return Transitions.NO_TRANSITION;
	}

	@Override
	public String toString() {
		return "UndWideSecMmChangeInProgressState";
	}

}
