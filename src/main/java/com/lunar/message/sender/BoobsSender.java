package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.Boobs;

import org.agrona.MutableDirectBuffer;

public class BoobsSender {
	static final Logger LOG = LogManager.getLogger(BoobsSender.class);

	private final MessageSender msgSender;
	private final BoobsSbeEncoder sbe = new BoobsSbeEncoder();

	/**
	 * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
	 * @param msgSender
	 * @return
	 */
	public static BoobsSender of(MessageSender msgSender){
		return new BoobsSender(msgSender);
	}
	
	BoobsSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public long trySendBoobs(MessageSinkRef sink, BoobsSbeDecoder boobs){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeBoobs(msgSender,
					sink.sinkId(), 
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					boobs);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeBoobs(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				boobs);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	

	/**
	 * This is a reference implementation of what is going to happen.  We are going to get binary data from 'somewhere', and turn that
	 * into our own format.
	 * @param sink
	 * @param secSid
	 * @param orderBook
	 */
	public void sendBoobs(MessageSinkRef sink, BoobsSbeDecoder boobs){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeBoobs(msgSender,
					sink.sinkId(), 
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					boobs);
			bufferClaim.commit();
			return;
		}
		int size = encodeBoobs(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				boobs);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
    public void sendBoobs(MessageSinkRef sink, Boobs boobs){
        sendBoobs(sink, boobs.secSid(), boobs.bestBid(), boobs.bestAsk(), boobs.last());
    }
    
	public void sendBoobs(MessageSinkRef sink, long secSid, int bestBid, int bestAsk, int last){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeBoobs(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					secSid,
					bestBid,
					bestAsk,
					last);
			bufferClaim.commit();
			return;
		}
		int size = encodeBoobs(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				secSid,
				bestBid,
				bestAsk,
				last);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}

    public void sendBoobs(MessageSinkRef[] sinks, long secSid, Boobs boobs){
        sendBoobs(sinks, secSid, boobs.bestBid(), boobs.bestAsk(), boobs.last());
    }

	public void sendBoobs(MessageSinkRef[] sinks, long secSid, int bestBid, int bestAsk, int last){
		int size = encodeBoobs(msgSender,
				0,
				msgSender.buffer(),
				0,
				sbe,
				secSid,
                bestBid,
                bestAsk,
                last);
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
		        BoobsSbeDecoder.BLOCK_LENGTH;
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
	static int encodeBoobs(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer,
			int offset,
			BoobsSbeEncoder sbe,
			BoobsSbeDecoder srcSbe){
		
		sbe.wrap(buffer, offset + sender.headerSize())
			.secSid(srcSbe.secSid()).bid(srcSbe.bid()).ask(srcSbe.ask()).last(srcSbe.last());			

		sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				BoobsSbeEncoder.BLOCK_LENGTH,
				BoobsSbeEncoder.TEMPLATE_ID,
				BoobsSbeEncoder.SCHEMA_ID,
				BoobsSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());

		return sender.headerSize() + sbe.encodedLength();
	}
			
	
	/* i expect we either call encoding method with the whole order book or with an exchange specific binary format */
	static int encodeBoobs(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer,
			int offset,
			BoobsSbeEncoder sbe,
			long secSid,
			int bestBid,
			int bestAsk,
			int last){
		
		int payloadLength = encodeBoobsWithoutHeader(buffer, offset + sender.headerSize(), sbe, secSid, bestBid, bestAsk, last);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				BoobsSbeEncoder.BLOCK_LENGTH,
				BoobsSbeEncoder.TEMPLATE_ID,
				BoobsSbeEncoder.SCHEMA_ID,
				BoobsSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeBoobsWithoutHeader(MutableDirectBuffer buffer,
			int offset,
			BoobsSbeEncoder sbe,
			long secSid,
			int bestBid,
			int bestAsk,
			int last){
		sbe.wrap(buffer, offset)
		.secSid(secSid)
		.bid(bestBid)
		.ask(bestAsk)
		.last(last);
		return sbe.encodedLength();
	}
	
}
