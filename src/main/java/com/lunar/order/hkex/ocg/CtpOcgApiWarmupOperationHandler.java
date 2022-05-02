package com.lunar.order.hkex.ocg;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.MatchingEngineOrderUpdateHandler;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.order.Trade;
import com.lunar.order.UserControlledMatchingEngine;
import com.lunar.order.hkex.ocg.CtpOcgApi.CallbackHandler;

public class CtpOcgApiWarmupOperationHandler implements CtpOcgApi.MoreOperationHandler {
    @SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(CtpOcgApiWarmupOperationHandler.class);

	private final UserControlledMatchingEngine engine;
	private final CallbackHandler handler;
	private final AtomicInteger cancelOrderSeq = new AtomicInteger(110000000);

	public static CtpOcgApiWarmupOperationHandler of(String name, TimerService timerService, CallbackHandler handler){
		return new CtpOcgApiWarmupOperationHandler(name, timerService, handler);
	}
	
	CtpOcgApiWarmupOperationHandler(String name, TimerService timerService, CallbackHandler handler){
		this.engine = UserControlledMatchingEngine.of(name, timerService);
        this.handler = handler;
		this.engine.init(orderUpdateHandler);
	}

	@Override
	public void afterAddOrderSent(int result, long nanoOfDay, int clientOrderId, long secSid, int side, int orderType, int timeInForce, int price, int quantity, boolean isEnhanced) {
	    // Add order into engine
	    int clientKey = clientOrderId;
	    NewOrderRequest orderRequest = NewOrderRequest.ofWithDefaults(clientKey, 
	            MessageSinkRef.NA_INSTANCE, 
	            secSid, 
	            CtpUtil.convertOrderType(orderType, isEnhanced), 
	            quantity, 
	            CtpUtil.convertSide(side), 
	            CtpUtil.convertTif(timeInForce),
	            BooleanType.FALSE, 
	            price, 
	            price, 
	            -1l,
	            false,
	            -1);
	    orderRequest.orderSid(clientKey);
	    engine.sendOrderRequest(orderRequest);
	}

	@Override
	public void afterCancelOrderSent(int result, long nanoOfDay, String clientOrderId, long secSid, int side) {
		// clientOrderId is our orderSid
	    int clientKey = Integer.parseInt(clientOrderId);
	    CancelOrderRequest orderRequest = CancelOrderRequest.of(cancelOrderSeq.getAndIncrement(), 
	            MessageSinkRef.NA_INSTANCE, 
	            clientKey, 
	            secSid, 
	            CtpUtil.convertSide(side));
	    orderRequest.orderSid(clientKey);
	    engine.sendOrderRequest(orderRequest);
	}

	@Override
	public void afterMassCancelAllSent(int result, long nanoOfDay) {
	    throw new UnsupportedOperationException("Mass cancel all");
	}

	@Override
	public void afterMassCancelBySecuritySent(int result, long nanoOfDay, long secSid) {
        throw new UnsupportedOperationException("Mass cancel by security");
	}

	@Override
	public void afterMassCancelBySideSent(int result, long nanoOfDay, int side) {
        throw new UnsupportedOperationException("Mass cancel by side");
	}

	@Override
	public int apply(Command command) {
		return engine.apply(command);
	}

