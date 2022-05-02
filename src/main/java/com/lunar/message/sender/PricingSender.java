package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.DividendCurveSbeEncoder;
import com.lunar.message.io.sbe.DividendCurveSbeEncoder.PointsEncoder;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.GreeksSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.pricing.DividendCurve;
import com.lunar.pricing.Greeks;

import org.agrona.MutableDirectBuffer;

public class PricingSender {
    static final Logger LOG = LogManager.getLogger(PricingSender.class);
    
    private final MessageSender msgSender;
    private final GreeksSbeEncoder greeksSbe = new GreeksSbeEncoder();
    private final DividendCurveSbeEncoder dividendCurveSbe = new DividendCurveSbeEncoder();

    /**
     * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
     * @param msgSender
     * @return
     */
    public static PricingSender of(MessageSender msgSender){
        return new PricingSender(msgSender);
    }
    
    PricingSender(MessageSender msgSender){
        this.msgSender = msgSender;
    }
    
    public void sendDividendCurve(final MessageSinkRef[] sinks, final DividendCurve dividendCurve){
        int size = encodeDividendCurve(msgSender,
                0,
                msgSender.buffer(),
                0,
                msgSender.stringBuffer(),
                dividendCurveSbe,
                dividendCurve);
        for (MessageSinkRef sink : sinks){
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }
    }
    
    public long sendDividendCurve(final MessageSinkRef sink, final DividendCurve dividendCurve){
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(MessageHeaderEncoder.ENCODED_LENGTH + expectedEncodedLength(dividendCurve), sink, bufferClaim) == MessageSink.OK) {
            encodeDividendCurve(msgSender,
                    sink.sinkId(), 
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    msgSender.stringBuffer(),
                    dividendCurveSbe,
                    dividendCurve);
            bufferClaim.commit();
            return MessageSink.OK;
        }
        int size = encodeDividendCurve(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                msgSender.stringBuffer(),
                dividendCurveSbe,
                dividendCurve);
        return msgSender.send(sink, msgSender.buffer(), 0, size);
    }    
    
    public static int expectedEncodedLength(final DividendCurve dividendCurve){
        int size = MessageHeaderEncoder.ENCODED_LENGTH +
                DividendCurveSbeEncoder.BLOCK_LENGTH + 
                PointsEncoder.sbeHeaderSize() + 
                PointsEncoder.sbeBlockLength() * dividendCurve.numPoints();
        return size;
    }
    
    static int encodeDividendCurve(final MessageSender sender,
            final int dstSinkId,
            final MutableDirectBuffer buffer,
            final int offset,
            final MutableDirectBuffer stringBuffer,
            final DividendCurveSbeEncoder sbe,
            final DividendCurve dividendCurve) {
    	int payloadLength = encodeDividendCurveWithoutHeader(buffer, offset + sender.headerSize(), stringBuffer, sbe, dividendCurve);
        sender.encodeHeader(dstSinkId,
                buffer,
                offset,
                DividendCurveSbeEncoder.BLOCK_LENGTH,
                DividendCurveSbeEncoder.TEMPLATE_ID,
                DividendCurveSbeEncoder.SCHEMA_ID,
                DividendCurveSbeEncoder.SCHEMA_VERSION,
                payloadLength);
        return sender.headerSize() + payloadLength;
                
    }    
    
    static public int encodeDividendCurveWithoutHeader(final MutableDirectBuffer dstBuffer, int offset, final MutableDirectBuffer stringBuffer, final DividendCurveSbeEncoder sbe, final DividendCurve dividendCurve) {
        PointsEncoder pointsEncoder = sbe.wrap(dstBuffer, offset).secSid(dividendCurve.secSid()).pointsCount(dividendCurve.numPoints());
        for (int i = 0; i < dividendCurve.numPoints(); i++) {
            pointsEncoder = pointsEncoder.next();
            pointsEncoder.date(dividendCurve.dates()[i]);
            pointsEncoder.amount(dividendCurve.amounts()[i]);
        }
        return sbe.encodedLength();
    }

    public void sendGreeks(final MessageSinkRef persistSink, final MessageSinkRef[] sinks, final Greeks greeks){
        int size = encodeGreeks(msgSender,
                persistSink.sinkId(),
                msgSender.buffer(),
                0,
                greeksSbe,
                greeks);
        msgSender.send(persistSink, msgSender.buffer(), 0, size);
        for (MessageSinkRef sink : sinks){
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }
    }

    public void sendGreeks(final MessageSinkRef[] sinks, final Greeks greeks){
        int size = encodeGreeks(msgSender,
                0,
                msgSender.buffer(),
                0,
                greeksSbe,
                greeks);
        for (MessageSinkRef sink : sinks){
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }
    }

    public long trySendGreeks(final MessageSinkRef sink, final Greeks greeks){
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(MessageHeaderEncoder.ENCODED_LENGTH + GreeksSbeDecoder.BLOCK_LENGTH, sink, bufferClaim) == MessageSink.OK) {
            encodeGreeks(msgSender,
                    sink.sinkId(), 
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    greeksSbe,
                    greeks);
            bufferClaim.commit();
            return MessageSink.OK;
        }
        int size = encodeGreeks(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                greeksSbe,
                greeks);
        return msgSender.trySend(sink, msgSender.buffer(), 0, size);
    }
    
    public long sendGreeks(final MessageSinkRef sink, final Greeks greeks){
        // try to write directly to the message sink buffer
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(MessageHeaderEncoder.ENCODED_LENGTH + GreeksSbeDecoder.BLOCK_LENGTH, sink, bufferClaim) == MessageSink.OK) {
            encodeGreeks(msgSender,
                    sink.sinkId(), 
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    greeksSbe,
                    greeks);
            bufferClaim.commit();
            return MessageSink.OK;
        }
        int size = encodeGreeks(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                greeksSbe,
                greeks);
        return msgSender.send(sink, msgSender.buffer(), 0, size);
    }
    
    static public int encodeGreeks(final MessageSender sender,
            final int dstSinkId,
            final MutableDirectBuffer buffer,
            final int offset,
            final GreeksSbeEncoder sbe,
            final Greeks greeks) {
    	int payloadLength = encodeGreeksWithoutHeader(buffer, offset + sender.headerSize(), sbe, greeks);
        sender.encodeHeader(dstSinkId,
                buffer,
                offset,
                GreeksSbeEncoder.BLOCK_LENGTH,
                GreeksSbeEncoder.TEMPLATE_ID,
                GreeksSbeEncoder.SCHEMA_ID,
                GreeksSbeEncoder.SCHEMA_VERSION,
                payloadLength);
        return sender.headerSize() + payloadLength;                
    }
    
    static public int encodeGreeksWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final GreeksSbeEncoder sbe,
            final Greeks greeks) {
        sbe.wrap(buffer, offset)
            .secSid(greeks.secSid())
            .delta(greeks.delta())
            .gamma(greeks.gamma())
            .vega(greeks.vega())
            .impliedVol(greeks.impliedVol())
            .refSpot(greeks.refSpot())
            .bid(greeks.bid())
            .ask(greeks.ask())
            .bidImpliedVol(greeks.bidImpliedVol())
            .askImpliedVol(greeks.askImpliedVol());
        return sbe.encodedLength();
    }       
}
