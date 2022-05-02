package com.lunar.order;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.TimerService;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Client that keeps track of order related request and execution reports.
 * Even if an order has already completed (e.g. filled), our system should be able to find out the owner sink of 
 * that order and send status updates to that owner. 
 *
 * Expected receivers of Order Updates and Trades (Execution Reports)
 * 1. Client
 *    a. send order to OMES
 *    b. receive all updates related to the order
 * 2. Snapshot Service
 *    a. send subscription request to OMES
 *    b. receive subscribed updates
 * 3. Position Service
 *    a. send subscription request to OMES
 *    b. receive subscribed updates
 * 
 * This class should be renamed to focus on only one thing - 
 * Order Request Only!
 * 
 * For each order request, client needs only to know one thing - 
 * whether the order request was sent OK or not.
 * 
 * If ok, the order request can be completed (or removed, gone, done.)
 * If not ok, the client will need to react accordingly.
 * If it is due to:
 * 1) timeout - something wrong with OMES, stop what it is currently doing
 * 2) failed - something failed unexpectedly, stop what it is currently doing
 * 3) rejected due to internal control, stop what it is doing and adjust risk params
 * 4) rejected due to external factor (e.g. too much price variation), adjust the strategy
 * 5) ok
 * 
 * Therefore, we break OrderRequest into three states:
 * 1) New
 * 2) Accepted
 * 3) Completed (ok or failed or rejected or timeout...etc)
 * 
 * If an order is not completed within certain time, we should mark it as timed out and
 * alert the owner.
 * 
 * New state - when the order request is created
 * Accepted state - optional state, when the order request has assigned with an internal
 * order sid.
 * Completed state - when the order request is:
 * 1. Internally rejected
 * 2. Internally timeout
 * 3. Externally accepted by the exchange
 * 4. Externally rejected by the exchange
 * 5. Externally order cancelled by the exchange
 * 6. Externally order cancel rejected amended by the exchange
 * 7. Externally order amended by the exchange
 * 8. Externally order amend rejected by the exchange
 *  
 *  
 * Just need to make sure whether an Order Request was received by OMES, possible outcomes:
 * 1) new order
 *    a) order accepted with order sid
 *    b) order rejected
 * 2) cancel order
 *    a) order accepted
 *    b)  
 * 
 * @author Calvin
 *
 */
public class OrderReqAndExecTracker {
	private static final int EXPECTED_OUTSTANDING_ORDERS = 100;
	private static final float LOAD_FACTOR = 0.6f;
	private static final Logger LOG = LogManager.getLogger(OrderReqAndExecTracker.class);
	private final Int2ObjectOpenHashMap<OrderRequestContext> requests;
	private final OrderSender sender;
	private final MutableDirectBuffer stringBuffer;
	
	public static OrderReqAndExecTracker of(MessageSinkRef self, TimerService timerService, OrderSender sender){
		return new OrderReqAndExecTracker(EXPECTED_OUTSTANDING_ORDERS, LOAD_FACTOR, sender);
	}
	
	private OrderReqAndExecTracker(int expected, 
			float load,
			OrderSender sender){
		this.requests = new Int2ObjectOpenHashMap<OrderRequestContext>(expected, load);
		this.sender = sender;
		this.stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
	}
	
	/**
	 * 
	 * @param sink
	 * @param orderRequest
	 * @param handler
	 * @return
	 */
	public CompletableFuture<OrderRequest> sendNewOrderAndTrack(MessageSinkRef omes, NewOrderRequest orderRequest){
		OrderRequestContext orderRequestContext = OrderRequestContext.of(orderRequest);
		long result = this.sender.sendNewOrder(omes, orderRequest);
		if (result == MessageSink.OK){
//			LOG.info("Put order request {} into tracker", orderRequest.clientKey());
			requests.put(orderRequest.clientKey(), orderRequestContext);			
		}
		else {
//			LOG.info("Could not deliver new order. [result:" + result + "]");
			orderRequestContext.result().completeExceptionally(new Exception("Could not deliver new order. [result:" + result + "]"));
		}
		return orderRequestContext.result();
	}
	