	private MatchingEngineOrderUpdateHandler orderUpdateHandler = new MatchingEngineOrderUpdateHandler() {
        
        @Override
        public void onTradeCreated(Order order, Trade trade) {
            handler.onExecutionReport(0,
                    order.sid(), 
                    String.valueOf(order.orderId()), 
                    trade.createTime(), 
                    -1, 
                    -1, 
                    CtpUtil.convertOrderStatus(order.status()), 
                    trade.secSid(), 
                    CtpUtil.convertSide(trade.side()),
                    order.limitPrice(),
                    order.quantity(),
                    trade.cumulativeQty(),
                    trade.leavesQty(),
                    trade.executionId(), 
                    trade.executionQty(),
                    trade.executionPrice(), 
                    order.reason(),
                    false);
        }
        
        @Override
        public void onOrderRejected(Order order) {
            handler.onExecutionReport(0,
                    order.sid(), 
                    String.valueOf(order.orderId()), 
                    order.updateTime(), 
                    -1 /* ExecTypeCode */, 
                    -1 /* ExecTransTypeCode */, 
                    CtpUtil.convertOrderStatus(order.status()), 
                    order.secSid(),
                    CtpUtil.convertSide(order.side()),
                    order.limitPrice(),
                    order.quantity(),
                    order.cumulativeExecQty(),
                    order.leavesQty(),
                    null /* execution id */,
                    -1 /* trade quantity */,
                    order.limitPrice(), 
                    order.reason(),
                    false);
        }
        
        @Override
        public void onOrderExpired(Order order) {
            handler.onExecutionReport(0,
                    order.sid(), 
                    String.valueOf(order.orderId()), 
                    order.updateTime(), 
                    -1 /* ExecTypeCode */, 
                    -1 /* ExecTransTypeCode */, 
                    CtpUtil.convertOrderStatus(order.status()), 
                    order.secSid(),
                    CtpUtil.convertSide(order.side()),
                    order.limitPrice(),
                    order.quantity(),
                    order.cumulativeExecQty(),
                    order.leavesQty(),
                    null /* execution id */,
                    -1 /* trade quantity */,
                    order.limitPrice(), 
                    order.reason(),
                    false);
        }
        
        @Override
        public void onOrderCancelled(CancelOrderRequest request, Order order) {
            handler.onExecutionReport(0,
                    order.sid(), 
                    String.valueOf(order.orderId()), 
                    order.updateTime(), 
                    -1 /* ExecTypeCode */, 
                    -1 /* ExecTransTypeCode */, 
                    CtpUtil.convertOrderStatus(order.status()), 
                    order.secSid(),
                    CtpUtil.convertSide(order.side()),
                    order.limitPrice(),
                    order.quantity(),
                    order.cumulativeExecQty(),
                    order.leavesQty(),
                    null /* execution id */,
                    -1 /* trade quantity */,
                    order.limitPrice(), 
                    order.reason(),
                    false);
        }
        
        @Override
        public void onOrderCancelled(Order order) {
            handler.onExecutionReport(0,
                    order.sid(), 
                    String.valueOf(order.orderId()), 
                    order.updateTime(), 
                    -1 /* ExecTypeCode */, 
                    -1 /* ExecTransTypeCode */, 
                    CtpUtil.convertOrderStatus(order.status()), 
                    order.secSid(),
                    CtpUtil.convertSide(order.side()),
                    order.limitPrice(),
                    order.quantity(),
                    order.cumulativeExecQty(),
                    order.leavesQty(),
                    null /* execution id */,
                    -1 /* trade quantity */,
                    order.limitPrice(), 
                    order.reason(),
                    false);
        }
        
        @Override
        public void onOrderAccepted(Order order) {
            handler.onExecutionReport(0,
                    order.sid(), 
                    String.valueOf(order.orderId()), 
                    order.createTime(), 
                    -1 /* ExecTypeCode */, 
                    -1 /* ExecTransTypeCode */, 
                    CtpUtil.convertOrderStatus(order.status()), 
                    order.secSid(), 
                    CtpUtil.convertSide(order.side()),
                    order.limitPrice(),
                    order.quantity(),
                    order.cumulativeExecQty(),
                    order.leavesQty(),
                    null /* execution id */,
                    -1 /* trade quantity */,
                    order.limitPrice(), 
                    order.reason(),
                    false);
        }
        
        @Override
        public void onCancelRejected(CancelOrderRequest request, OrderCancelRejectType rejectType, String reason, Order order) {
            handler.onExecutionReport(0,
                    request.ordSidToBeCancelled(),
                    null,
                    -1 /* time */, 
                    -1, 
                    -1, 
                    CtpOcgApi.UNKNOWN, 
                    request.secSid(), 
                    CtpUtil.convertSide(request.side()), 
                    -1,
                    -1,
                    -1 /* cumulative quantity */, 
                    -1 /* leaves quantity */, 
                    null /* execution id */,
                    -1 /* quantity */, 
                    -1 /* price */, 
                    reason, 
                    false);
        }

		@Override
		public void onEndOfRecovery() {
			// no need to pass back
		}
    };
}
