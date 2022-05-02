package com.lunar.order;

import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.TimerService;
import com.lunar.entity.LongEntityManager;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.OrderAcceptedDecoder;
import com.lunar.message.binary.OrderAmendRejectedDecoder;
import com.lunar.message.binary.OrderAmendedDecoder;
import com.lunar.message.binary.OrderCancelRejectedDecoder;
import com.lunar.message.binary.OrderCancelledDecoder;
import com.lunar.message.binary.OrderExpiredDecoder;
import com.lunar.message.binary.OrderRejectedDecoder;
import com.lunar.message.binary.TradeCancelledDecoder;
import com.lunar.message.binary.TradeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Manager of all order context.
 * *STRICT* All updates driven by updates from line handler.
 * It notifies OMES that an order request has been completed or failed
 * 
 * We are not going to store all orders in memory.
 * 
 * When an order is cancelled and filled.  It will be removed from its memory.
 * 
 * If we receive updates for an order that has been cancelled, this is definitely a bug; either
 * our own bug or exchange's bug (plan for the worst.)  In this case, we should
 * 1) Send a message to Dead Letter message sink
 * 2) Report it
 * 3) Has a mechanism to 're-cancel' an order
 * 
 * Thread Safety: NO
 * 
 * Note from OrderEntityManager
 * ============================
 * An entity manager that keeps track of all outstanding orders in the system.
 * Canceled orders should be stored in other means (not in memory).  This class is
 * different from {@link LongEntityManager} in which orders are using created/consumed
 * in sequential orders, so locality of reference can be achieved if we store
 * orders in sequential order in this class.
 * 
 * Conceptually a linked list can do the job, however behind the scene, objects in
 * a linked list would be created in memory space that can be far away from each
 * other.
 * 
 * Array is good, but search is slow in the order of O(n)
 * 
 * Proposed solution:
 * 1. Open address hash map to save memory
 * 2. Low load factor to avoid collision
 * 3. Hashing
 *    a. Open/Double Hashing
 *    b. First hash code uses (orderId mod size) [use orderId & (size - 1)]
 *    c. Second hash code - Do not use linear probing because two orders having the same hash code is an
 *    indication that something is screwed up.  Use some key that can give us a good distribution.
 * 4. Array of objects can be pre-allocated
 * 5. Use one bit of the key (e.g. key[hash(index)] & (1L << 63))to decide whether the value is empty or not
 * 6. Should copy from FastUtil.Long2ObjectOpenHashMap
 * 7. Modify it to accept an object factory to create new Order
 * 8. Object pooling
 *    I want to reuse each created Order object.  So, it would be better to get an new Order from this class.
 *    manager.getNewOrder(){
 *    	int msgSeq = msgSeq.getAndIncrement();
 *      int index = index(hash(msgSeq));
 *      int key = keys[index];
 *      while {
 *      	if (key < 0){
 *      		keys[index] &= (-1 >> 1 << 1); // unset the first bit
 *      		return values[index];
 *      	}
 *      	index = index(rehash(msqSeq));
 *      	key = keys[index];
 *      }
 *    }
 *    manager.removeOrder(int id){
 *    	// set the bit back!
 *    }
 * 9. This class can be used by Trade (Fill) - anything that behaves similarly to orders.   
 * 
 * This class is able to accept order and trade updates from different exchange specific raw binary messages,
 * 
 * accept binary of exchange,
 * process the binary data to order, convert it into our own format and send it out   
 *    
 * 
 * Does Order need reference to a Security?
 * 
 * 
 * @author wongca
 *
 */
public class OrderContextManager implements LineHandlerEngineOrderUpdateListener {
	private static final Logger LOG = LogManager.getLogger(OrderContextManager.class);
	
	private final Int2ObjectMap<OrderContext> orderContextByOrdSid;
	private final AtomicInteger tradeSidGenerator;
	/**
	 * This is a mapping of (Client Order ID, Order Request).  
	 * Please note that this is not a mapping of (Original Client Order ID, Order Request).
	 * This mapping is required because messages coming from the exchange do not always contain
	 * original client order ID (e.g. Order Cancel Rejected).
	 * 
	 * Sample flow in the binary specification shows that Original Client Order ID is available
	 * in the Order Cancel Rejected message.
	 *
	 */
	private final OrderManagementContext orderManagementContext;
	private OrderRequestCompletionHandler currentOrdReqCompletionHandler = OrderRequestCompletionHandler .NULL_HANDLER;
	private final OrderRequestCompletionHandler activeOrdReqCompletionHandler;
	private LineHandlerEngineOrderUpdateListener currentRecoveryHandler = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;
	private final ExposureUpdateHandler exposureUpdateHandler;
	
	/**
	 * TODO Replace this map with persistence
	 */
	private final Int2ObjectOpenHashMap<OrderContext> archivedOrderContextBySid;
	
	private final TimerService timerService;
	private final Messenger messenger;
	private final OrderSender sender;
	private final long[] sinkSendResults;

	public static OrderContextManager createWithChildMessenger(int initialCapacity,
			OrderManagementContext orderManagementContext,
			Messenger messenger,
			OrderRequestCompletionHandler orderReqCompletionHandler,
			ExposureUpdateHandler exposureUpdateHandler){
		return new OrderContextManager(initialCapacity, 
				orderManagementContext, 
				messenger.timerService(), 
				messenger.createChildMessenger(),
				orderReqCompletionHandler,
				exposureUpdateHandler);
	}
	
