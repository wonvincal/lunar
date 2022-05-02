package com.lunar.strategy;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.SubscriberList;
import com.lunar.core.SystemClock;
import com.lunar.core.TriggerInfo;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;

import static org.apache.logging.log4j.util.Unbox.box;

public class NormalStrategyOrderService implements StrategyOrderService {
    private static final Logger LOG = LogManager.getLogger(NormalStrategyOrderService.class);

    private static final long ORDER_RETRY_LONG_TIMEOUT = 10_000_000_000L;
    private static final long ORDER_RETRY_SMALL_TIMEOUT = 150_000_000L;
    protected static final int FAKE_PORT_SID = 1000;

    private final Messenger messenger;
    private final SystemClock systemClock;
    private final TriggerInfo triggerInfo;
    private final StrategyErrorHandler errorHandler;
    private final SubscriberList performanceSubscribers;
    private boolean internalRejectRetryFlag;

    public NormalStrategyOrderService(final Messenger messenger, final SystemClock systemClock, final TriggerInfo triggerInfo, final StrategyErrorHandler errorHandler, final SubscriberList performanceSubscribers) {
        this.messenger = messenger;
        this.systemClock = systemClock;
        this.triggerInfo = triggerInfo;
        this.errorHandler = errorHandler;
        this.performanceSubscribers = performanceSubscribers;
    }
    
    protected Messenger messenger() {
        return messenger;
    }
    
    protected CompletableFuture<OrderRequest> createBuyOrderFuture(StrategySecurity security, int price, long quantity, StrategyExplain explain, TriggerInfo triggerInfo) {
        final NewOrderRequest request = NewOrderRequest.of(messenger().getNextClientKeyAndIncrement(), 
                messenger().self(),
                security, 
                OrderType.ENHANCED_LIMIT_ORDER, 
                (int)quantity, 
                Side.BUY, 
                TimeInForce.FILL_AND_KILL, 
                BooleanType.TRUE, 
                price,
                price,
                Long.MAX_VALUE,
                false,
                security.assignedThrottleTrackerIndex(),
                FAKE_PORT_SID,
                true,
                triggerInfo);
        return messenger().sendNewOrder(messenger().referenceManager().omes(), request);
    }

