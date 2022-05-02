package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.MarketTrade;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;

import org.agrona.MutableDirectBuffer;

public class MarketDataTradeSender {
    static final Logger LOG = LogManager.getLogger(MarketDataTradeSender.class);

    private final MessageSender msgSender;
    private final MarketDataTradeSbeEncoder sbe = new MarketDataTradeSbeEncoder();

    /**
     * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
     * @param msgSender
     * @return
     */
    public static MarketDataTradeSender of(MessageSender msgSender){
        return new MarketDataTradeSender(msgSender);
    }

    MarketDataTradeSender(MessageSender msgSender){
        this.msgSender = msgSender;
    }

    public void sendMarketDataTrade(MessageSinkRef sink, MarketTrade trade){
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
            encodeMarketDataTrade(msgSender,
                    sink.sinkId(),
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    sbe,
                    trade);
            bufferClaim.commit();
            return;
        }
        int size = encodeMarketDataTrade(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                sbe,
                trade);
        msgSender.send(sink, msgSender.buffer(), 0, size);
    }

    public void sendMarketDataTrade(MessageSinkRef snapshotSink, MessageSinkRef[] sinks, MarketTrade trade){
        int size = encodeMarketDataTrade(msgSender,
                snapshotSink.sinkId(),
                msgSender.buffer(),
                0,
                sbe,
                trade);
        msgSender.send(snapshotSink, msgSender.buffer(), 0, size);
        for (MessageSinkRef sink : sinks){
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }
    }

    /**
     * @param orderBook
     * @return
     */
    public static int expectedEncodedLength(){
        int size = MessageHeaderEncoder.ENCODED_LENGTH +
                MarketDataTradeSbeDecoder.BLOCK_LENGTH;
        return size;
    }

    /* i expect we either call encoding method with the whole order book or with an exchange specific binary format */
    static int encodeMarketDataTrade(final MessageSender sender,
            int dstSinkId,
            MutableDirectBuffer buffer,
            int offset,
            MarketDataTradeSbeEncoder sbe,
            MarketTrade trade){
        sbe.wrap(buffer, offset + sender.headerSize()).
        secSid(trade.secSid()).
        price(trade.price()).
        quantity(trade.quantity()).
        tradeType(trade.tradeType()).
        tradeTime(trade.tradeNanoOfDay()).
        numActualTrades(trade.numActualTrades()).
        isRecovery(trade.isRecovery() ? BooleanType.TRUE : BooleanType.FALSE).
        triggerInfo().nanoOfDay(trade.triggerInfo().triggerNanoOfDay()).triggerSeqNum(trade.triggerInfo().triggerSeqNum()).triggeredBy(trade.triggerInfo().triggeredBy());

        sender.encodeHeader(dstSinkId,
                buffer,
                offset,
                MarketDataTradeSbeEncoder.BLOCK_LENGTH,
                MarketDataTradeSbeEncoder.TEMPLATE_ID,
                MarketDataTradeSbeEncoder.SCHEMA_ID,
                MarketDataTradeSbeEncoder.SCHEMA_VERSION,
                sbe.encodedLength());

        return sbe.encodedLength() + sender.headerSize();
    }

    public void sendMarketDataTrade(final MessageSinkRef snapshotSink, final MessageSinkRef[] sinks, final MutableDirectBuffer buffer, final int bufferSize) {
        msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        msgSender.send(snapshotSink, buffer, 0, bufferSize);
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, buffer, 0, bufferSize);
        }
    }

    public void sendMarketDataTrade(final MessageSinkRef sink, final MutableDirectBuffer buffer, final int bufferSize) {
        msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
        msgSender.send(sink, buffer, 0, bufferSize);
    }

    public void sendMarketDataTrade(final MessageSinkRef[] sinks, final MutableDirectBuffer buffer, final int bufferSize) {
        msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, buffer, 0, bufferSize);
        }
    }	


}
