package com.lunar.order;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventHandler;
import com.lunar.fsm.channelbuffer.ChannelBufferContext;
import com.lunar.fsm.channelbuffer.ChannelEvent;
import com.lunar.fsm.channelbuffer.ChannelMissingMessageRequester;
import com.lunar.fsm.channelbuffer.MessageAction;
import com.lunar.fsm.channelbuffer.MessageAction.MessageActionType;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.OrderAndTradeDecoderSupplier;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;

/**
 * This buffer should intercept all order related updates and snapshots.
 * Forward to downstream if received message has the expected sequence number.
 * @author wongca
 *
 */
public class OrderChannelBuffer {
	static final Logger LOG = LogManager.getLogger(OrderChannelBuffer.class);
	private final OrderAndTradeDecoderSupplier supplier;
	private final ChannelBufferContext[] channelBuffers;
	private final Int2ReferenceOpenHashMap<EventHandler<ChannelEvent>> channelEventHandlers;
	private final MessageAction nextAction;
	
	public static OrderChannelBuffer of(int messageBufferCapacity, int snapshotBufferCapacity){
		return new OrderChannelBuffer(messageBufferCapacity, snapshotBufferCapacity);
	}
	
	OrderChannelBuffer(int messageBufferCapacity, int snapshotBufferCapacity){
		this.nextAction = MessageAction.of();
		this.supplier = OrderAndTradeDecoderSupplier.of();
		this.channelBuffers = new ChannelBufferContext[ServiceConstant.NUM_ORDER_AND_TRADE_SUBSCRIPTION_CHANNELS];
		for (int i = 0; i < this.channelBuffers.length; i++){
			this.channelBuffers[i] = ChannelBufferContext.of("order-channel", i, messageBufferCapacity, snapshotBufferCapacity, this.commonEventHandler);
		}
		this.channelEventHandlers = new Int2ReferenceOpenHashMap<>();
		initChannelEventHandlers();
	}

	public void start(){
		// Start intercepting calls
		supplier.interceptOrder(orderInterceptor);
		supplier.interceptOrderAccepted(orderAcceptedInterceptor);
		supplier.interceptOrderAcceptedWithOrderInfo(orderAcceptedWithOrderInfoInterceptor);
		supplier.interceptOrderAmended(orderAmendedInterceptor);
		supplier.interceptOrderAmendRejected(orderAmendRejectedInterceptor);
		supplier.interceptOrderCancelled(orderCancelledInterceptor);
		supplier.interceptOrderCancelledWithOrderInfo(orderCancelledWithOrderInfoInterceptor);
		supplier.interceptOrderCancelRejected(orderCancelRejectedInterceptor);
		supplier.interceptOrderExpired(orderExpiredInterceptor);
		supplier.interceptOrderExpiredWithOrderInfo(orderExpiredWithOrderInfoInterceptor);
		supplier.interceptOrderRejected(orderRejectedInterceptor);
		supplier.interceptOrderRejectedWithOrderInfo(orderRejectedWithOrderInfoInterceptor);
		supplier.interceptOrderRejected(orderRejectedInterceptor);
		supplier.interceptOrderRejectedWithOrderInfo(orderRejectedWithOrderInfoInterceptor);
		supplier.interceptTrade(tradeInterceptor);
		supplier.interceptTradeCancelled(tradeCancelledInterceptor);
		supplier.interceptTradeCreated(tradeCreatedInterceptor);
		supplier.interceptTradeCreatedWithOrderInfo(tradeCreatedWithOrderInfoInterceptor);
	}
	
	public void stop(){
		supplier.clearAllInterceptors();
	}
	
	public OrderAndTradeDecoderSupplier supplier(){
		return supplier;
	}
	
    public void register(MessageReceiver receiver){
    	this.supplier.register(receiver);
    }
    
    public void messageRequester(ChannelMissingMessageRequester messageRequester){
		for (int i = 0; i < this.channelBuffers.length; i++){
			this.channelBuffers[i].messageRequester(messageRequester);
		}
    }

