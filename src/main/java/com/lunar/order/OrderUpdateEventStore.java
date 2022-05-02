package com.lunar.order;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;

import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A handy class to store order updates
 * @author wongca
 *
 */
public class OrderUpdateEventStore {
	private static final Logger LOG = LogManager.getLogger(OrderUpdateEventStore.class);
	private final Int2LongArrayMap orderUpdateChannelSeqs;
    private final ObjectArrayList<OrderUpdateEvent> orderUpdates;

    public static OrderUpdateEventStore of(int expectedNumOrder, int expectedUpdatesPerOrder){
    	return new OrderUpdateEventStore(expectedNumOrder, expectedUpdatesPerOrder);
    }
    
	OrderUpdateEventStore(int expectedNumOrder, int expectedUpdatesPerOrder){
		this.orderUpdates = new ObjectArrayList<>(expectedNumOrder * expectedUpdatesPerOrder);
		this.orderUpdateChannelSeqs = new Int2LongArrayMap(expectedNumOrder * expectedUpdatesPerOrder);
		this.orderUpdateChannelSeqs.defaultReturnValue(-1);
	}
	
    // Order update related handler
	private Handler<TradeCreatedSbeDecoder> tradeCreatedHandler = new Handler<TradeCreatedSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, TradeCreatedSbeDecoder payload) {
			// Noop
		};
	};
    
	private Handler<TradeCancelledSbeDecoder> tradeCancelledHandler = new Handler<TradeCancelledSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCancelledSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, TradeCancelledSbeDecoder payload) {
			// Noop
		};
	};

    private Handler<OrderAcceptedSbeDecoder> orderAcceptedHandler = new Handler<OrderAcceptedSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderAcceptedSbeDecoder payload) {
			// Noop
		};
	};

    private Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandler = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderAcceptedWithOrderInfoSbeDecoder payload) {
			// Noop
		};
	};

    private Handler<OrderCancelledSbeDecoder> orderCancelledHandler = new Handler<OrderCancelledSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderCancelledSbeDecoder payload) {
			// Noop
		};
	};

    private Handler<OrderRejectedSbeDecoder> orderRejectedHandler = new Handler<OrderRejectedSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderRejectedSbeDecoder payload) {
			// Noop
		};
	};

    private Handler<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandler = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderRejectedWithOrderInfoSbeDecoder payload) {
			// Noop
		};
	};

    private Handler<OrderExpiredSbeDecoder> orderExpiredHandler = new Handler<OrderExpiredSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderExpiredSbeDecoder payload) {
			// Noop
		};
	};
	
    private Handler<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandler = new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder payload) {
			storeOrderUpdate(payload.channelId(), payload.channelSeq(), buffer, offset, header);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderExpiredWithOrderInfoSbeDecoder payload) {
			// Noop
		};
	};

	public void register(Messenger messenger){
	    messenger.receiver().orderAcceptedHandlerList().add(orderAcceptedHandler);
	    messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandler);
	    messenger.receiver().orderCancelledHandlerList().add(orderCancelledHandler);
	    messenger.receiver().orderExpiredHandlerList().add(orderExpiredHandler);
	    messenger.receiver().orderExpiredWithOrderInfoHandlerList().add(orderExpiredWithOrderInfoHandler);
	    messenger.receiver().orderRejectedHandlerList().add(orderRejectedHandler);
	    messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(orderRejectedWithOrderInfoHandler);
	    messenger.receiver().tradeCreatedHandlerList().add(tradeCreatedHandler);
	    messenger.receiver().tradeCancelledHandlerList().add(tradeCancelledHandler);
	}
	
	public void unregister(Messenger messenger){
	    messenger.receiver().orderAcceptedHandlerList().remove(orderAcceptedHandler);
	    messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandler);
	    messenger.receiver().orderCancelledHandlerList().remove(orderCancelledHandler);
	    messenger.receiver().orderExpiredHandlerList().remove(orderExpiredHandler);
	    messenger.receiver().orderExpiredWithOrderInfoHandlerList().remove(orderExpiredWithOrderInfoHandler);
	    messenger.receiver().orderRejectedHandlerList().remove(orderRejectedHandler);
	    messenger.receiver().orderRejectedWithOrderInfoHandlerList().remove(orderRejectedWithOrderInfoHandler);
	    messenger.receiver().tradeCreatedHandlerList().remove(tradeCreatedHandler);
	    messenger.receiver().tradeCancelledHandlerList().remove(tradeCancelledHandler);
	}
	
    private void storeOrderUpdate(int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header){
		long storedChannelSeq = orderUpdateChannelSeqs.get(channelId);
		if (storedChannelSeq == orderUpdateChannelSeqs.defaultReturnValue()){
			LOG.info("Initialized channel seq [channelId:{}, channelSeq:{}]", channelId, channelSeq);
			orderUpdateChannelSeqs.put(channelId, channelSeq);
		}
		else {
			if (storedChannelSeq + 1 != channelSeq){
				LOG.error("Received out of sync order update [storedChannelSeq:{}, channelSeq:{}]", storedChannelSeq, channelSeq);
			}
			orderUpdateChannelSeqs.put(channelId, channelSeq);
		}
	    LOG.info("Stored order update [channelId:{}, channelSeq:{}]", channelId, channelSeq);
		orderUpdates.add(OrderUpdateEvent.createFrom(header, buffer, offset));
	}  
    
    public ObjectArrayList<OrderUpdateEvent> orderUpdates(){
    	return orderUpdates;
    }
}