	public CompletableFuture<OrderRequest> sendNewCompositeOrderAndTrack(MessageSinkRef omes, NewOrderRequest orderRequest){
		OrderRequestContext orderRequestContext = OrderRequestContext.of(orderRequest);
		long result = this.sender.sendNewCompositeOrder(omes, orderRequest);
		if (result == MessageSink.OK){
			requests.put(orderRequest.clientKey(), orderRequestContext);			
		}
		else {
			orderRequestContext.result().completeExceptionally(new Exception("Could not deliver new composite order. [result:" + result + "]"));
		}
		return orderRequestContext.result();
	}
	
	public CompletableFuture<OrderRequest> cancelOrderAndTrack(MessageSinkRef omes, CancelOrderRequest orderRequest){
		OrderRequestContext orderRequestContext = OrderRequestContext.of(orderRequest);
		long result = this.sender.sendCancelOrder(omes, orderRequest);
		if (result != MessageSink.OK){
//			LOG.info("Could not deliver cancel order. [result:" + result + "]");
			orderRequestContext.result().completeExceptionally(new Exception("Could not deliver cancel order. [result:" + result + "]"));
		}
//		LOG.info("Put order request {} into tracker", orderRequest.seq());
		requests.put(orderRequest.clientKey(), orderRequestContext);
		return orderRequestContext.result();
	}

	private final Handler<OrderRequestAcceptedSbeDecoder> orderRequestAcceptedHandler = new Handler<OrderRequestAcceptedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder codec) {
			// This can be a noop for now
			// We can use this as an indicator that OMES is pingable
		}
	};
	
	private final Handler<OrderAcceptedSbeDecoder> orderAcceptedHandler = new Handler<OrderAcceptedSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
			LOG.warn("Unexpected order accepted without order info [orderSid:{}]", codec.orderSid());
		}
	};

	private final Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandler = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
			OrderRequestContext context = requests.get(codec.clientKey());
			if (context != null){
			    assert codec.orderSid() != OrderAcceptedWithOrderInfoSbeDecoder.orderSidNullValue();
				OrderRequest request = context.request().completionType(OrderRequestCompletionType.OK).rejectType(OrderRequestRejectType.VALID_AND_NOT_REJECT).orderSid(codec.orderSid());
				context.result().complete(request);				
			}
		}
	};

	private final Handler<OrderRequestCompletionSbeDecoder> orderRequestCompletionHandler = new Handler<OrderRequestCompletionSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder completed) {
//			LOG.info("Received order request completion message [orderSid:{}]", completed.orderSid());
			OrderRequestContext context = requests.remove(completed.clientKey());
			completed.getReason(stringBuffer.byteArray(), 0);
			OrderRequest request = context.request()
					.completionType(completed.completionType())
					.rejectType(completed.rejectType())
					.orderSid(completed.orderSid())
					.reason(stringBuffer.byteArray());
			
			context.result().complete(request);
		}
	};

	public void registerEventsForOrderRequestTracker(MessageReceiver receiver){
		receiver.orderRequestAcceptedHandlerList().add(orderRequestAcceptedHandler);
		receiver.orderAcceptedHandlerList().add(orderAcceptedHandler);
		receiver.orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandler);
		receiver.orderRequestCompletionHandlerList().add(orderRequestCompletionHandler);
	}
	
	public void unregisterEventsForOrderRequestTracker(MessageReceiver receiver){
		receiver.orderRequestAcceptedHandlerList().remove(orderRequestAcceptedHandler);
		receiver.orderAcceptedHandlerList().remove(orderAcceptedHandler);
		receiver.orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandler);
		receiver.orderRequestCompletionHandlerList().remove(orderRequestCompletionHandler);
	}
	
	public Int2ObjectOpenHashMap<OrderRequestContext> requests(){
		return requests;
	}
}