    private final EventHandler<ChannelEvent> commonEventHandler = new EventHandler<ChannelEvent>() {
		@Override
		public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
			channelEventHandlers.get(event.templateId())
			.onEvent(event, sequence, endOfBatch);
		}
	};

	private final Handler<OrderAcceptedSbeDecoder> orderAcceptedInterceptor = new Handler<OrderAcceptedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
			// Forward message to channel buffer
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderAcceptedSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderAcceptedHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoInterceptor = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderAcceptedWithOrderInfoHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderCancelledSbeDecoder> orderCancelledInterceptor = new Handler<OrderCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderCancelledSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderCancelledHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderCancelledWithOrderInfoSbeDecoder> orderCancelledWithOrderInfoInterceptor = new Handler<OrderCancelledWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledWithOrderInfoSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderCancelledWithOrderInfoSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderCancelledWithOrderInfoHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderExpiredSbeDecoder> orderExpiredInterceptor = new Handler<OrderExpiredSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderExpiredSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderExpiredHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoInterceptor = new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderExpiredWithOrderInfoSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderExpiredWithOrderInfoHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderRejectedSbeDecoder> orderRejectedInterceptor = new Handler<OrderRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderRejectedSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderRejectedHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoInterceptor = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderRejectedWithOrderInfoSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderRejectedWithOrderInfoHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderAmendedSbeDecoder> orderAmendedInterceptor = new Handler<OrderAmendedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendedSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderAmendedSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderAmendedHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderAmendRejectedSbeDecoder> orderAmendRejectedInterceptor = new Handler<OrderAmendRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendRejectedSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderAmendRejectedSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderAmendRejectedHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderCancelRejectedSbeDecoder> orderCancelRejectedInterceptor = new Handler<OrderCancelRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, OrderCancelRejectedSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderCancelRejectedHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<OrderSbeDecoder> orderInterceptor = new Handler<OrderSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onIndividualSnapshot(codec.channelId(), codec.channelSnapshotSeq(), buffer, offset, header, OrderSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderHandlerList().handle(buffer, offset, header, codec);
			}
		}
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onSequentialSnapshot(codec.channelId(), codec.channelSnapshotSeq(), responseSeq, isLast, buffer, offset, header, OrderSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.orderHandlerList().handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, codec);
			}
		}
	};

	private final Handler<TradeSbeDecoder> tradeInterceptor = new Handler<TradeSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onIndividualSnapshot(codec.channelId(), codec.channelSnapshotSeq(), buffer, offset, header, TradeSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.tradeHandlerList().handle(buffer, offset, header, codec);
			}
		}
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, TradeSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onSequentialSnapshot(codec.channelId(), codec.channelSnapshotSeq(), responseSeq, isLast, buffer, offset, header, TradeSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.tradeHandlerList().handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, codec);
			}
		}
	};

	private final Handler<TradeCreatedSbeDecoder> tradeCreatedInterceptor = new Handler<TradeCreatedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, TradeCreatedSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.tradeCreatedHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoInterceptor = new Handler<TradeCreatedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, TradeCreatedWithOrderInfoSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.tradeCreatedWithOrderInfoHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private final Handler<TradeCancelledSbeDecoder> tradeCancelledInterceptor = new Handler<TradeCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCancelledSbeDecoder codec) {
			int index = codec.channelId() & channelBuffers.length;
			channelBuffers[index].onMessage(codec.channelId(), codec.channelSeq(), buffer, offset, header, TradeCancelledSbeDecoder.TEMPLATE_ID, nextAction);
			if (nextAction.actionType() == MessageActionType.SEND_NOW){
				supplier.tradeCancelledHandlerList().handle(buffer, offset, header, codec);
			}
		}
	};

	private void initChannelEventHandlers(){
		this.channelEventHandlers.put(OrderAcceptedSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderAcceptedSbeDecoder sbe  = new OrderAcceptedSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
				supplier.orderAcceptedHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);				
			}
		});
		this.channelEventHandlers.put(OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderAcceptedWithOrderInfoSbeDecoder sbe  = new OrderAcceptedWithOrderInfoSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
				supplier.orderAcceptedWithOrderInfoHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(OrderCancelledSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderCancelledSbeDecoder sbe  = new OrderCancelledSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
				supplier.orderCancelledHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(OrderCancelledWithOrderInfoSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderCancelledWithOrderInfoSbeDecoder sbe  = new OrderCancelledWithOrderInfoSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderCancelledWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderCancelledWithOrderInfoSbeDecoder.SCHEMA_VERSION);
				supplier.orderCancelledWithOrderInfoHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(OrderRejectedSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderRejectedSbeDecoder sbe  = new OrderRejectedSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);
				supplier.orderRejectedHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(OrderRejectedWithOrderInfoSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderRejectedWithOrderInfoSbeDecoder sbe  = new OrderRejectedWithOrderInfoSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderRejectedWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderRejectedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
				supplier.orderRejectedWithOrderInfoHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(OrderExpiredSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderExpiredSbeDecoder sbe  = new OrderExpiredSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
				supplier.orderExpiredHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(OrderExpiredWithOrderInfoSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final OrderExpiredWithOrderInfoSbeDecoder sbe  = new OrderExpiredWithOrderInfoSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), OrderExpiredWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderExpiredWithOrderInfoSbeDecoder.SCHEMA_VERSION);
				supplier.orderExpiredWithOrderInfoHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(TradeCreatedSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final TradeCreatedSbeDecoder sbe  = new TradeCreatedSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION);
				supplier.tradeCreatedHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(TradeCreatedWithOrderInfoSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final TradeCreatedWithOrderInfoSbeDecoder sbe  = new TradeCreatedWithOrderInfoSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), TradeCreatedWithOrderInfoSbeDecoder.BLOCK_LENGTH, TradeCreatedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
				supplier.tradeCreatedWithOrderInfoHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
		this.channelEventHandlers.put(TradeCancelledSbeDecoder.TEMPLATE_ID, new EventHandler<ChannelEvent>() {
			private final MessageHeaderDecoder header = new MessageHeaderDecoder();
			private final TradeCancelledSbeDecoder sbe  = new TradeCancelledSbeDecoder();
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				header.wrap(event.buffer(), ChannelEvent.OFFSET);
				sbe.wrap(event.buffer(), ChannelEvent.OFFSET + header.encodedLength(), TradeCancelledSbeDecoder.BLOCK_LENGTH, TradeCancelledSbeDecoder.SCHEMA_VERSION);
				supplier.tradeCancelledHandlerList().handle(event.buffer(), ChannelEvent.OFFSET, header, sbe);
			}
		});
	}
}