	OrderContextManager(int initialCapacity,
			OrderManagementContext orderManagementContext,
			TimerService timerService,
			Messenger messenger,
			OrderRequestCompletionHandler orderReqCompletionHandler,
			ExposureUpdateHandler exposureUpdateHandler){
		this.orderManagementContext = orderManagementContext;
		this.orderContextByOrdSid = new Int2ObjectOpenHashMap<>(initialCapacity);
		this.archivedOrderContextBySid = new Int2ObjectOpenHashMap<>(initialCapacity);
		this.timerService = timerService;
		this.sender = messenger.orderSender();
		this.messenger = messenger;
		this.sinkSendResults = new long[ServiceConstant.MAX_SUBSCRIBERS];
		this.currentOrdReqCompletionHandler = orderReqCompletionHandler;
		this.activeOrdReqCompletionHandler = orderReqCompletionHandler;
		this.tradeSidGenerator = new AtomicInteger(ServiceConstant.START_TRADE_SID_SEQUENCE);
		this.exposureUpdateHandler = exposureUpdateHandler;
	}
	
	long[] sinkSendResults(){
		return sinkSendResults;
	}
	
	Int2ObjectMap<OrderContext> orderContextByOrdSid(){
		return orderContextByOrdSid;
	}
	
	private final IntHashSet recoveredOrdSids = new IntHashSet(-1);
	
