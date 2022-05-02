package com.lunar.message.sender;

import java.util.Iterator;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.MarketOrderBook;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder.EntryEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.Tick;
import com.lunar.service.ServiceConstant;

public class MarketDataSender {
    static final Logger LOG = LogManager.getLogger(MarketDataSender.class);
    public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
    public static int EXPECTED_NUM_PARAMETERS = 4;
    static {
        EXPECTED_LENGTH_EXCLUDE_HEADER = AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH + AggregateOrderBookUpdateSbeEncoder.EntryEncoder.sbeHeaderSize() + AggregateOrderBookUpdateSbeEncoder.EntryEncoder.sbeBlockLength() * EXPECTED_NUM_PARAMETERS;
    }
    private final MessageSender msgSender;
    private final AggregateOrderBookUpdateSbeEncoder aggregateOrderBookUpdateSbe = new AggregateOrderBookUpdateSbeEncoder();
    private final OrderBookSnapshotSbeEncoder orderBookSnapshotSbe = new OrderBookSnapshotSbeEncoder(); 

    /**
     * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
     * @param msgSender
     * @return
     */
    public static MarketDataSender of(MessageSender msgSender){
        return new MarketDataSender(msgSender);
    }

    MarketDataSender(MessageSender msgSender){
        this.msgSender = msgSender;
    }

