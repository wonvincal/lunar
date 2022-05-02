package com.lunar.order;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.core.SystemClock;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTableBuilder;

/*
 * Can only handle fill and kill
 */
class MultiThreadedMatchingEngine extends MatchingEngine {
    static final Logger LOG = LogManager.getLogger(MultiThreadedMatchingEngine.class);
    static final int BUFFER_SIZE = 8192;
    
    private enum MatchEventType { MARKETDEPTH, TRADE, ORDER };
    private class MatchEvent {
        private MatchEventType matchEventType;
        private long secSid;
        private long timestamp;
        private MarketOrderBook orderBook;
        private Order order;
        
        public MatchEvent() {
            orderBook = MarketOrderBook.of(BOOK_DEPTH, SpreadTableBuilder.get(), Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        public MatchEventType matchEventType() { return matchEventType; }
        public MatchEvent matchEventType(final MatchEventType matchEventType) { this.matchEventType = matchEventType; return this; }
        
        public long secSid() { return secSid; }
        public MatchEvent secSid(final long secSid) { this.secSid = secSid; return this; }
        
        public long timestamp() { return timestamp; }
        public MatchEvent timestamp(final long timestamp) { this.timestamp = timestamp; return this; } 
             
        public MarketOrderBook orderBook() { return orderBook; }
   
        public Order order() { return order; }

        public MatchEvent order(final Order order) { this.order = order; return this; }

    }
    private final RingBuffer<MatchEvent> orderBookRingBuffer;
    private final ExecutorService executorService;
    private final Disruptor<MatchEvent> disruptor;

    @SuppressWarnings({ "unchecked", "deprecation" })
    MultiThreadedMatchingEngine(final MatchedHandler matchedHandler, final SystemClock systemClock, final long orderDelay) {
        super(matchedHandler, systemClock, orderDelay);
        executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory("matching-engine", "matching-engine-multithreaded"));
        disruptor = new Disruptor<MatchEvent>(() -> { return new MatchEvent(); }, BUFFER_SIZE, executorService);
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            switch (event.matchEventType()) {
            case MARKETDEPTH:
                super.handleOrder(event.timestamp(), event.secSid(), event.orderBook());
                break;
            case TRADE:
                super.handleTrade(event.timestamp());
                break;
            case ORDER:
                super.addOrder(event.secSid(), event.order());
                break;
            }
        });
        this.orderBookRingBuffer = disruptor.getRingBuffer();
        disruptor.start();
    }

    @Override
    protected void handleOrder(final long timestamp, final long secSid, final MarketOrderBook orderBook) {
        final long sequence = this.orderBookRingBuffer.next();
        try {
            final MatchEvent event = this.orderBookRingBuffer.get(sequence);
            event.matchEventType(MatchEventType.MARKETDEPTH);
            event.secSid(secSid);
            event.timestamp(timestamp);
            event.orderBook().copyFrom(orderBook);
        }
        finally {
            this.orderBookRingBuffer.publish(sequence);
        }
    }
    
    @Override
    protected void handleTrade(final long timestamp) {
        final long sequence = this.orderBookRingBuffer.next();
        try {
            final MatchEvent event = this.orderBookRingBuffer.get(sequence);
            event.matchEventType(MatchEventType.TRADE);
            event.timestamp(timestamp);
        }
        finally {
            this.orderBookRingBuffer.publish(sequence);
        }
    }

    @Override
    public void addOrder(final long secSid, final Order order) throws Exception {
        final long sequence = this.orderBookRingBuffer.next();
        try {
            final MatchEvent event = this.orderBookRingBuffer.get(sequence);
            event.matchEventType(MatchEventType.ORDER);
            event.secSid(secSid);
            event.order(order);
        }
        finally {
            this.orderBookRingBuffer.publish(sequence);
        }
    }
    
    public void stop() {
        super.stop();
        disruptor.shutdown();
        executorService.shutdown();
    }

}
