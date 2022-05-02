package com.lunar.order;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.RealSystemClock;
import com.lunar.core.SystemClock;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;

public class MatchingEngineWarmup {
    static final Logger LOG = LogManager.getLogger(MatchingEngineWarmup.class);
    private static final int COMPILE_WARMUP_COUNTS = 20000; // default compile threshold is 10000
    private static final int PREDICTIVE_WARMUP_COUNTS = 120000;

    public static void warmup() {
        LOG.info("Warming up matching engine...");
        final SystemClock systemClock = new RealSystemClock();
        final MatchingEngine matchingEngine = new MultiThreadedMatchingEngine(new MatchingEngine.MatchedHandler() {
            @Override
            public void onTrade(long timestamp, Order order, int price, int quantity) {
            }
            
            @Override
            public void onOrderExpired(long timestamp, Order order) {
            }
        }, systemClock, 0);
        final SpreadTable spreadTable = SpreadTableBuilder.get();
        MarketOrderBook orderBook = MarketOrderBook.of(5L, 10, spreadTable, Integer.MIN_VALUE, Integer.MIN_VALUE);

        try {
            for (int i = 0; i < COMPILE_WARMUP_COUNTS; i++) {
                orderBook.askSide().clear();
                orderBook.bidSide().clear();
                orderBook.askSide().create(spreadTable.tickToPrice(999), 10000);
                orderBook.bidSide().create(spreadTable.tickToPrice(998), 10000);        
                matchingEngine.handleOrder(systemClock.nanoOfDay(), 1, orderBook);
        
                orderBook.askSide().create(spreadTable.tickToPrice(1000), 10000);
                orderBook.bidSide().create(spreadTable.tickToPrice(997), 10000);        
                matchingEngine.handleOrder(systemClock.nanoOfDay(), 1, orderBook);
                
                Order order = Order.of(1L, null, 1, 6000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, spreadTable.tickToPrice(999), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                order = Order.of(1L, null, 1, 10000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, spreadTable.tickToPrice(999), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                order = Order.of(1L, null, 1, 12000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, spreadTable.tickToPrice(999), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
        
                order = Order.of(1L, null, 1, 12000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, spreadTable.tickToPrice(1000), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                order = Order.of(1L, null, 1, 12000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, spreadTable.tickToPrice(998), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);        
                
                order = Order.of(1L, null, 1, 6000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.SELL, spreadTable.tickToPrice(998), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                order = Order.of(1L, null, 1, 10000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.SELL, spreadTable.tickToPrice(998), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                order = Order.of(1L, null, 1, 12000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.SELL, spreadTable.tickToPrice(998), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
        
                order = Order.of(1L, null, 1, 12000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.SELL, spreadTable.tickToPrice(997), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                order = Order.of(1L, null, 1, 12000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.SELL, spreadTable.tickToPrice(999), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                
                matchingEngine.handleTrade(systemClock.nanoOfDay());
            }
            
            // if the system is fast enough, we expect the order price be the same as the best bid/ask
            orderBook.askSide().clear();
            orderBook.bidSide().clear();
            orderBook.askSide().create(spreadTable.tickToPrice(999), 10000);
            orderBook.bidSide().create(spreadTable.tickToPrice(998), 10000);        
            matchingEngine.handleOrder(systemClock.nanoOfDay(), 1, orderBook);            
            for (int i = 0; i < PREDICTIVE_WARMUP_COUNTS; i++) {
                Order order = Order.of(1L, null, 1, 6000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, spreadTable.tickToPrice(999), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
                order = Order.of(1L, null, 1, 6000, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.SELL, spreadTable.tickToPrice(998), 0, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
                matchingEngine.addOrder(systemClock.nanoOfDay(), order);
            }
        }
        catch (final Exception e) {
            LOG.error("Error encountered while trying to warmup MatchingEngine...", e);
        }
        matchingEngine.stop();
        LOG.info("Done warming up matching engine...");
    }
    

}