	private final LineHandlerEngineOrderUpdateListener recoveryHandler = new LineHandlerEngineOrderUpdateListener(){

        @Override
        public void receiveAccepted(DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
        	int orderSid = accepted.orderSid();        	
            orderManagementContext.updateLatestOrderSid(accepted.orderSid());
            
            if (!recoveredOrdSids.add(orderSid)){
            	LOG.error("Received order accepted for an existing order. Skip [{}]", OrderAcceptedDecoder.decodeToString(accepted));
            	return;
            }
            
            // Create order request with specific orderSid
        	// We lost the order request
        	// We can retrieve it back from 'journal', 'database', however that is always a chance that
        	// persist fails
        	//
        	long secSid = accepted.secSid();
        	int price = accepted.price();
        	int quantity = accepted.cumulativeQty() + accepted.leavesQty();
        	Side side = accepted.side();
        	
        	LOG.info("Processing recovery order accepted [orderSid:{}, side:{}, secSid:{}, price:{}, quantity:{}]", orderSid, side.name(), secSid, price, quantity);

        	SecurityLevelInfo securityLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
			NewOrderRequest request = NewOrderRequest.ofWithDefaults(ServiceConstant.NULL_CLIENT_ORDER_REQUEST_ID,
					messenger.referenceManager().deadLetters(),
					secSid,
					OrderType.LIMIT_ORDER /* Just a guess */,
					quantity,
					side,
					TimeInForce.DAY /* Another guess */,
					BooleanType.FALSE /* Guess */,
					price,
					price,
					Long.MAX_VALUE,
					false,
					-1)
					.securityLevelInfo(securityLevelInfo);
			request.orderSid(accepted.orderSid());
			orderManagementContext.putOrderRequest(accepted.orderSid(), request);
			if (request.side() == Side.BUY){
			    long notional = (long)price * quantity;
			    orderManagementContext.exposure().decPurchasingPower(notional);
//				securityLevelInfo.validationOrderBook().newBuyOrder(price);
			}
//			else {
//				securityLevelInfo.validationOrderBook().newSellOrder(price, quantity);
//			}
		    exposureUpdateHandler.updateValidationBook(request.side(), secSid, price, quantity);
			orderManagementContext.exposureAfterRecoveryUpdatedTime(messenger.timerService().toNanoOfDay());
        }

        @Override
        public void receiveAmended(DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended) {
            orderManagementContext.updateLatestOrderSid(amended.orderSid());
            // NOOP ignore
        }

        @Override
        public boolean receiveRejected(DirectBuffer buffer, int offset, OrderRejectedSbeDecoder recovered) {
        	int orderSid = recovered.orderSid();
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
            
            if (!recoveredOrdSids.add(orderSid)){
            	LOG.warn("Skip processing order rejected with an orderSid that has already been used in the system. [{}]", OrderRejectedDecoder.decodeToString(recovered));
            	return false;
            }
            
            // Order rejected can happen when we use a duplicate client order id, in that case 
            // an order request may already exist and we should ignore that.
            long secSid = recovered.secSid();
            int price = recovered.price();
            int quantity = recovered.cumulativeQty() + recovered.leavesQty();
        	Side side = recovered.side();
        	
			LOG.info("Processing recovery order rejected [orderSid:{}, side:{}, secSid:{}, price:{}, leavesQty:{}, cumulativeQty:{}]", 
					orderSid, side.name(), secSid, price, recovered.leavesQty(), recovered.cumulativeQty());

			if (secSid <= 0){
				LOG.error("Recovered order rejected message contains invalid info [secSid:{}]", secSid);
				return false;
			}
			
            SecurityLevelInfo securityLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
            NewOrderRequest request = NewOrderRequest.ofWithDefaults(ServiceConstant.NULL_CLIENT_ORDER_REQUEST_ID,
            		messenger.referenceManager().deadLetters(),
            		secSid,
            		OrderType.LIMIT_ORDER /* Just a guess */,
            		quantity,
            		side,
            		TimeInForce.DAY /* Another guess */,
            		BooleanType.FALSE /* Guess */,
            		price,
            		price,
            		Long.MAX_VALUE,
            		false,
            		-1)
            		.securityLevelInfo(securityLevelInfo);
            request.orderSid(recovered.orderSid());
            orderManagementContext.putOrderRequest(recovered.orderSid(), request);
            if (request.side() == Side.BUY){
            	long notional = (long)price * quantity;
            	orderManagementContext.exposure().decPurchasingPower(notional);
//            	securityLevelInfo.validationOrderBook().newBuyOrder(price);
            }
//            else {
//            	securityLevelInfo.validationOrderBook().newSellOrder(price, quantity);
//            }
		    exposureUpdateHandler.updateValidationBook(request.side(), secSid, price, quantity);
            orderManagementContext.exposureAfterRecoveryUpdatedTime(messenger.timerService().toNanoOfDay());
            return true;
        }

        @Override
        public void receiveCancelled(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder recovered) {
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
            // There is no way to tell if this cancelled was caused by a CancelOrderRequest, OrderExpired, Mass Order Cancel...etc
            // There is no impact to use if CancelOrderRequest is not being created here
            
            // This cancelled should be sent back to OMES such that
            // exposure and position can be adjusted (same as regular flow)
			LOG.info("Processing recovery order cancelled [orderSid:{}, side:{}, secSid:{}, price:{}, leavesQty:{}, cumulativeQty:{}]", 
					recovered.orderSid(), recovered.side().name(), recovered.secSid(), recovered.price(), recovered.leavesQty(), recovered.cumulativeQty());
        }

        @Override
        public void receiveCancelledOnBehalfOf(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder recovered) {
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
			LOG.info("Processing recovery order cancelled on behalf of [orderSid:{}, side:{}, secSid:{}, price:{}, leavesQty:{}, cumulativeQty:{}]", 
					recovered.orderSid(), recovered.side().name(), recovered.secSid(), recovered.price(), recovered.leavesQty(), recovered.cumulativeQty());
        }

        @Override
        public void receiveCancelledUnsolicited(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder recovered) {
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
			LOG.info("Processing recovery order cancelled unsolicited [orderSid:{}, side:{}, secSid:{}, price:{}, leavesQty:{}, cumulativeQty:{}]", 
					recovered.orderSid(), recovered.side().name(), recovered.secSid(), recovered.price(), recovered.leavesQty(), recovered.cumulativeQty());
        }

        @Override
        public void receiveCancelRejected(DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder recovered) {
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
            // Checked - no other action needs to be done
			LOG.info("Processing recovery order cancelled unsolicited [orderSid:{}, side:{}, secSid:{}, leavesQty:{}, cumulativeQty:{}]", 
					recovered.orderSid(), recovered.side().name(), recovered.secSid(), recovered.leavesQty(), recovered.cumulativeQty());
        }

        @Override
        public void receiveAmendRejected(DirectBuffer buffer, int offset, OrderAmendRejectedSbeDecoder amendRejected) {
            orderManagementContext.updateLatestOrderSid(amendRejected.orderSid());
        }

        @Override
        public void receiveExpired(DirectBuffer buffer, int offset, OrderExpiredSbeDecoder recovered) {
            // This expired should be sent back to OMES such that
            // exposure and position can be adjusted (same as regular flow)
        	int orderSid = recovered.orderSid();
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());

            if (!recoveredOrdSids.add(orderSid)){
            	LOG.warn("Skip processing order expired with an orderSid that has already been used in the system. [{}]", OrderExpiredDecoder.decodeToString(recovered));
            	return;
            }

			LOG.info("Processing recovery order expired [orderSid:{}, side:{}, secSid:{}, price:{}, leavesQty:{}, cumulativeQty:{}]", 
					recovered.orderSid(), recovered.side().name(), recovered.secSid(), recovered.price(), recovered.leavesQty(), recovered.cumulativeQty());

        	long secSid = recovered.secSid();
        	int price = recovered.price();
        	int quantity = recovered.cumulativeQty() + recovered.leavesQty();
        	SecurityLevelInfo securityLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
			NewOrderRequest request = NewOrderRequest.ofWithDefaults(ServiceConstant.NULL_CLIENT_ORDER_REQUEST_ID,
					messenger.referenceManager().deadLetters(),
					secSid,
					OrderType.LIMIT_ORDER /* Just a guess */,
					quantity,
					recovered.side(),
					TimeInForce.DAY /* Another guess */,
					BooleanType.FALSE /* Guess */,
					price,
					price,
					Long.MAX_VALUE,
					false,
					-1)
					.securityLevelInfo(securityLevelInfo);
			request.orderSid(recovered.orderSid());
			orderManagementContext.putOrderRequest(recovered.orderSid(), request);
			if (request.side() == Side.BUY){
			    long notional = (long)price * quantity;
			    orderManagementContext.exposure().decPurchasingPower(notional);
//				securityLevelInfo.validationOrderBook().newBuyOrder(price);
			}
//			else {
//				securityLevelInfo.validationOrderBook().newSellOrder(price, quantity);
//			}
		    exposureUpdateHandler.updateValidationBook(request.side(), secSid, price, quantity);
			orderManagementContext.exposureAfterRecoveryUpdatedTime(messenger.timerService().toNanoOfDay());
		}

