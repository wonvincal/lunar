package com.lunar.marketdata;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.message.Parameter;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.TemplateType;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.agrona.DirectBuffer;

public class MarketDataClient {
	private static final Logger LOG = LogManager.getLogger(MarketDataClient.class);

    public interface OrderBookFactory {
        MarketOrderBook createOrderBook(final Security security);
    }

    public interface OrderBookUpdateHandler {
        void onUpdate(final long transactTime, final Security security, final MarketOrderBook orderBook);
    }

    private class SecuritySubscription	{		
        private final MarketOrderBook m_orderBook;
        private final Security m_security;
        private int m_numOfSubscriptions;

        public SecuritySubscription(final Security security, final MarketOrderBook orderBook) {
            m_security = security;
            m_orderBook = orderBook;
            m_numOfSubscriptions = 0;			
        }

        public Security getSecurity() {
            return m_security;
        }

        public int getNumOfSubscriptions() {
            return m_numOfSubscriptions;
        }

        public int increaseSubscription() {
            return ++m_numOfSubscriptions;
        }

        public int decreaseSubscription() {
            return --m_numOfSubscriptions;
        }

        public MarketOrderBook getOrderBook() {
            return m_orderBook;
        }
    }

    private final Messenger m_messenger;
    private final LongEntityManager<? extends Security> m_securities;
    private final Long2ObjectOpenHashMap<SecuritySubscription> m_subscriptions;	

    public MarketDataClient(final Messenger messenger, final LongEntityManager<? extends Security> securities, final int initialCapacitySize) {
        m_messenger = messenger;
        m_securities = securities;
        m_subscriptions = new Long2ObjectOpenHashMap<SecuritySubscription>(initialCapacitySize);
    }

    public boolean handleMarketData(final OrderBookSnapshotSbeDecoder codec, final DirectBuffer buffer, final int offset, final OrderBookUpdateHandler obUpdateHandler) throws Exception {        
        //TODO shayan - to remove
    	//final TriggerInfoDecoder triggerInfo = codec.triggerInfo();
        //LOG.info("Received market data " + triggerInfo.seqNum() + " at " + triggerInfo.timestamp() + " from " + triggerInfo.triggeredBy());
    	final long secSid = codec.secSid();
        final SecuritySubscription subscription = m_subscriptions.get(secSid);
        if (subscription == null) {
            return false;
        }
        final MarketOrderBook orderBook = subscription.getOrderBook();
        int expectedSeqNum = orderBook.channelSeqNum() + 1;
        if (codec.seqNum() >= expectedSeqNum) {
	        orderBook.isRecovery(codec.isRecovery() == BooleanType.TRUE);
            if (codec.isRecovery() == BooleanType.TRUE) {
                LOG.warn("Market data recovery message received: secCode {}, seqNum {}", secSid, codec.seqNum());
            }
	        orderBook.channelSeqNum(codec.seqNum());
	        orderBook.transactNanoOfDay(codec.transactTime());
	        orderBook.triggerInfo().decode(codec.triggerInfo());
	        orderBook.askSide().clear();
	        for (final OrderBookSnapshotSbeDecoder.AskDepthDecoder tick : codec.askDepth()) {
	        	orderBook.askSide().create(tick.price(), tick.quantity());
	        }
	        orderBook.bidSide().clear();
	        for (final OrderBookSnapshotSbeDecoder.BidDepthDecoder tick : codec.bidDepth()) {
	        	orderBook.bidSide().create(tick.price(), tick.quantity());
	        }
	        obUpdateHandler.onUpdate(orderBook.transactNanoOfDay(), subscription.getSecurity(), orderBook);
        }
        else {
            LOG.error("Cannot process data because seqnum isn't as expected: secCode {}, seqNum {}, expected {}", secSid, codec.seqNum(), expectedSeqNum);
        }
        return true;
    }
    
    public void subscribe(final long secSid, final OrderBookFactory orderBookFactory, final ResponseHandler responseHandler) {
        final Security security = m_securities.get(secSid);
        if (security != null) {
            SecuritySubscription subscription = m_subscriptions.get(secSid);
            LOG.info("Checking for existing subscription for {}", security.code());
            if (subscription == null) {
                subscription = new SecuritySubscription(security, orderBookFactory.createOrderBook(security));
                m_subscriptions.put(secSid, subscription);
            }
            final int numSubscribers = subscription.getNumOfSubscriptions();
            if (numSubscribers == 0) {
                LOG.info("Subscribing market data for {}", security.code());
                m_messenger.sendRequest(security.mdsSink(),
                        RequestType.SUBSCRIBE,
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ORDERBOOK_SNAPSHOT.value()), Parameter.of(ParameterType.SECURITY_SID, security.sid())).build(),
                        responseHandler);
            }
            subscription.increaseSubscription();
        }	    
    }

    public void unsubscribe(final long secSid) {
        final SecuritySubscription subscription = m_subscriptions.get(secSid);
        if (subscription != null) {
            if (subscription.decreaseSubscription() == 0) {
                final Security security = subscription.getSecurity();
                m_messenger.sendRequest(security.mdsSink(),
                        RequestType.UNSUBSCRIBE,
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ORDERBOOK_SNAPSHOT.value()), Parameter.of(ParameterType.SECURITY_SID, security.sid())).build(),
                        ResponseHandler.NULL_HANDLER);
                m_subscriptions.remove(secSid);
            }
        }
    }

}
