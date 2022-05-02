package com.lunar.order;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SystemClock;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.ReplayMarketDataSource;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.marketdata.hkex.CtpMduMarketDataSource;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.order.MatchingEngine.MatchedHandler;

public class MatchingEngineFactory {
    static final Logger LOG = LogManager.getLogger(MatchingEngineFactory.class);
    private static final int BOOK_DEPTH = 10;

    static public MatchingEngine createReplayMatchingEngine(final MatchedHandler matchedHandler, final SystemClock systemClock, final long orderDelay, final boolean isMultiThreaded) {
        final MatchingEngine matchingEngine = isMultiThreaded ? new MultiThreadedMatchingEngine(matchedHandler, systemClock, orderDelay) : new MatchingEngine(matchedHandler, systemClock, orderDelay);
        ReplayMarketDataSource.instanceOf().registerCallbackHandler(new ReplayMarketDataSource.MarketDataReplayerCallbackHandler() {
            @Override
            public int onMarketData(final long secSid, final MarketOrderBook orderBook) {
                matchingEngine.handleOrder(orderBook.transactNanoOfDay(), secSid, orderBook);
                return 0;
            }

            @Override
            public int onTrade(long secSid, final MarketTrade trade) {
                matchingEngine.handleTrade(trade.tradeNanoOfDay());
                return 0;
            }

            @Override
            public int onTradingPhase(MarketStatusType marketStatus) {
                return 0;
            }            
        });
        return matchingEngine;
    }

    static public MatchingEngine createCtpMduMatchingEngine(final MatchedHandler matchedHandler, final SystemClock systemClock, final long orderDelay, final boolean isMultiThreaded) {
        final MatchingEngine matchingEngine = isMultiThreaded ? new MultiThreadedMatchingEngine(matchedHandler, systemClock, orderDelay) : new MatchingEngine(matchedHandler, systemClock, orderDelay);
        final MarketOrderBook orderBook = MarketOrderBook.of(BOOK_DEPTH, SpreadTableBuilder.get(), Integer.MIN_VALUE, Integer.MIN_VALUE);
        CtpMduMarketDataSource.instanceOf().registerCallbackHandler(new CtpMduMarketDataSource.CtpCallbackHandler() {
            private final OrderBookSnapshotSbeDecoder orderBookSnapshotDecoder = new OrderBookSnapshotSbeDecoder();
            @Override
            public int onMarketData(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
            	try {
	                orderBookSnapshotDecoder.wrap(directBuffer, MessageHeaderEncoder.ENCODED_LENGTH, OrderBookSnapshotSbeDecoder.BLOCK_LENGTH, OrderBookSnapshotSbeDecoder.SCHEMA_VERSION);
	                int limit = orderBookSnapshotDecoder.limit();
	                final long timestamp = orderBookSnapshotDecoder.transactTime();
	                orderBook.secSid(orderBookSnapshotDecoder.secSid());
	                orderBook.isRecovery(orderBookSnapshotDecoder.isRecovery() == BooleanType.TRUE);
	                orderBook.channelSeqNum(orderBookSnapshotDecoder.seqNum());
	                orderBook.transactNanoOfDay(timestamp);                
	                orderBook.askSide().clear();
	                orderBook.bidSide().clear();
	                for (OrderBookSnapshotSbeDecoder.AskDepthDecoder askDepth : orderBookSnapshotDecoder.askDepth()) {
	                    orderBook.askSide().create(askDepth.price(), askDepth.quantity());
	                }
	                for (OrderBookSnapshotSbeDecoder.BidDepthDecoder bidDepth : orderBookSnapshotDecoder.bidDepth()) {
	                    orderBook.bidSide().create(bidDepth.price(), bidDepth.quantity());
	                }
	                orderBookSnapshotDecoder.limit(limit);
	                matchingEngine.handleOrder(timestamp, secSid, orderBook);
            	}
            	catch (final Exception e) {
            		LOG.error("Error encountered when handling market data for {}", secSid, e);
            	}
                return 0;
            }

            @Override
            public int onTrade(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
                final long timestamp = orderBookSnapshotDecoder.transactTime();
                matchingEngine.handleTrade(timestamp);
                return 0;
            }

            @Override
            public int onMarketStats(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
                return 0;
            }
            
            @Override
            public int onTradingPhase(MarketStatusType marketStatus) {
                return 0;
            }
            
            @Override
            public int onSessionState(int sessionState) {
                return 0;
            }
        });        
        return matchingEngine;
        
    }
}