        @Override
        public void receiveTradeCreated(DirectBuffer buffer, int offset, TradeCreatedSbeDecoder recovered) {
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
            // This trade created should be sent back to OMES such that
            // exposure and position can be adjusted (same as regular flow)
			LOG.info("Processing recovery trade created [orderSid:{}, side:{}, secSid:{}, execPrice:{}, executionQty:{}]", 
					recovered.orderSid(), recovered.side().name(), recovered.secSid(), recovered.executionPrice(), recovered.executionQty());
        }

        @Override
        public void receiveTradeCancelled(DirectBuffer buffer, int offset, TradeCancelledSbeDecoder recovered) {
            orderManagementContext.updateLatestOrderSid(recovered.orderSid());
            // This trade cancelled should be sent back to OMES such that
            // exposure and position can be adjusted (same as regular flow)
			LOG.info("Processing recovery trade cancelled [{}]", TradeCancelledDecoder.decodeToString(recovered));
        }
	    
	};
	
	/**
	 * 7.6.7.1 Order Accepted
	 * The OCG will send this execution report once the new order (board lot or odd lot/special lot)
	 * is accepted.
	 * @param accepted
	 */
	@Override
	public void receiveAccepted(DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
//		LOG.info("Received order accepted [orderSid:{}]", accepted.orderSid());
		// clientOrderId is our internal system generate ID
		int orderSid = accepted.orderSid();
		try {
		    currentRecoveryHandler.receiveAccepted(buffer, offset, accepted);
		    
			// Sending order request completion back to OMES
			// OMES will forward the order request to client
			// Why don't we send completion to both omes and order request owner at the same time?
			// Because, it makes the flow more 'sane'...
			currentOrdReqCompletionHandler.completeWithOrdSidOnly(orderSid);

			NewOrderRequest request = orderManagementContext.getOrderRequest(orderSid).asNewOrderRequest();
			OrderContext context = OrderContext.of(orderUpdateDistributor, request.securityLevelInfo().channel());
			OrderContext existingContext = orderContextByOrdSid.put(orderSid, context);
			context.onOrderAcceptedAsFirstUpdate(request, buffer, offset, accepted, timerService.toNanoOfDay());
			if (existingContext != orderContextByOrdSid.defaultReturnValue()){
				LOG.error("Received order accepted for an existing order, discard existing order [orderSid:{}, discarded:{}, replacedBy:{}]", 
						accepted.orderSid(),
						existingContext.order(),
						OrderAcceptedDecoder.decodeToString(accepted));
			}
			currentOrdReqCompletionHandler.receivedFromExchange(request);
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving status for clientOrderId: " + orderSid, e);
			handleBuggyOrderUpdate(orderSid);
		}
		finally {
		}
	}

	/**
	 * 7.6.7.2 Order Rejected
	 * The OCG will send this execution report once the new order (board lot or odd lot/special lot)
	 * is rejected.
	 * @param reject
	 */
	@Override
	public boolean receiveRejected(DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected) {
		// clientOrderId is our internal system generate ID
		int orderSid = rejected.orderSid();
		try {
			if (currentRecoveryHandler.receiveRejected(buffer, offset, rejected)){
				rejected.getReason(messenger.stringByteBuffer(), 0);
				
				currentOrdReqCompletionHandler.rejectWithOrdSidOnly(orderSid, 
						OrderRequestRejectTypeConverter.from(rejected.rejectType()),
						messenger.stringByteBuffer());
				
				OrderContext context = orderContextByOrdSid.get(orderSid);

				// Most of the time, context hasn't been created at this point
				// So we can safely assume that the jvm will be optimized to handle 
				// this condition
				if (context == null){
					NewOrderRequest request = orderManagementContext.getOrderRequest(orderSid).asNewOrderRequest();
					context = OrderContext.of(orderUpdateDistributor, request.securityLevelInfo().channel());
					orderContextByOrdSid.put(orderSid, context);
					
					String reason = new String(messenger.stringByteBuffer());
					context.onOrderRejectedAsFirstUpdate(request, buffer, offset, rejected, reason);
					currentOrdReqCompletionHandler.receivedFromExchange(request);
				}
				else{
					context.onOrderRejected(buffer, offset, rejected);
				}
			}
			return true;
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving status for clientOrderId: " + orderSid, e);
			handleBuggyOrderUpdate(rejected.orderSid());
			return false;
		}
		finally {
		}
	}

	/**
	 * 7.6.7.3 Order Cancelled
	 * The OCG sends this execution report once the Cancel Request for an order (board lot or odd
	 * lot/special Lot) is accepted.
	 * 
	 * This is a message that comes back after a CancelOrderRequest.
	 * 
	 * @param cancelled
	 */
	@Override
	public void receiveCancelled(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		int origOrderSid = cancelled.origOrderSid();
		try {
		    currentRecoveryHandler.receiveCancelled(buffer, offset, cancelled);
		    OrderContext context = orderContextByOrdSid.get(origOrderSid);
		    if (context == null){
		        throw new IllegalStateException("Receive cancelled for an order that does not exist [orderSid:" + origOrderSid + "]");
		    }
		    
		    Integer cancelRequestOrderSid = orderManagementContext.origOrdSidToCancelOrderSid().get(origOrderSid);
		    if (cancelRequestOrderSid != null){
//		        LOG.debug("Detected cancelled from a cancel request");
                currentOrdReqCompletionHandler.completeWithOrdSidOnly(cancelRequestOrderSid);
                // Actually, nothing needs to be done with the previous CancelOrderRequest
                context.onOrderCancelled(buffer, 
                        offset, 
                        cancelled,
                        cancelRequestOrderSid);
		    }
		    else {
//		        LOG.debug("Detected cancelled that will be converted into OrderCancelled");
                if (context != null){
                    context.onOrderCancelled(buffer, offset, cancelled, origOrderSid);
                }
                // Leave this here for now
//                else {
//                    NewOrderRequest request = orderManagementContext.getOrderRequest(origOrderSid).asNewOrderRequest();
//                    context = OrderContext.of(orderUpdateDistributor, request.securityLevelInfo().channel());
//                    orderContextByOrdSid.put(origOrderSid, context);
//                    context.onOrderExpiredAsFirstUpdate(request, buffer, offset, cancelled);
//                }
		    }
		} 
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving status [orderSid: {}, origOrderSid:{}]", cancelled.orderSid(), origOrderSid, e);
			handleBuggyOrderUpdate(origOrderSid);
		}
		finally {
		}
	}

	@Override
	public void receiveCancelledOnBehalfOf(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
	    currentRecoveryHandler.receiveCancelledOnBehalfOf(buffer, offset, cancelled);
		throw new UnsupportedOperationException("CancelledOnBehalfOf is currently not supported");
	}
	
	@Override
	public void receiveCancelledUnsolicited(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
	    currentRecoveryHandler.receiveCancelledUnsolicited(buffer, offset, cancelled);
		throw new UnsupportedOperationException("receiveCancelledUnsolicited is currently not supported");
	}
	
	/**
	 * 7.6.7.6 Order Expired
	 * The OCG will send this execution report when an order (board lot or odd lot/special lot)
	 * expires.
	 * 
	 * Original Client Order ID is not provided in this message.
	 * 
	 * @param expired
	 */
	@Override
	public void receiveExpired(DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired){
		int orderSid = expired.orderSid();
		try {
		    currentRecoveryHandler.receiveExpired(buffer, offset, expired);
			OrderContext context = orderContextByOrdSid.get(orderSid);
			if (context != null){
				context.onOrderExpired(buffer, offset, expired);
			}
			else {
				NewOrderRequest request = orderManagementContext.getOrderRequest(orderSid).asNewOrderRequest();
				context = OrderContext.of(orderUpdateDistributor, request.securityLevelInfo().channel());
				orderContextByOrdSid.put(orderSid, context);
				context.onOrderExpiredAsFirstUpdate(request, buffer, offset, expired);
				currentOrdReqCompletionHandler.receivedFromExchange(request);
//				processWithArchivedOrderContext(expired);
			}
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving expired for clientOrderId: " + orderSid, e);
			handleBuggyOrderUpdate(orderSid);
		}
	}
	
	/**
	 * 7.6.7.7 Order Amended
	 * The OCG sends this execution report when an Order Amend request for an order (board lot)
	 * is accepted.
	 * @param amended
	 */
	@Override
	public void receiveAmended(DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended) {
		int orderSid = amended.orderSid();
		int origOrderSid = amended.origOrderSid();
		try {
		    currentRecoveryHandler.receiveAmended(buffer, offset, amended);
			OrderContext context = orderContextByOrdSid.get(origOrderSid);
			if (context != null){
				context.onOrderAmended(orderManagementContext.getOrderRequest(amended.orderSid()).asAmendOrderRequest(), buffer, offset, amended);
			}
			else {
				processWithArchivedOrderContext(amended);
			}
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving amended for clientOrderId: " + origOrderSid, e);
			handleBuggyOrderUpdate(origOrderSid);
		}
		finally {
			currentOrdReqCompletionHandler.completeWithOrdSidOnly(orderSid);
		}
	}
	
	/**
	 * 7.6.7.8 Order Cancel Rejected
	 * The OCG sends this execution report when an Order Cancel Request is rejected.
	 * 
	 * Original Client Order ID is not mandatory
	 * 
	 * @param cancelRejected
	 */
	@Override
	public void receiveCancelRejected(DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected){
		int orderSid = cancelRejected.orderSid();
		try {
		    currentRecoveryHandler.receiveCancelRejected(buffer, offset, cancelRejected);
			// Find request first to look up the actual order
			// because cancelReject message may not have Client Order ID
			currentOrdReqCompletionHandler.rejectWithOrdSidOnly(orderSid, 
					OrderRequestRejectTypeConverter.from(cancelRejected.cancelRejectType()),
					OrderSender.ORDER_CANCEL_REJECTED_EMPTY_REASON);

			OrderRequest item = orderManagementContext.getOrderRequest(orderSid);
			CancelOrderRequest cancelOrderRequest = item.asCancelOrderRequest();
			
			// Look for order context of order that we want to cancel
			if (cancelRejected.cancelRejectType() != OrderCancelRejectType.UNKNOWN_ORDER){
				OrderContext context = orderContextByOrdSid.get(cancelOrderRequest.ordSidToBeCancelled());
				if (context != null){
					context.onOrderCancelRejected(cancelOrderRequest, buffer, offset, cancelRejected);
				}
				else {
					processWithArchivedOrderContext(cancelRejected);
				}
			}
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving cancel rejected for clientOrderId: " + orderSid, e);
			handleBuggyOrderUpdate(orderSid);
		}
		finally {
		}
	}
	
	/**
	 * 7.6.7.9 Order Amend Rejected
	 * The OCG sends this execution report when an Order Amend Request is rejected.
	 * @param amendRejected
	 */
	@Override
	public void receiveAmendRejected(DirectBuffer buffer, int offset, OrderAmendRejectedSbeDecoder amendRejected){
		int orderSid = amendRejected.orderSid();
		try {
		    currentRecoveryHandler.receiveAmendRejected(buffer, offset, amendRejected);
		    
			currentOrdReqCompletionHandler.rejectWithOrdSidOnly(orderSid, 
					OrderRequestRejectType.OTHER,
					OrderSender.ORDER_REJECTED_EMPTY_REASON);

			OrderRequest item = orderManagementContext.getOrderRequest(orderSid);
			AmendOrderRequest amendOrderRequest = item.asAmendOrderRequest();
			OrderContext context = orderContextByOrdSid.get(amendOrderRequest.orderSid());
			if (context != null){
				context.onOrderAmendRejected(amendOrderRequest, buffer, offset, amendRejected);
			}
			else {
				processWithArchivedOrderContext(amendRejected);
			}
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving cancel rejected for clientOrderId: " + orderSid, e);
			handleBuggyOrderUpdate(orderSid);
		}
		finally {
		}
	}
	
	/**
	 * 7.6.7.10
	 * Trade (Board lot Order Executed)
	 * The OCG sends this execution report for an auto-matched trade.
	 * @param trade
	 */
	@Override
	public void receiveTradeCreated(DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade){
		int orderSid = trade.orderSid();
//		LOG.info("received trade");
		try {
		    currentRecoveryHandler.receiveTradeCreated(buffer, offset, trade);
			OrderContext context = orderContextByOrdSid.get(orderSid);
			
			// It is possible to receive just a Trade update instead of an OrderAccepeted first for 
			// the following scenarios:
			// 1) Fill or Kill
			// 2) Immediate or Cancel
			if (context != null){
				context.onTradeCreated(buffer, offset, trade, tradeSidGenerator.getAndIncrement());
			}
			else {
				NewOrderRequest request = orderManagementContext.getOrderRequest(orderSid).asNewOrderRequest();
				context = OrderContext.of(orderUpdateDistributor, request.securityLevelInfo().channel());
				orderContextByOrdSid.put(orderSid, context);
				context.onTradeCreatedAsFirstUpdate(request, buffer, offset, trade, tradeSidGenerator.getAndIncrement());
//				processWithArchivedOrderContext(trade);
			}
		}
		catch (Exception e){
			LOG.error("Caught unexcepted exception after receiving trade for clientOrderId: " + orderSid, e);
			handleBuggyOrderUpdate(orderSid);
		}		
	}


	@Override
	public void receiveTradeCancelled(DirectBuffer buffer, int offset, TradeCancelledSbeDecoder tradeCancelled) {
		throw new UnsupportedOperationException("receiveTradeCancelled is not supported");
	}

	public OrderUpdateHandler orderUpdateDistributor(){
		return orderUpdateDistributor;
	}
	
	private final OrderUpdateHandler orderUpdateDistributor = new OrderUpdateHandler() { 

		@Override
		public void onAccepted(OrderContext orderContext, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
			// send order accepted to order owner and order update subscribers
			int encodedLength = sender.sendOrderAccepted(orderContext.order().ownerSinkRef(), 
					orderContext.channel().id(),
					orderContext.channel().getAndIncrementSeq(),
					buffer,
					offset,
					accepted,
					messenger.internalEncodingBuffer(),
					0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver accepted message to at least one of the subscribers [orderSid:{}]", accepted.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver accepted message to order owner [ownerSinkId:{}, orderSid:{}]", orderContext.order().ownerSinkId(), accepted.orderSid());
		}

		@Override
		public void onAcceptedAsFirstUpdate(OrderContext orderContext, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
			// send order accepted to order owner and order update subscribers
			int encodedLength = sender.sendOrderAcceptedWithOrderInfo(orderContext.order().ownerSinkRef(), 
					orderContext.channel().id(),
					orderContext.channel().getAndIncrementSeq(),
					buffer, 
					offset, 
					accepted,
					orderContext.order(), 
					messenger.internalEncodingBuffer(), 
					0);

			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver accepted-as-first-update message to at least one of the subscribers [orderSid:{}]", accepted.orderSid());
				}
				return;
			}		
			LOG.warn("Could not deliver accepted-as-first-update message to order owner [ownerSinkId:{}, orderSid:{}]", orderContext.order().ownerSinkId(), accepted.orderSid());
		}

		@Override
		public void onAmended(OrderContext context, DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended) {
			// send order amended to order owner and order update subscribers
			int encodedLength = sender.sendOrderAmended(context.order().ownerSinkRef(), orderManagementContext.orderUpdateSubscribers(), buffer, offset, amended, messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver amended message to at least one of the subscribers [orderSid:{}]", amended.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver amended message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), amended.orderSid());
		}

		@Override
		public void onRejected(OrderContext context, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected) {
			// No need to send order rejected to subscribers
			// Actually, there is also no need to send order rejected message to order owner because
			// the owner will receive this as OrderRequestCompletion - Rejected
			int encodedLength = sender.sendOrderRejected(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					rejected, 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver rejected message to at least one of the subscribers [orderSid:{}]", rejected.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver rejected message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), rejected.orderSid());			
			archivedOrderContextBySid.put(rejected.orderSid(), orderContextByOrdSid.remove(rejected.orderSid()));
		}

		@Override
		public void onRejectedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected) {
			// Really there is no need to send order rejected message to order owner because
			// the owner will receive this as OrderRequestCompletion - Rejected
			int encodedLength = sender.sendOrderRejectedWithOrderInfo(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset, 
					rejected, 
					context.order(), 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver rejected-as-first-update message to at least one of the subscribers [orderSid:{}]", rejected.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver rejected-as-first-update message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), rejected.orderSid());
			archivedOrderContextBySid.put(rejected.orderSid(), orderContextByOrdSid.remove(rejected.orderSid()));
		}

		@Override
		public void onCancelled(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
			int encodedLength = sender.sendOrderCancelledWithOrderQuantityOverride(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					cancelled, 
					context.order(),
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver cancelled message to at least one of the subscribers [orderSid:{}]", cancelled.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver cancelled message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelled.orderSid());
			archivedOrderContextBySid.put(cancelled.orderSid(), orderContextByOrdSid.remove(cancelled.orderSid()));
		}

        @Override
        public void onCancelled(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled, int overrideOrderSid) {
		    // Send to OMES first then to owner and other subscribers
		    long encodedLength = sender.sendOrderCancelledWithOrderQuantityOverride(orderManagementContext.orderUpdateSubscribers(),
		            orderManagementContext.orderUpdateSubscribers().length,
		            context.channel().id(),
		            context.channel().getAndIncrementSeq(),
		            buffer, 
		            offset,
		            cancelled, 
		            context.order(),
		            sinkSendResults);

		    if (encodedLength > 0){
		        long result =  messenger.trySend(context.order().ownerSinkRef().sinkId(), messenger.internalEncodingBuffer(), 0, (int)encodedLength);
                if (result != MessageSink.OK){
                    LOG.warn("Could not deliver order cancelled message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelled.orderSid());
                }
                return;
		    }
            LOG.warn("Could not deliver order cancelled message to at least one of the subscribers [orderSid:{}]", cancelled.orderSid());
//            archivedOrderContextBySid.put(cancelled.orderSid(), orderContextByOrdSid.remove(cancelled.orderSid()));
        }

        @Override
		public void onCancelledAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
			int encodedLength = sender.sendOrderCancelledWithOrderInfo(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),				
					buffer, 
					offset,
					cancelled, 
					context.order(), messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver cancelled-as-first-update message to at least one of the subscribers [orderSid:{}]", cancelled.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver cancelled-as-first-update message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelled.orderSid());
			archivedOrderContextBySid.put(cancelled.orderSid(), orderContextByOrdSid.remove(cancelled.orderSid()));
		}

		@Override
		public void onCancelRejected(OrderContext context, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected) {
			int encodedLength = sender.sendOrderCancelRejected(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					cancelRejected, 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver cancel rejected message to at least one of the subscribers [orderSid:{}]", cancelRejected.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver cancel rejected message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelRejected.orderSid());
			archivedOrderContextBySid.put(cancelRejected.orderSid(), orderContextByOrdSid.remove(cancelRejected.orderSid()));
		}

		@Override
		public void onCancelRejectedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected) {
			int encodedLength = sender.sendOrderCancelRejectedWithOrderInfo(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					cancelRejected, 
					context.order(),
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver cancel rejected message to at least one of the subscribers [orderSid:{}]", cancelRejected.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver cancel rejected message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelRejected.orderSid());
			archivedOrderContextBySid.put(cancelRejected.orderSid(), orderContextByOrdSid.remove(cancelRejected.orderSid()));
		}

		@Override
		public void onTradeCreated(OrderContext context, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int tradeSid) {
		    // Send to OMES first then to owner and other subscribers
		    long encodedLength = sender.sendTradeCreated(orderManagementContext.orderUpdateSubscribers(),
		            orderManagementContext.orderUpdateSubscribers().length,
		            context.channel().id(),
		            context.channel().getAndIncrementSeq(),
		            buffer, 
		            offset,
		            trade, 
		            tradeSid,
		            sinkSendResults);
		    
		    if (encodedLength > 0){
		        long result =  messenger.trySend(context.order().ownerSinkRef().sinkId(), messenger.internalEncodingBuffer(), 0, (int)encodedLength);
                if (result != MessageSink.OK){
                    LOG.warn("Could not deliver trade message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), trade.orderSid());
                }
                return;
		    }
            LOG.warn("Could not deliver trade message to at least one of the subscribers [orderSid:{}]", trade.orderSid());
		}

		@Override
		public void onTradeCreatedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int tradeSid) {
			int encodedLength = sender.sendTradeCreatedWithOrderInfo(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					trade, 
					tradeSid,
					context.order(), 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver trade-as-first-update message to at least one of the subscribers [orderSid:{}]", trade.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver trade-as-first-update message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), trade.orderSid());
		}

		@Override
		public void onExpired(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
			int encodedLength = sender.sendOrderExpired(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					cancelled, 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver expired (cancelled) message to at least one of the subscribers [orderSid:{}]", cancelled.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver expired (cancelled) message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelled.orderSid());
		}

		@Override
		public void onExpired(OrderContext context, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired) {
			int encodedLength = sender.sendOrderExpired(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset,
					expired, 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver expired message to at least one of the subscribers [orderSid:{}]", expired.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver expired message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), expired.orderSid());
			archivedOrderContextBySid.put(expired.orderSid(), orderContextByOrdSid.remove(expired.orderSid()));		
		}

		@Override
		public void onExpiredAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired) {
			int encodedLength = sender.sendOrderExpiredWithOrderInfo(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset, 
					expired, context.order(), messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver expired-as-first-update message to at least one of the subscribers [orderSid:{}]", expired.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver expired-as-first-update message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), expired.orderSid());
			archivedOrderContextBySid.put(expired.orderSid(), orderContextByOrdSid.remove(expired.orderSid()));		
		}
		
		@Override
		public void onExpiredAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
			int encodedLength = sender.sendOrderExpiredWithOrderInfo(context.order().ownerSinkRef(), 
					context.channel().id(),
					context.channel().getAndIncrementSeq(),
					buffer, 
					offset, 
					cancelled, 
					context.order(), 
					messenger.internalEncodingBuffer(), 0);
			if (encodedLength > 0){
				long result = messenger.trySend(orderManagementContext.orderUpdateSubscribers(), messenger.internalEncodingBuffer(), 0, encodedLength, sinkSendResults);
				if (result != MessageSink.OK){
					LOG.warn("Could not deliver expired-as-first-update (cancelled) message to at least one of the subscribers [orderSid:{}]", cancelled.orderSid());
				}
				return;
			}
			LOG.warn("Could not deliver expired-as-first-update (cancelled) message to order owner [ownerSinkId:{}, orderSid:{}]", context.order().ownerSinkId(), cancelled.orderSid());
		}
		
	};

	/**
	 * Sample implementation
	 * 
	 * 1. Archive service should be some off memory persistence
	 * 2. Retrieve it using a separate thread
	 * 3. Once retrieved, send a special message to recreate it in {@link orderContextBySid}, follow by
	 *    the accepted message
	 * 4. If the Client Order ID is not valid, log an error 
	 * 
	 * @param accepted
	 */
	@SuppressWarnings("unused")
	private void processWithArchivedOrderContext(OrderCancelledSbeDecoder cancelled){
		LOG.error("Received order cancelled message for an order that does not exist in our map.  This looks like a bug.  {}", OrderCancelledDecoder.decodeToString(cancelled));		
	}
	
	@SuppressWarnings("unused")
	private void processWithArchivedOrderContext(OrderExpiredSbeDecoder expired){
		LOG.error("Received order expired message for an order that does not exist in our map.  This looks like a bug.  {}", OrderExpiredDecoder.decodeToString(expired));		
	}

	private void processWithArchivedOrderContext(OrderAmendedSbeDecoder amended){
		LOG.error("Received order amended message for an order that does not exist in our map.  This looks like a bug.  {}", OrderAmendedDecoder.decodeToString(amended));		
	}

	private void processWithArchivedOrderContext(OrderCancelRejectedSbeDecoder cancelRejected){
		LOG.error("Received order cancel rejected message for an order that does not exist in our map.  This looks like a bug.  {}", OrderCancelRejectedDecoder.decodeToString(cancelRejected));		
	}

	private void processWithArchivedOrderContext(OrderAmendRejectedSbeDecoder amendRejected){
		LOG.error("Received order amend rejected message for an order that does not exist in our map.  This looks like a bug.  {}", OrderAmendRejectedDecoder.decodeToString(amendRejected));		
	}

	@SuppressWarnings("unused")
	private void processWithArchivedOrderContext(TradeSbeDecoder trade){
		LOG.error("Received trade message for an order that does not exist in our map.  This looks like a bug.  {}", TradeDecoder.decodeToString(trade));		
	}

	private void handleBuggyOrderUpdate(int clientOrderId){
		LOG.error("Received buggy order update with clientOrderId:{}", clientOrderId);
	}

	/**
	 * Synchronous non-blocking call 
	 * @return
	 */
	public boolean reset() {
		orderContextByOrdSid.clear();
		archivedOrderContextBySid.clear();
		recoveredOrdSids.clear();
		return true;
	}
	
	public boolean isClear(){
	    return orderContextByOrdSid.isEmpty() && archivedOrderContextBySid.isEmpty(); 
	}
	
	public boolean recover(){
	    LOG.info("Change to recovery");
	    
	    if (ServiceConstant.SHOULD_OCG_PERFORM_RECOVERY){
	    	LOG.info("Changed handler to process recovered messages");
		    this.currentRecoveryHandler = recoveryHandler;	    	
	    }
	    else{
	    	LOG.info("Changed handler to skip recovered messages");
		    this.currentRecoveryHandler = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;	    	
	    }
	    
	    this.currentOrdReqCompletionHandler = OrderRequestCompletionHandler.NULL_HANDLER;
	    return true;
	}
	
	public boolean active(){
	    LOG.info("Change to active");
	    this.currentRecoveryHandler = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;
	    this.currentOrdReqCompletionHandler = this.activeOrdReqCompletionHandler;
	    return true;
	}

    public boolean warmup() {
        LOG.info("Change to warmup");
        return true;
    }
}
