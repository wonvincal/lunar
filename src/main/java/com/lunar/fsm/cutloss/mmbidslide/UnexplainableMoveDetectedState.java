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

/**
 * Sell when it reaches certain threshold
 * Return back to HOLD_POSITION state if:
 * 1) all downward changes are explainable
 * 2) no more downward changes
 * @author wongca
 *
 */
class UnexplainableMoveDetectedState implements State {
	private static final Logger LOG = LogManager.getLogger(UnexplainableMoveDetectedState.class);

	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		LOG.trace("[mmbidslide] Enter UnexplainableMoveDetectedState state [secSid:{}]", box(context.market().security().sid()));
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
			LOG.error("[mmbidslid] Received own buy trade with a different price [secSid:{}, {}, tradeSid:{}, executionPrice:{}]",
					box(context.market().security().sid()),
					context,
					box(trade.tradeSid()),
					box(trade.executionPrice()));
			return Transitions.in_ANY_STATE_receive_FAIL;
		}
		else {
			// If sell trade is detected here, it may be an aftermath of SELL, go to wait state
			LOG.warn("[mmbidslide] Received sell trade in UnexplainableMoveDetectedState state [secSid:{}, quantity:{}]", box(context.market().security().sid()), box(trade.executionQty()));
			return Transitions.in_UNEXPLAINABLE_DOWNWARD_MOVE_receive_SELL_DETECTED;
		}
	}

	@Override
	public Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
		// NOOP
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		// If bidLevel goes back to our original bid price, no need to monitor
		if (bidLevel >= context.initRefBidTickLevel()){
			return Transitions.in_UNEXPLAINABLE_DOWNWARD_MOVE_receive_EXPLAINABLE;
		}
		if (context.suggestedSellAtBidLevel() == context.market().nullTickLevel() && context.market().currentMMBidTickLevel() < context.minDetectedUnexplainableBidLevel() &&
			MmBidSlide.isUnexplainableDownwardMoveDetected(context)){
			context.minDetectedUnexplainableBidLevel(context.market().currentMMBidTickLevel());
			if (context.addDetectedUnexplainableMove() >= context.maxUnexplainableMoves()){
				int currentBidTickLevel = context.market().currentBidTickLevel();
				int expectedLossInTick  = context.initRefTradeTickLevel() - currentBidTickLevel;
				// Don't sell if >= max
				if (expectedLossInTick <= context.maxAllowableLossInTicks()){
					LOG.debug("[mmbidslide] Sell NOW [secSid:{}, bid:{}]", box(context.market().security().sid()), box(currentBidTickLevel));
					context.signalHandler().handleCutLossSignal(context, currentBidTickLevel, nanoOfDay);
					context.suggestedSellAtBidLevel(currentBidTickLevel);
				}
			}
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
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public String toString() {
		return "UnexplainableMoveDetectedState";
	}
}
