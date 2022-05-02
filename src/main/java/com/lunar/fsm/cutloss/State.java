package com.lunar.fsm.cutloss;

import com.lunar.message.io.sbe.TradeSbeDecoder;

public interface State {
	
	/**
	 * Enter state with specified event
	 * @param context
	 * @param event
	 * @return
	 */
	default Transition enter(TradeContext context, StateTransitionEvent enterOnEvent){
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	/**
	 * Exit state with specified event
	 * @param context
	 * @param event
	 * @return
	 */
	State exit(TradeContext context, StateTransitionEvent exitOnEvent);

	/**
	 * Receive event in current state
	 * @param request
	 * @param event
	 * @return
	 */
	Transition onEvent(TradeContext context, StateTransitionEvent resultingEvent);

	/**
	 * Receive response in current state
	 * @param request
	 * @param message
	 * @return
	 */
//	Transition onMessage(Context context, Response message);

	/**
	 * Receive response in current state
	 * @param request
	 * @param message
	 * @return
	 */
//	Transition onMessage(Context context, DirectBuffer buffer, int offset, ResponseSbeDecoder message);

	/**
	 * Receive timeout event in current state
	 * @param request
	 * @param timerEventType
	 * @return
	 */
//	Transition onTimeout(Context context, TimerEventType timerEventType);

	Transition onOwnSecTrade(TradeContext context, TradeSbeDecoder trade);
	Transition onSecTrade(TradeContext context, long timestamp, int side, int price, int quantity, int numActualTrades);
	Transition onSecBestLevelChange(TradeContext context, int bidLevel, int askLevel, boolean isWide, long nanoOfDay);
	Transition onSecMMBestLevelChange(TradeContext context, int mmBidLevel, int mmAskLevel, boolean isWide, long nanoOfDay);
	Transition onSecNumBidLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay);
	Transition onSecNumAskLevelChange(TradeContext context, int numBestLevels, int numNonBestLevels, long nanoOfDay);
	Transition onSecBidLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay);
	Transition onSecAskLevelQuantityChange(TradeContext context, long bestQty, long nonBestQty, long nanoOfDay);
	Transition onUndBestLevelOrderBookUpdate(TradeContext context, long timestamp, int bestBidPrice, int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread);
}