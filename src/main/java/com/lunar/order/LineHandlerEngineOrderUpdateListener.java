package com.lunar.order;

import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;

import org.agrona.DirectBuffer;

/**
 * This interface is exchange specific.  I expect that we will have a different 
 * interface for another exchange.  You may argue that we don't need an interface
 * at all.  
 * 
 * Yes, we may not need this interface for performance perspective.  However
 * having an interface makes testing other components easier.
 * 
 * TODO Check if the line handler engine can really give us the 'decoder'
 * 
 * @author wongca
 *
 */
public interface LineHandlerEngineOrderUpdateListener {
	public void receiveAccepted(DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted);
	public void receiveAmended(DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended);
	public boolean receiveRejected(DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected);
	public void receiveCancelled(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	public void receiveCancelledOnBehalfOf(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	public void receiveCancelledUnsolicited(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	public void receiveCancelRejected(DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected);
	public void receiveAmendRejected(DirectBuffer buffer, int offset, OrderAmendRejectedSbeDecoder amendRejected);
	public void receiveExpired(DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired);
	public void receiveTradeCreated(DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade);
	public void receiveTradeCancelled(DirectBuffer buffer, int offset, TradeCancelledSbeDecoder tradeCancelled);

	public static LineHandlerEngineOrderUpdateListener NULL_LISTENER = new LineHandlerEngineOrderUpdateListener() {
		
		@Override
		public boolean receiveRejected(DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected) {
			return true;
		}
		
		@Override
		public void receiveCancelled(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}
		
		@Override
		public void receiveCancelledOnBehalfOf(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}
		
		@Override
		public void receiveCancelledUnsolicited(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}
		
		@Override
		public void receiveAmended(DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended) {
		}
		
		@Override
		public void receiveAccepted(DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
		}

		@Override
		public void receiveCancelRejected(DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected) {
		}

		@Override
		public void receiveAmendRejected(DirectBuffer buffer, int offset, OrderAmendRejectedSbeDecoder amendRejected) {
		}

		@Override
		public void receiveTradeCreated(DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade) {
		}

		@Override
		public void receiveTradeCancelled(DirectBuffer buffer, int offset, TradeCancelledSbeDecoder tradeCancelled){
			
		}

		@Override
		public void receiveExpired(DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired) {
			// TODO Auto-generated method stub
			
		}
	};
}