    @Override
    public void buy(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
        final long timestamp = systemClock.timestamp();
        final byte triggeredBy = triggerInfo.triggeredBy();
        final int triggerSeqNum = triggerInfo.triggerSeqNum();
        final long triggerNanoOfDay = triggerInfo.triggerNanoOfDay();

        final CompletableFuture<OrderRequest> future = createBuyOrderFuture(security, price, quantity, explain, triggerInfo);
        future.whenComplete((r, b) -> {
            try {
                LOG.info("Sent order request: buy, secCode {}, ob seqNum {}, ob time {}, trigger seqNum {}", security.code(), box(security.orderBook().channelSeqNum()), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
//                if (triggeredBy > 0) {
//                    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_NEWORDERREQUEST, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
//                }
                if (r == null) {
                    LOG.warn("Received buy order request that is null for {}", security.code());
                }
                else {
                    explain.orderSid(r.orderSid());
                    explain.logExplainForBuyOrder();
                    messenger.eventSender().sendEventWithMultipleValues(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.INFO, EventType.ORDER_EXPLAIN, messenger.self().sinkId(), triggerNanoOfDay, "Buy order from strategy", explain.eventValueTypes(), explain.eventValues());
                    if (r.completionType() != OrderRequestCompletionType.OK) {
                        final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                        if (handler != null) {
                            LOG.info("Received buy order request rejected for {} - {} {}, {}", security.code(), r.completionType(), r.rejectType(), r.reason());
                            if (r.completionType().equals(OrderRequestCompletionType.FAILED)) {
                                LOG.error("Order request unexpectingly failed for {}", security.code());
                                errorHandler.onError(security.sid(), "Order request unexpectingly failed");
                            }
                            handler.onOrderStatusReceived(systemClock.nanoOfDay(), 0, 0, r.rejectType());
                        }
                    }
                    else {
                        LOG.info("Received buy order request ok for {}", security.code());
                    }                    
                }
            }
            catch (final Exception e) {
                LOG.error("Critical error encountered while handling sell-side order completion future for {}", security.code(), e);
                errorHandler.onError(security.sid(), "Critical error encountered while handling sell-side order completion future");
            }
        });
    }

    @Override
    public void sell(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
        final long timestamp = systemClock.timestamp();
        final byte triggeredBy = triggerInfo.triggeredBy();
        final int triggerSeqNum = triggerInfo.triggerSeqNum();
        final long triggerNanoOfDay = triggerInfo.triggerNanoOfDay();   

        security.updatePendingSell(quantity);
        final NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
                messenger.self(),
                security, 
                OrderType.ENHANCED_LIMIT_ORDER, 
                (int)quantity, 
                Side.SELL,
                TimeInForce.FILL_AND_KILL, 
                BooleanType.TRUE, 
                price, 
                price, 
                messenger.timerService().toNanoOfDay() + ORDER_RETRY_SMALL_TIMEOUT,
                true,
                security.assignedThrottleTrackerIndex(),
                FAKE_PORT_SID,
                !internalRejectRetryFlag,
                triggerInfo);
        final CompletableFuture<OrderRequest> future = messenger.sendNewOrder(messenger.referenceManager().omes(), request);
        future.whenComplete((r, b) -> {
            try {
                LOG.info("Sent order request: sell, secCode {}, ob seqNum {}, ob time {}, trigger seqNum {}", security.code(), box(security.orderBook().channelSeqNum()), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
//                if (triggeredBy > 0) {
//                    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_NEWORDERREQUEST, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
//                }
                if (r == null) {
                    LOG.warn("Received sell order request that is null for {}", security.code());
                }
                else {
                    explain.orderSid(r.orderSid());
                    explain.logExplainForSellOrder();
                    messenger.eventSender().sendEventWithMultipleValues(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.INFO, EventType.ORDER_EXPLAIN, messenger.self().sinkId(), triggerNanoOfDay, "Sell order from strategy", explain.eventValueTypes(), explain.eventValues());
                    if (r.completionType() != OrderRequestCompletionType.OK) {
                        final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                        if (handler != null) {
                            LOG.info("Received sell order request rejected for {} - {} {}, {}", security.code(), r.completionType(), r.rejectType(), r.reason());
                            security.updatePendingSell(-quantity);
                            if (r.completionType().equals(OrderRequestCompletionType.FAILED)) {
                                LOG.error("Order request unexpectingly failed for {}", security.code());
                                errorHandler.onError(security.sid(), "Order request unexpectingly failed");
                            }
                            //TODO temp change to not use throttle if reject from RMS reject
                            internalRejectRetryFlag = r.rejectType() == OrderRequestRejectType.RMS_INSUFFICIENT_LONG_POSITION_DIRECT_MAP;
                            handler.onOrderStatusReceived(systemClock.nanoOfDay(), 0, 0, r.rejectType());
                            internalRejectRetryFlag = false;
                        }
                    }
                    else {
                        LOG.info("Received sell order request ok for {}", security.code());
                    }
                }
            }
            catch (final Exception e) {
                LOG.error("Critical error encountered while handling sell-side order completion future for {}", security.code(), e);
                errorHandler.onError(security.sid(), "Critical error encountered while handling sell-side order completion future");
            }
        });
    }
    
    @Override
    public void sellLimit(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain) {
        final long timestamp = systemClock.timestamp();
        final byte triggeredBy = triggerInfo.triggeredBy();
        final int triggerSeqNum = triggerInfo.triggerSeqNum();
        final long triggerNanoOfDay = triggerInfo.triggerNanoOfDay();   

        security.updatePendingSell(quantity);
        final NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
                messenger.self(),
                security, 
                OrderType.LIMIT_ORDER, 
                (int)quantity, 
                Side.SELL,
                TimeInForce.DAY, 
                BooleanType.TRUE, 
                price,
                price,
                messenger.timerService().toNanoOfDay() + ORDER_RETRY_SMALL_TIMEOUT,
                true,
                security.assignedThrottleTrackerIndex(),
                FAKE_PORT_SID,
                true,
                triggerInfo);
        final CompletableFuture<OrderRequest> future = messenger.sendNewOrder(messenger.referenceManager().omes(), request);
        future.whenComplete((r, b) -> {
            try {
                LOG.info("Sent order request: sell, secCode {}, ob seqNum {}, ob time {}, trigger seqNum {}", security.code(), box(security.orderBook().channelSeqNum()), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
//                if (triggeredBy > 0) {
//                    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_NEWORDERREQUEST, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
//                }
                if (r == null) {
                    LOG.warn("Received sell order request that is null for {}", security.code());
                }
                else {
                    explain.orderSid(r.orderSid());
                    explain.logExplainForSellOrder();
                    messenger.eventSender().sendEventWithMultipleValues(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.INFO, EventType.ORDER_EXPLAIN, messenger.self().sinkId(), triggerNanoOfDay, "Sell order from strategy", explain.eventValueTypes(), explain.eventValues());
                    if (r.completionType() != OrderRequestCompletionType.OK) {
                        final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                        if (handler != null) {
                            LOG.info("Received sell limit order request rejected for {} - {} {}, {}", security.code(), r.completionType(), r.rejectType(), r.reason());
                            security.updatePendingSell(-quantity);
                        }
                    }
                    else {
                        security.limitOrderSid(r.orderSid());
                        security.limitOrderPrice(price);
                        security.limitOrderQuantity(quantity);
                        LOG.info("Received sell limit order request ok for {}", security.code());
                    }
                }
            }
            catch (final Exception e) {
                LOG.error("Critical error encountered while handling sell-side order completion future for {}", security.code(), e);
                errorHandler.onError(security.sid(), "Critical error encountered while handling sell-side order completion future");
            }
        });        
    }

    @Override
    public void sellToExit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
        final long timestamp = systemClock.timestamp();
        final byte triggeredBy = triggerInfo.triggeredBy();
        final int triggerSeqNum = triggerInfo.triggerSeqNum();
        final long triggerNanoOfDay = triggerInfo.triggerNanoOfDay();        

        security.updatePendingSell(quantity);
        final NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
                messenger.self(),
                security, 
                OrderType.ENHANCED_LIMIT_ORDER, 
                (int)quantity, 
                Side.SELL, 
                TimeInForce.FILL_AND_KILL, 
                BooleanType.TRUE, 
                price, 
                price, 
                messenger.timerService().toNanoOfDay() + ORDER_RETRY_LONG_TIMEOUT,
                true,
                security.assignedThrottleTrackerIndex(),
                FAKE_PORT_SID,
                true,
                triggerInfo);
        final CompletableFuture<OrderRequest> future = messenger.sendNewOrder(messenger.referenceManager().omes(), request);
        future.whenComplete((r, b) -> {
            try {
                LOG.info("Sent order request: sell, secCode {}, ob seqNum {}, ob time {}, trigger seqNum {}", security.code(), box(security.orderBook().channelSeqNum()), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
//                if (triggeredBy > 0) {
//                    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_NEWORDERREQUEST, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
//                }
                if (r == null) {
                    LOG.warn("Received sell order request that is null for {}", security.code());
                }
                else {
                    explain.orderSid(r.orderSid());
                    explain.logExplainForSellOrder();
                    messenger.eventSender().sendEventWithMultipleValues(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.INFO, EventType.ORDER_EXPLAIN, messenger.self().sinkId(), timestamp, "Sell order from strategy", explain.eventValueTypes(), explain.eventValues());
                    if (r.completionType() != OrderRequestCompletionType.OK) {
                        final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                        if (handler != null) {
                            LOG.info("Received sell order request rejected for {} - {} {}, {}", security.code(), r.completionType(), r.rejectType(), r.reason());
                            security.updatePendingSell(-quantity);
                            if (r.completionType().equals(OrderRequestCompletionType.FAILED)) {
                                LOG.error("Order request unexpectingly failed for {}", security.code());
                                errorHandler.onError(security.sid(), "Order request unexpectingly failed");
                            }
                            handler.onOrderStatusReceived(systemClock.nanoOfDay(), 0, 0, r.rejectType());
                        }
                    }
                    else {
                        LOG.info("Received sell order request ok for {}", security.code());
                    }
                }
            }
            catch (final Exception e) {
                LOG.error("Critical error encountered while handling sell-side order completion future for {}", security.code(), e);
                errorHandler.onError(security.sid(), "Critical error encountered while handling sell-side order completion future");
            }
        });
    }

