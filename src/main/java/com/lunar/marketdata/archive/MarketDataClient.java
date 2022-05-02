package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.marketdata.OrderBookSide;
import com.lunar.message.Parameter;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

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
        private final MutableDirectBuffer[] m_queue;
        private int m_queueSize;
        private int m_numOfSubscriptions;
        private int m_firstSeqNum;
        private int m_prevSeqNum;		

        public SecuritySubscription(final Security security, final MarketOrderBook orderBook) {
            m_security = security;
            m_orderBook = orderBook;
            m_numOfSubscriptions = 0;			
            m_queue = new MutableDirectBuffer[ServiceConstant.MAX_MARKETDATA_QUEUE_SIZE];
            m_queueSize = 0;
            m_firstSeqNum = 0;
            m_prevSeqNum = 0;
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

        public MutableDirectBuffer[] getQueue() {
            return m_queue;
        }

        public int getQueueSize() {
            return m_queueSize;
        }

        public void clearQueue() {
            m_queueSize = 0;
            m_firstSeqNum = 0;
            m_prevSeqNum = 0;
        }

        public int getFirstSeqNum() {
            return m_firstSeqNum;
        }

        public void addToQueue(final DirectBuffer buffer, final int offset, final int seqNum) throws Exception {
            if (m_queueSize > 0 && seqNum > m_prevSeqNum + 1) {
                // gap while waiting for snapshot, clear the queue
                clearQueue();
            }
            if (m_queueSize < ServiceConstant.MAX_MARKETDATA_QUEUE_SIZE) {
                if (m_queueSize == 0) {
                    m_firstSeqNum = seqNum;
                }
                if (m_queue[m_queueSize] == null) {
                    m_queue[m_queueSize] = new UnsafeBuffer(ByteBuffer.allocateDirect(buffer.capacity() - offset).order(ByteOrder.nativeOrder()));
                }
                m_queue[m_queueSize++].putBytes(0, buffer, offset, buffer.capacity() - offset);
                m_prevSeqNum = seqNum;
            }
            else {
                throw new Exception("Queue size capacity reached!");
            }
        }
    }

    private final Messenger m_messenger;
    private final LongEntityManager<? extends Security> m_securities;
    private final Long2ObjectOpenHashMap<SecuritySubscription> m_subscriptions;	
    private final AggregateOrderBookUpdateSbeDecoder m_sbeDecoder;

    public MarketDataClient(final Messenger messenger, final LongEntityManager<? extends Security> securities, final int initialCapacitySize) {
        m_messenger = messenger;
        m_securities = securities;
        m_subscriptions = new Long2ObjectOpenHashMap<SecuritySubscription>(initialCapacitySize);
        m_sbeDecoder = new AggregateOrderBookUpdateSbeDecoder();        
    }

    public boolean handleMarketData(final AggregateOrderBookUpdateSbeDecoder codec, final DirectBuffer buffer, final int offset, final OrderBookUpdateHandler obUpdateHandler) throws Exception {
        //final TriggerInfoDecoder triggerInfo = codec.triggerInfo();
        //TODO shayan - to remove
        //LOG.info("Received market data " + triggerInfo.seqNum() + " at " + triggerInfo.timestamp() + " from " + triggerInfo.triggeredBy());
    	final long secSid = codec.secSid();
        final SecuritySubscription subscription = m_subscriptions.get(secSid);
        if (subscription == null) {
            return false;
        }
        final MarketOrderBook orderBook = subscription.getOrderBook();
        int expectedSeqNum = orderBook.channelSeqNum() + 1;
        if (codec.isSnapshot().equals(BooleanType.FALSE)) {
            if (codec.seqNum() == expectedSeqNum) {
                final long transactTime = updateOrderBook(orderBook, codec, false);
                obUpdateHandler.onUpdate(transactTime, subscription.getSecurity(), orderBook);
            }
            else if (codec.seqNum() > expectedSeqNum) {
                subscription.addToQueue(buffer, offset, codec.seqNum());
                if (subscription.getQueueSize() == 1) {
                    requestSnapshot(subscription);
                }
            }		    
        }
        else {
            if (codec.seqNum() + 1 < subscription.getFirstSeqNum()) {
                // snapshot is stale...
                requestSnapshot(subscription);
            }
            else if (codec.seqNum() >= expectedSeqNum) {
                long transactTime = updateOrderBook(orderBook, codec, true);
                expectedSeqNum = orderBook.channelSeqNum() + 1;
                final MutableDirectBuffer[] queue = subscription.getQueue();
                for (int i = 0; i < subscription.getQueueSize(); i++) {
                    m_sbeDecoder.wrap(queue[i], 0, AggregateOrderBookUpdateSbeDecoder.BLOCK_LENGTH, AggregateOrderBookUpdateSbeDecoder.SCHEMA_VERSION);
                    if (m_sbeDecoder.seqNum() < expectedSeqNum) {
                        continue;
                    }
                    else if (m_sbeDecoder.seqNum() == expectedSeqNum) {
                        transactTime = updateOrderBook(orderBook, m_sbeDecoder, true);
                        expectedSeqNum++;        
                    }
                    else {
                        throw new Exception("Unexpected state encountered!");
                        // snapshot is stale...should have been handled earlier
                        // requestSnapshot(subscription);
                        // break;
                    }
                }
                subscription.clearQueue();
                if (transactTime > 0) {
                    obUpdateHandler.onUpdate(transactTime, subscription.getSecurity(), orderBook);
                }
            }            
            else if (subscription.getQueueSize() > 0) {
                // impossible to get there
                throw new Exception("Unexpected state encountered!");
            }
        }
        return true;
    }

    public void subscribe(final long secSid, final OrderBookFactory orderBookFactory, final ResponseHandler responseHandler) {
        final Security security = m_securities.get(secSid);
        if (security != null) {
            SecuritySubscription subscription = m_subscriptions.get(secSid);
            if (subscription == null) {
                subscription = new SecuritySubscription(security, orderBookFactory.createOrderBook(security));
                m_subscriptions.put(secSid, subscription);
            }

            final int numSubscribers = subscription.getNumOfSubscriptions();
            if (numSubscribers == 0) {
                m_messenger.sendRequest(security.mdsSink(),
                        RequestType.SUBSCRIBE,
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.AggregateOrderBookUpdate.value()), Parameter.of(ParameterType.SECURITY_SID, security.sid())).build(),
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
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.AggregateOrderBookUpdate.value()), Parameter.of(ParameterType.SECURITY_SID, security.sid())).build(),
                        ResponseHandler.NULL_HANDLER);
                m_subscriptions.remove(secSid);
            }
        }
    }

    private long updateOrderBook(final MarketOrderBook orderBook, final AggregateOrderBookUpdateSbeDecoder codec, final boolean isSnapshot) {
        long transactTime = 0;
        if (codec.isSnapshot().equals(BooleanType.TRUE)) {
            orderBook.bidSide().clear();
            orderBook.askSide().clear();
        }
        for (com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder entry : codec.entry()) {
            //security.orderBook().process(entry);
            switch (entry.updateAction()){
            case NEW:
            {
                OrderBookSide orderBookSide = entry.side().equals(Side.BUY) ? orderBook.bidSide() : orderBook.askSide();
                orderBookSide.create(entry.tickLevel(), entry.price(), entry.priceLevel(), entry.quantity(), entry.numOrders());
                break;
            }
            case CHANGE:
            {
            	OrderBookSide orderBookSide = entry.side().equals(Side.BUY) ? orderBook.bidSide() : orderBook.askSide();
                orderBookSide.create(entry.tickLevel(), entry.price(), entry.priceLevel(), entry.quantity(), entry.numOrders());
                orderBookSide.update(entry.tickLevel(), entry.price(), entry.priceLevel(), entry.quantity(), entry.numOrders());
                break;
            }
            case DELETE:
            {
            	OrderBookSide orderBookSide = entry.side().equals(Side.BUY) ? orderBook.bidSide() : orderBook.askSide();
                orderBookSide.delete(entry.tickLevel(), entry.price(), entry.priceLevel());
                break;
            }
            case CLEAR:
            {
                orderBook.bidSide().clear();
                orderBook.askSide().clear();
            }
            default:
                break;
            }
            transactTime = entry.transactTime();
        }
        orderBook.channelSeqNum(codec.seqNum()).transactNanoOfDay(transactTime).isRecovery(isSnapshot);
        return transactTime;
    }

    private void requestSnapshot(final SecuritySubscription subscription) {
        final Security security = subscription.getSecurity();
        LOG.info("Requesting market data snapshot for {}", security.code());
        m_messenger.sendRequest(security.mdsssSink(),
                RequestType.GET,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.AggregateOrderBookUpdate.value()), Parameter.of(ParameterType.SECURITY_SID, security.sid())).build(),
                ResponseHandler.NULL_HANDLER);
    }
}
