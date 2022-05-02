package com.lunar.fsm.cutloss;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.apache.logging.log4j.util.Unbox.box;
import com.lunar.message.io.sbe.TradeSbeDecoder;

/**
 * This state is to wait for all orders to be sold (OS = 0) before it can proceed to READY again
 * @author wongca
 *
 */
class WaitState implements State {
	private static final Logger LOG = LogManager.getLogger(WaitState.class);
	
	
	@Override
	public Transition enter(TradeContext context, StateTransitionEvent enterOnEvent) {
		LOG.trace("[cutloss] Enter WaitState state [secSid:{}]", box(context.market().security().sid()));
		if (context.osQty() == 0){
			context.calculatePnl();
			context.reset();
			LOG.info("[cutloss] [secSid:{}, actualPnl:{}, theoPnl:{}]", box(context.market().security().sid()), context.actualPnl() / 1000.00, context.theoPnl() / 1000.00);
			return Transitions.in_WAIT_receive_READY;
		}
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
	public Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade){
		if (context.osQty() == 0){
			context.calculatePnl();
			LOG.info("[cutloss] [secSid:{}, actualPnl:{}, theoPnl:{}]", box(context.market().security().sid()), context.actualPnl() / 1000.00, context.theoPnl() / 1000.00);
			context.reset();
			return Transitions.in_WAIT_receive_READY;
		}
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecMMBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		return Transitions.NO_TRANSITION;
	}

	@Override
	public Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay) {
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
		return "WaitState";
	}
}
