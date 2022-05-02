package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.MarketStats;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;

import org.agrona.MutableDirectBuffer;

public class MarketStatsSender {
	static final Logger LOG = LogManager.getLogger(MarketStatsSender.class);

	private final MessageSender msgSender;
	private final MarketStatsSbeEncoder sbe = new MarketStatsSbeEncoder();

	/**
	 * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
	 * @param msgSender
	 * @return
	 */
	public static MarketStatsSender of(MessageSender msgSender){
		return new MarketStatsSender(msgSender);
	}
	
	MarketStatsSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public void sendMarketStats(MessageSinkRef sink, MarketStats stats){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeMarketStats(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					stats);
			bufferClaim.commit();
			return;
		}
		int size = encodeMarketStats(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				stats);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	public void sendMarketStats(MessageSinkRef snapshotSink, MessageSinkRef[] sinks, MarketStats stats){
		int size = encodeMarketStats(msgSender,
				snapshotSink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				stats);
		msgSender.send(snapshotSink, msgSender.buffer(), 0, size);
		for (MessageSinkRef sink : sinks){
			if (sink == null)
				break;
			MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
			msgSender.send(sink, msgSender.buffer(), 0, size);
		}
	}

    public void sendMarketStats(MessageSinkRef[] sinks, MarketStats stats) {
        int size = encodeMarketStats(msgSender, 0, msgSender.buffer(), 0, sbe, stats);
        for (MessageSinkRef sink : sinks) {
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
				MarketStatsSbeDecoder.BLOCK_LENGTH;
		return size;
	}
	
	/* i expect we either call encoding method with the whole order book or with an exchange specific binary format */
	static int encodeMarketStats(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer,
			int offset,
			MarketStatsSbeEncoder sbe,
			MarketStats stats){
	    encodeMarketStatsWithoutHeader(buffer, offset + sender.headerSize(), sbe, stats);

		sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				MarketStatsSbeEncoder.BLOCK_LENGTH,
				MarketStatsSbeEncoder.TEMPLATE_ID,
				MarketStatsSbeEncoder.SCHEMA_ID,
				MarketStatsSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());

		return sbe.encodedLength() + sender.headerSize();
	}
	
	public void sendMarketStats(final MessageSinkRef snapshotSink, final MessageSinkRef[] sinks, final MutableDirectBuffer buffer, final int bufferSize) {
		msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
		msgSender.send(snapshotSink, buffer, 0, bufferSize);
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, buffer, 0, bufferSize);
        }
	}
	
	public void sendMarketStats(final MessageSinkRef[] sinks, final MutableDirectBuffer buffer, final int bufferSize) {
		msgSender.header().wrap(buffer, 0).seq(msgSender.getAndIncSeq());
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(buffer, 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, buffer, 0, bufferSize);
        }
	}	

    public static int encodeMarketStatsWithoutHeader(MutableDirectBuffer buffer,
            int offset,
            MarketStatsSbeEncoder sbe,
            MarketStats stats){
        sbe.wrap(buffer, offset).
        secSid(stats.secSid()).
        isRecovery(stats.isRecovery() ? BooleanType.TRUE : BooleanType.FALSE).
        transactTime(stats.transactTime()).
        seqNum(stats.seqNum()).
        open(stats.open()).
        high(stats.high()).
        low(stats.low()).
        close(stats.close()).
        volume(stats.volume()).
        turnover(stats.turnover()).     
        triggerInfo().nanoOfDay(stats.triggerInfo().triggerNanoOfDay()).triggerSeqNum(stats.triggerInfo().triggerSeqNum()).triggeredBy(stats.triggerInfo().triggeredBy());
        return sbe.encodedLength();
    }
}