    public long trySendAggregateOrderBookUpdate(MessageSinkRef sink, AggregateOrderBookUpdateSbeDecoder orderBook){
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(expectedEncodedLength(orderBook), sink, bufferClaim) == MessageSink.OK){
            encodeAggregateOrderBookUpdate(msgSender,
                    sink.sinkId(), 
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    aggregateOrderBookUpdateSbe,
                    orderBook);
            bufferClaim.commit();
            return MessageSink.OK;
        }
        int size = encodeAggregateOrderBookUpdate(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                aggregateOrderBookUpdateSbe,
                orderBook);
        return msgSender.trySend(sink, msgSender.buffer(), 0, size);
    }


    /**
     * This is a reference implementation of what is going to happen.  We are going to get binary data from 'somewhere', and turn that
     * into our own format.
     * @param sink
     * @param secSid
     * @param orderBook
     */
    public void sendAggregateOrderBookUpdate(MessageSinkRef sink, AggregateOrderBookUpdateSbeDecoder orderBook){
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(expectedEncodedLength(orderBook), sink, bufferClaim) == MessageSink.OK){
            encodeAggregateOrderBookUpdate(msgSender,
                    sink.sinkId(), 
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    aggregateOrderBookUpdateSbe,
                    orderBook);
            bufferClaim.commit();
            return;
        }
        int size = encodeAggregateOrderBookUpdate(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                aggregateOrderBookUpdateSbe,
                orderBook);
        msgSender.send(sink, msgSender.buffer(), 0, size);
    }

    public long sendAggregateOrderBookUpdateActions(MessageSinkRef sink) {
        int size = msgSender.headerSize() + aggregateOrderBookUpdateSbe.encodedLength();
        // send to snapshot service first
        MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
        msgSender.send(sink, msgSender.buffer(), 0, size);
        return 0;
    }

    public long sendAggregateOrderBookUpdateActions(MessageSinkRef snapshotSink, MessageSinkRef[] sinks) {
        int size = msgSender.headerSize() + aggregateOrderBookUpdateSbe.encodedLength();
        // send to snapshot service first
        MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), snapshotSink.sinkId());
        msgSender.send(snapshotSink, msgSender.buffer(), 0, size);
        // now send to subscribed sinks
        if (sinks != null) {
            for (MessageSinkRef sink : sinks){
                if (sink == null)
                    break;
                MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
                msgSender.send(sink, msgSender.buffer(), 0, size);
            }
        }
        return 0;
    }


    public long sendAggregateOrderBookUpdateActions(final MessageSinkRef[] sinks) {
        if (sinks != null) {
            int size = msgSender.headerSize() + aggregateOrderBookUpdateSbe.encodedLength();		
            for (final MessageSinkRef sink : sinks){
                if (sink == null)
                    break;
                MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
                msgSender.send(sink, msgSender.buffer(), 0, size);
            }
        }
        return 0;
    }

    /**
     * @param orderBook
     * @return
     */
    public static int expectedEncodedLength(AggregateOrderBookUpdateSbeDecoder orderBook){
        int limit = orderBook.limit();
        int size = MessageHeaderEncoder.ENCODED_LENGTH +
                AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH + 
                EntryDecoder.sbeHeaderSize() + 
                EntryDecoder.sbeBlockLength() * orderBook.entry().count();
        orderBook.limit(limit);
        return size;
    }

    public static int expectedEncodedLength(int entryCount){
        return MessageHeaderEncoder.ENCODED_LENGTH + 
                AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH + 
                EntryEncoder.sbeHeaderSize() + 
                EntryDecoder.sbeBlockLength() * entryCount; 
    }

    /**
     * This is just to simulate what will happen.  I don't expect this method to be used in production.
     * @param sender
     * @param dstSinkId
     * @param buffer
     * @param offset
     * @param sbe
     * @param secSid
     * @param srcSbe
     * @return
     */
    static int encodeAggregateOrderBookUpdate(final MessageSender sender,
            int dstSinkId,
            MutableDirectBuffer buffer,
            int offset,
            AggregateOrderBookUpdateSbeEncoder sbe,
            AggregateOrderBookUpdateSbeDecoder srcSbe){
        EntryDecoder entryDecoder = srcSbe.entry();
        int count = entryDecoder.count();

        EntryEncoder entryEncoder = sbe.wrap(buffer, offset + sender.headerSize())
                .secSid(srcSbe.secSid())
                .isSnapshot(srcSbe.isSnapshot())
                .seqNum(srcSbe.seqNum())
                .entryCount(count);

        while (entryDecoder.hasNext()){
            entryDecoder = entryDecoder.next();
            entryEncoder = entryEncoder.next();
            entryEncoder.numOrders(entryDecoder.numOrders())
            .price(entryDecoder.price())
            .priceLevel(entryDecoder.priceLevel())
            .quantity(entryDecoder.quantity())
            .tickLevel(entryDecoder.tickLevel());
        }

        sender.encodeHeader(dstSinkId,
                buffer,
                offset,
                AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH,
                AggregateOrderBookUpdateSbeEncoder.TEMPLATE_ID,
                AggregateOrderBookUpdateSbeEncoder.SCHEMA_ID,
                AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION,
                sbe.encodedLength());

        return sender.headerSize() + sbe.encodedLength();
    }		

    public void sendOrderBookSnapshot(final MessageSinkRef snapshotSink, final MessageSinkRef[] sinks, final MutableDirectBuffer buffer, final int bufferSize) {
        msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        msgSender.send(snapshotSink, buffer, 0, bufferSize);
        if (sinks != null) {
            for (final MessageSinkRef sink : sinks) {
                if (sink == null)
                    break;
                MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
                msgSender.send(sink, buffer, 0, bufferSize);
            }
        }
    }

    public void sendOrderBookSnapshot(final MessageSinkRef sink, final MutableDirectBuffer buffer, final int bufferSize) {
        msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
        msgSender.send(sink, buffer, 0, bufferSize);
    }   

    public void sendOrderBookSnapshot(final MessageSinkRef[] sinks, final MutableDirectBuffer buffer, final int bufferSize) {
        msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, buffer, 0, bufferSize);
        }
    }	

    public void sendOrderBookSnapshot(final MessageSinkRef snapshotSink, final MessageSinkRef[] sinks, final MarketOrderBook orderBook) {
        final int size = encodeOrderBookSnapshot(msgSender,
                0,
                msgSender.buffer(),
                0,
                orderBookSnapshotSbe,
                orderBook);
        msgSender.send(snapshotSink, msgSender.buffer(), 0, size);
        if (sinks != null) {
            for (final MessageSinkRef sink : sinks){
                if (sink == null)
                    break;
                MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
                msgSender.send(sink, msgSender.buffer(), 0, size);
            }
        }		
    }

    public void sendOrderBookSnapshot(final MessageSinkRef[] sinks, final MarketOrderBook orderBook) {
        final int size = encodeOrderBookSnapshot(msgSender,
                0,
                msgSender.buffer(),
                0,
                orderBookSnapshotSbe,
                orderBook);
        for (final MessageSinkRef sink : sinks){
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }
    }

    public void sendOrderBookSnapshot(final MessageSinkRef sink, final MarketOrderBook orderBook) {
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(expectedEncodedLength(orderBook), sink, bufferClaim) == MessageSink.OK){
            encodeOrderBookSnapshot(msgSender,
                    sink.sinkId(),
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    orderBookSnapshotSbe,
                    orderBook);
            bufferClaim.commit();
            return;
        }
        int size = encodeOrderBookSnapshot(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                orderBookSnapshotSbe,
                orderBook);
        msgSender.send(sink, msgSender.buffer(), 0, size);
    }

    public static int expectedEncodedLength(final MarketOrderBook orderBook){
        int size = MessageHeaderEncoder.ENCODED_LENGTH + 
                OrderBookSnapshotSbeEncoder.BLOCK_LENGTH + 
                OrderBookSnapshotSbeEncoder.BidDepthEncoder.sbeHeaderSize() + 
                OrderBookSnapshotSbeEncoder.BidDepthEncoder.sbeBlockLength() * orderBook.bidSide().numPriceLevels() +
                OrderBookSnapshotSbeEncoder.AskDepthEncoder.sbeHeaderSize() +
                OrderBookSnapshotSbeEncoder.AskDepthEncoder.sbeBlockLength() * orderBook.askSide().numPriceLevels();

        if (size > ServiceConstant.MAX_MESSAGE_SIZE) {
            throw new IllegalArgumentException("Block length (" + size +  
                    ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
        }
        return size;
    }

    public static int encodeOrderBookSnapshot(final MessageSender sender,
            final int dstSinkId,
            final MutableDirectBuffer buffer,
            final int offset,			
            final OrderBookSnapshotSbeEncoder sbe,
            final MarketOrderBook orderBook) {

        int payloadLength = encodeOrderBookSnapshotWithoutHeader(buffer, offset + sender.headerSize(), sbe, orderBook);

        sender.encodeHeader(dstSinkId,
                buffer,
                offset,
                OrderBookSnapshotSbeEncoder.BLOCK_LENGTH,
                OrderBookSnapshotSbeEncoder.TEMPLATE_ID,
                OrderBookSnapshotSbeEncoder.SCHEMA_ID,
                OrderBookSnapshotSbeEncoder.SCHEMA_VERSION,
                payloadLength);

        return sender.headerSize() + payloadLength;
    }

    public static int encodeOrderBookSnapshotWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final OrderBookSnapshotSbeEncoder sbe,
            final MarketOrderBook orderBook) {
        sbe.wrap(buffer, offset).secSid(orderBook.secSid()).seqNum(orderBook.channelSeqNum()).transactTime(orderBook.transactNanoOfDay()).isRecovery(orderBook.isRecovery() ? BooleanType.TRUE : BooleanType.FALSE);
        sbe.triggerInfo().triggeredBy(orderBook.triggerInfo().triggeredBy()).triggerSeqNum(orderBook.triggerInfo().triggerSeqNum()).nanoOfDay(orderBook.triggerInfo().triggerNanoOfDay());
        OrderBookSnapshotSbeEncoder.AskDepthEncoder askEncoder = sbe.askDepthCount(orderBook.askSide().numPriceLevels());
        final Iterator<Tick> askIterator = orderBook.askSide().localPriceLevelsIterator();
        while (askIterator.hasNext()) {
            final Tick tick = askIterator.next();
            askEncoder = askEncoder.next().price(tick.price()).quantity((int)tick.qty()).tickLevel(tick.tickLevel());
        }
        OrderBookSnapshotSbeEncoder.BidDepthEncoder bidEncoder = sbe.bidDepthCount(orderBook.bidSide().numPriceLevels());
        final Iterator<Tick> bidIterator = orderBook.bidSide().localPriceLevelsIterator();
        while (bidIterator.hasNext()) {
            final Tick tick = bidIterator.next();
            bidEncoder = bidEncoder.next().price(tick.price()).quantity((int)tick.qty()).tickLevel(tick.tickLevel());
        }		
        return sbe.encodedLength();
    }

}
