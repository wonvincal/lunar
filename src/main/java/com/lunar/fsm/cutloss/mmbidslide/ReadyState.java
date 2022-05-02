package com.lunar.fsm.cutloss.mmbidslide;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.fsm.cutloss.State;
import com.lunar.fsm.cutloss.StateTransitionEvent;
import com.lunar.fsm.cutloss.TradeContext;
import com.lunar.fsm.cutloss.Transition;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Initialize different parameters
 * @author wongca
 *
 */
class ReadyState implements State {
	private static final Logger LOG = LogManager.getLogger(ReadyState.class);
	
	
	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		LOG.trace("[mmbidslide] Enter READY state [secSid:{}]", box(context.market().security().sid()));
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public State exit(TradeContext Context, StateTransitionEvent exitOnEvent) {
		return this;
	}

	@Override
	public Transition onEvent(TradeContext context, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return Transitions.NO_TRANSITION;
		}
		LOG.error("Unexpected event {}, treat this as FAIL [secSid:{}]", event, box(context.market().security().sid()));
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	@Override
	public Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades) {
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		return transitionIfReady(context);
	}

	@Override
	public Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
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
		return transitionIfReady(context);
	}
	
	private static Transition transitionIfReady(TradeContext context){
		if (context.hasBuyTrade()){
			if (context.market().hasMmBidLevels() && context.market().hasValidBidLevel() && context.market().currentUndSpreadInTick() > 0){
				return Transitions.in_READY_receive_BUY_DETECTED;
			}
			else {
				return Transitions.in_ANY_STATE_receive_FAIL;				
			}
		}
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	public String toString() {
		return "ReadyState";
	}

	@Override
	public Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade) {
		if (trade.side() == Side.BUY){
			context.initBuyTrade(trade.executionPrice(), trade.executionQty());
			LOG.trace("[mmbidslide] Received own buy trade [secSid:{}, {}]", box(context.market().security().sid()), context);
			return transitionIfReady(context);
		}
		else {
			// If sell trade is detected here, it may be an aftermath of SELL
			LOG.warn("[mmbidslide] Received sell trade in Ready state[secSid:{}, quantity:{}]", box(context.market().security().sid()), trade.executionQty());
			return Transitions.in_ANY_STATE_receive_FAIL;			
		}
	}
}
