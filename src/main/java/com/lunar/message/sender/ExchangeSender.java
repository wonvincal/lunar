package com.lunar.message.sender;

import com.lunar.entity.Exchange;
import com.lunar.message.io.sbe.ExchangeSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;
import com.lunar.util.StringUtil;

import org.agrona.MutableDirectBuffer;

public class ExchangeSender {
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = ExchangeSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final ExchangeSbeEncoder sbe = new ExchangeSbeEncoder();

	public static ExchangeSender of(MessageSender msgSender){
		return new ExchangeSender(msgSender);
	}
	
	ExchangeSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}

	/**
	 * Send exchange.
	 * @param sender
	 * @param sink
	 * @param exchange
	 */
	public void sendExchange(MessageSinkRef sink, Exchange exchange){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeExchange(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					exchange);
			bufferClaim.commit();
			return;
		}
		int size = encodeExchange(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				exchange);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	/**
	 * Send exchange to array of sinks.
	 * @param sender
	 * @param sinks
	 * @param exchange
	 */
	public long sendExchange(MessageSinkRef[] sinks, Exchange exchange, long[] sinkSendResults){
		// make a copy
		int size = encodeExchange(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(),
				0,
				sbe,
				exchange);
		return msgSender.send(sinks, msgSender.buffer(), 0, size, sinkSendResults);
	}
	
	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + ExchangeSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + ExchangeSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + ExchangeSbeEncoder.BLOCK_LENGTH;
	}
	
	static int encodeExchange(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			ExchangeSbeEncoder sbe, 
			Exchange exchange){
		sbe.wrap(buffer, offset + sender.headerSize())
			.lastUpdateSeq(exchange.lastUpdateSeq())
		    .sid((int)exchange.sid());
		
		sbe.putCode(StringUtil.copy(exchange.code(), sender.stringBuffer()).byteArray(), 0);
		sbe.putName(StringUtil.copy(exchange.name(), sender.stringBuffer()).byteArray(), 0);

		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				ExchangeSbeEncoder.BLOCK_LENGTH, 
				ExchangeSbeEncoder.TEMPLATE_ID, 
				ExchangeSbeEncoder.SCHEMA_ID, 
				ExchangeSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());

		return sbe.encodedLength() + sender.headerSize();
	}
}
