package com.lunar.order;

import java.util.Iterator;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.SystemClock;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.Side;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/*
 * Can only handle fill and kill
 */
public class MatchingEngine {
    static final Logger LOG = LogManager.getLogger(MatchingEngine.class);

    public interface MatchedHandler {
        void onTrade(final long timestamp, final Order order, final int price, final int quantity);
        void onOrderExpired(final long timestamp, final Order order);
    }
    
    private static final int NUM_SECURITIES = 5000;
    private static final int MAX_PENDING_ORDERS = 1024;
    protected static final int BOOK_DEPTH = 10;
    
    private final Long2ObjectOpenHashMap<SecurityInfo> securityInfosMap;
    private final MatchedHandler matchedHandler;
    
    private final Order[] pendingOrders;
    private int pendingOrdersHeadIndex;
    private int numPendingOrders;    
    
    private final SystemClock systemClock;
    private final long orderDelay;
    
    private class SecurityInfo {
        private MarketOrderBook orderBook = MarketOrderBook.of(BOOK_DEPTH, SpreadTableBuilder.get(), Integer.MIN_VALUE, Integer.MIN_VALUE);
        
        public SecurityInfo() {
        }
        
        public MarketOrderBook orderBook() { return this.orderBook; }

    }
    
    MatchingEngine(final MatchedHandler matchedHandler, final SystemClock systemClock, final long orderDelay) {
        this.securityInfosMap = new Long2ObjectOpenHashMap<SecurityInfo>(NUM_SECURITIES);
        this.systemClock = systemClock;
        this.orderDelay = orderDelay;
        this.pendingOrders = new Order[MAX_PENDING_ORDERS];
        this.pendingOrdersHeadIndex = 0;
        this.numPendingOrders = 0;
        this.matchedHandler = matchedHandler;
        LOG.info("Create matching engine [orderDelayInNs:{}]", orderDelay);
    }
    
    protected void handleOrder(final long timestamp, final long secSid, final MarketOrderBook orderBook) {
        processOrderQueue();
        SecurityInfo securityInfo = securityInfosMap.get(secSid);
        if (securityInfo == null) {
            securityInfo = new SecurityInfo();
            securityInfosMap.put(secSid, securityInfo);
        }
        securityInfo.orderBook().copyFrom(orderBook);
    }
    
    protected void handleTrade(final long timestamp) {
        processOrderQueue();
    }
    
    public void addOrder(final long secSid, final Order order) throws Exception {
        order.updateTime(systemClock.nanoOfDay() + orderDelay);
        if (orderDelay == 0) {
            matchOrder(order);
        }
        else {
            queueOrder(order);
        }
    }
    
    public void stop() {
        
    }
    
    private void processOrderQueue() {        
        Order order = peekOrder();
        while (order != null && order.updateTime() < systemClock.nanoOfDay()) {
            //order.updateTime(systemClock.nanoOfDay());
            popOrder();
            matchOrder(order);
            order = peekOrder();
        }
    }

    private int matchOrder(final Order order) {
        LOG.trace("Matching order orderSid {} for security {}", order.sid(), order.secSid());
        final SecurityInfo securityInfo = securityInfosMap.get(order.secSid());        
        if (securityInfo != null) {            
            int outstanding = order.quantity();
            order.cumulativeExecQty(0);
            if (order.side().equals(Side.BUY)) {
                final Iterator<Tick> iterator = securityInfo.orderBook().askSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    final Tick tick = iterator.next();
                    if (tick.qty() <= 0)
                        break;
                    if (tick.price() <= order.limitPrice()) {
                        final int quantityBought = Math.min(outstanding, (int)tick.qty());
                        outstanding -= quantityBought;
                        order.cumulativeExecQty(order.cumulativeExecQty() + quantityBought);
                        order.leavesQty(outstanding);
                        order.status((outstanding != 0) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED);
                        LOG.trace("Matched order orderSid {} for security {} of quantity {}", order.sid(), order.secSid(), quantityBought);
                        matchedHandler.onTrade(order.updateTime(), order, tick.price(), quantityBought);
                        if (outstanding == 0)
                            break;
                    }
                }
            }
            else if (order.side().equals(Side.SELL)) {
                final Iterator<Tick> iterator = securityInfo.orderBook().bidSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    final Tick tick = iterator.next();
                    if (tick.qty() <= 0)
                        break;
                    if (tick.price() >= order.limitPrice()) {
                        final int quantityBought = Math.min(outstanding, (int)tick.qty());
                        outstanding -= quantityBought;
                        order.leavesQty(outstanding);
                        order.cumulativeExecQty(order.cumulativeExecQty() + quantityBought);
                        order.status((outstanding != 0) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED);
                        LOG.trace("Matched order orderSid {} for security {} of quantity {}", order.sid(), order.secSid(), quantityBought);
                        matchedHandler.onTrade(order.updateTime(), order, tick.price(), quantityBought);                            
                        if (outstanding == 0)
                            break;
                    }
                }
            }
            if (outstanding > 0) {
            	// According to 4A-OCG Offline Simulator Binary Test Cases, leaves quantity in this OrderExpired
            	// will be set at 0
            	//order.leavesQty(0);
            	order.status(OrderStatus.CANCELLED);
            	LOG.trace("Cannot match order orderSid {} for security {}", order.sid(), order.secSid());
                matchedHandler.onOrderExpired(order.updateTime(), order);
            }
        }
        return 0;
    }

    private void queueOrder(final Order order) {
        if (numPendingOrders == MAX_PENDING_ORDERS) {
            throw new IndexOutOfBoundsException();
        }
        final int index = (numPendingOrders + pendingOrdersHeadIndex) & (MAX_PENDING_ORDERS - 1);
        pendingOrders[index] = order;
        numPendingOrders++;
    }
    
    private Order peekOrder() {
        if (numPendingOrders == 0)
            return null;
        return pendingOrders[pendingOrdersHeadIndex];
    }
    
    private Order popOrder() {
        if (numPendingOrders == 0)
            return null;
        final Order order = pendingOrders[pendingOrdersHeadIndex++];
        pendingOrdersHeadIndex = pendingOrdersHeadIndex & (MAX_PENDING_ORDERS - 1);
        numPendingOrders--;
        return order;
    }    
}
