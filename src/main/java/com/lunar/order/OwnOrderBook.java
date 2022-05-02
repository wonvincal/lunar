package com.lunar.order;

import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;

/**
 * @author wongca
 *
 */
public class OwnOrderBook implements OrderStateChangeHandler {
	private final SingleSidedOwnOrderBook[] sides;

	public static OwnOrderBook of(SpreadTable spreadTable, int capacity, int expNumOrdersPerLevel, int nullTickLevel, int nullPrice){
		return new OwnOrderBook(
				SingleSidedOwnOrderBook.of(capacity, spreadTable, expNumOrdersPerLevel, nullTickLevel, nullPrice),
				SingleSidedOwnOrderBook.of(capacity, spreadTable, expNumOrdersPerLevel, nullTickLevel, nullPrice));
	}

	public OwnOrderBook(SingleSidedOwnOrderBook bid, SingleSidedOwnOrderBook ask){
		this.sides = new SingleSidedOwnOrderBook[2];
		this.sides[Side.BUY.value()] = bid;
		this.sides[Side.SELL.value()] = ask;
	}
	
	public boolean isCrossed(Side side, int price, int portSid){
		return false;
	}

	public SingleSidedOwnOrderBook side(Side side){
		return this.sides[side.value()];
	}
	
	public OrderRejectType validate(Side side, int price, int portSid){
		return OrderRejectType.VALID_AND_NOT_REJECT;
	}

	@Override
	public void onNew(OrderAcceptedSbeDecoder accepted) {
	}

	@Override
	public void onAmended(OrderAmendedSbeDecoder amended) {
	}

	@Override
	public void onRejected(OrderRejectedSbeDecoder rejected) {
		// Remove an order
	}

	@Override
	public void onCancelled(OrderCancelledSbeDecoder cancelled) {
		// Remove an order
	}

	@Override
	public void onTrade(TradeSbeDecoder trade) {
		// Change an order outstanding quantity
	}

	@Override
	public void onExpired(OrderExpiredSbeDecoder expired) {
		// Remove an order
	}

	@Override
	public void onAmendRejected(OrderAmendRejectedSbeDecoder amendRejected) {
		// TODO Auto-generated method stub
		
	}
}
