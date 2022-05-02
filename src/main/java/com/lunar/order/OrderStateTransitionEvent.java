package com.lunar.order;

/**
 * Lunar order state
 * @author Calvin
 *
 */
public enum OrderStateTransitionEvent {
	CREATE,	
	AMEND,
	CANCEL,
	CREATE_THROTTLED,
	AMEND_THROTTLED,
	CANCEL_THROTTLED,
	TIMEOUT,
	EXPIRE,	
	NEW,
	REJECTED,
	FILLED,	
	CANCELLED,
	CANCEL_REJECT,
	AMENDED,	
	AMEND_REJECT,
	TRADE_CANCEL, // technically, an order is alive if a trade cancel is received for a live order
				  // if a trade cancel is received for a dead order, the order should remain to be dead
				  // need to clarify with each exchange
	TRADE_REJECT,	
	FAIL,
	ANY,
	UNEXPECTED
}