    @Override
    public boolean canTrade() {
        throw new RuntimeException("canTrade not implemented");
    }

    @Override
    public void setBuyOrderType(final OrderType orderType) {
        throw new RuntimeException("setBuyOrderType not implemented");
    }

    @Override
    public void cancelAndSellOutstandingSell(StrategySecurity security, int price, StrategyExplain explain) {
        final long timestamp = systemClock.timestamp();
        if (security.limitOrderSid() > 0) {
	        final CancelOrderRequest request = CancelOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
	                messenger.self(),
	                security.limitOrderSid(),
	                security.sid(),
	                Side.SELL);
	        final CompletableFuture<OrderRequest> future = messenger.sendCancelOrder(messenger.referenceManager().omes(), request);
	        future.whenComplete((r, b) -> {
	            try {
	                LOG.info("Sent cancel order request: sell, secCode {}, ob seqNum {}, ob time {}, trigger seqNum {}", security.code(), box(security.orderBook().channelSeqNum()), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
	                if (r == null) {
	                    LOG.warn("Received cancel order request that is null for {}", security.code());
	                }
	                else {
	                    explain.orderSid(r.orderSid());
	                    explain.logExplainForSellOrder();
	                    messenger.eventSender().sendEventWithMultipleValues(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.INFO, EventType.ORDER_EXPLAIN, messenger.self().sinkId(), timestamp, "Cancel sell order from strategy", explain.eventValueTypes(), explain.eventValues());
	                    if (r.completionType() != OrderRequestCompletionType.OK) {
	                        final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
	                        if (handler != null) {
	                            LOG.info("Received cancel sell order request rejected for {} - {} {}, {}", security.code(), r.completionType(), r.rejectType(), r.reason());
	                            handler.onOrderStatusReceived(systemClock.nanoOfDay(), 0, 0, r.rejectType());
	                        }
	                    }
	                    else {
	                        LOG.info("Received cancel sell order request ok for {}", security.code());
	                        //sell(security, price, security.pendingSell(), explain);
	                    }
	                }
	            }
	            catch (final Exception e) {
	                LOG.error("Critical error encountered while handling sell-side order completion future for {}", security.code(), e);
	                errorHandler.onError(security.sid(), "Critical error encountered while handling sell-side order completion future");
	            }
	        });
        }
    }
}
