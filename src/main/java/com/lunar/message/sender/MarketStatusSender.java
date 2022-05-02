package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.MarketStatusSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusSbeEncoder;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;

import org.agrona.MutableDirectBuffer;

public class MarketStatusSender {
	static final Logger LOG = LogManager.getLogger(MarketStatusSender.class);

	private final MessageSender msgSender;
	private final MarketStatusSbeEncoder sbe = new MarketStatusSbeEncoder();

	/**
	 * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
	 * @param msgSender
	 * @return
	 */
	public static MarketStatusSender of(MessageSender msgSender){
		return new MarketStatusSender(msgSender);
	}
	
	MarketStatusSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public long trySendMarketStatus(MessageSinkRef sink, MarketStatusSbeDecoder marketStatus){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeMarketStatus(msgSender,
					sink.sinkId(), 
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					marketStatus);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeMarketStatus(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				marketStatus);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	

	/**
	 * This is a reference implementation of what is going to happen.  We are going to get binary data from 'somewhere', and turn that
	 * into our own format.
	 * @param sink
	 * @param secSid
	 * @param orderBook
	 */
	public void sendMarketStatus(MessageSinkRef sink, MarketStatusSbeDecoder marketStatus){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeMarketStatus(msgSender,
					sink.sinkId(), 
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					marketStatus);
			bufferClaim.commit();
			return;
		}
		int size = encodeMarketStatus(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				marketStatus);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
    
	public void sendMarketStatus(MessageSinkRef sink, int exchangeSid, MarketStatusType marketStatus){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeMarketStatus(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					exchangeSid,
					marketStatus);
			bufferClaim.commit();
			return;
		}
		int size = encodeMarketStatus(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				exchangeSid,
				marketStatus);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	public void sendMarketStatus(MessageSinkRef[] sinks, int exchangeSid, MarketStatusType marketStatus){
		int size = encodeMarketStatus(msgSender,
				0,
				msgSender.buffer(),
				0,
				sbe,
				exchangeSid,
				marketStatus);
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
				MarketStatusSbeDecoder.BLOCK_LENGTH;
		return size;
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
	static int encodeMarketStatus(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer,
			int offset,
			MarketStatusSbeEncoder sbe,
			MarketStatusSbeDecoder srcSbe){
		
		sbe.wrap(buffer, offset + sender.headerSize())
			.exchangeSid(srcSbe.exchangeSid()).status(srcSbe.status());
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				MarketStatusSbeEncoder.BLOCK_LENGTH,
				MarketStatusSbeEncoder.TEMPLATE_ID,
				MarketStatusSbeEncoder.SCHEMA_ID,
				MarketStatusSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
			
	
	/* i expect we either call encoding method with the whole order book or with an exchange specific binary format */
	static int encodeMarketStatus(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer,
			int offset,
			MarketStatusSbeEncoder sbe,
			int exchangeSid,
			MarketStatusType marketStatus){
		
		sbe.wrap(buffer, offset + sender.headerSize())
		.exchangeSid(exchangeSid)
		.status(marketStatus);

		sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				MarketStatusSbeEncoder.BLOCK_LENGTH,
				MarketStatusSbeEncoder.TEMPLATE_ID,
				MarketStatusSbeEncoder.SCHEMA_ID,
				MarketStatusSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());

		return sbe.encodedLength() + sender.headerSize();
	}
}
