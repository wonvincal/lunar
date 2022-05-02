package com.lunar.message.sender;

import com.lunar.message.Echo;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EchoSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class EchoSender {
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = EchoSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final EchoSbeEncoder sbe = new EchoSbeEncoder();

	public static EchoSender of(MessageSender msgSender){
		return new EchoSender(msgSender);
	}
	
	EchoSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	/**
	 * Send echo.  Message sequence number will be set in the input {@link echo}.
	 * @param sender
	 * @param sink
	 * @param echo
	 */
	public void sendEcho(MessageSinkRef sink, Echo echo){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeEcho(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					echo);
			bufferClaim.commit();
			return;
		}
		int size = encodeEcho(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0, 
				sbe,
				echo);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	public void sendEcho(MessageSinkRef sink, int key, long startTime, BooleanType isResponse){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeEcho(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					key,
					startTime,
					isResponse);
			bufferClaim.commit();
			return;
		}
		int size = encodeEcho(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0, 
				sbe,
				key,
				startTime,
				isResponse);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + EchoSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + EchoSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + EchoSbeEncoder.BLOCK_LENGTH;
	}
	
	static int encodeEcho(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset, 
			EchoSbeEncoder sbe, 
			Echo echo){
		
		sbe.wrap(buffer, offset + sender.headerSize())
		.key(echo.key())
		.isResponse(echo.isResponse())
		.startTime(echo.startTime());

		int seq = sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				EchoSbeEncoder.BLOCK_LENGTH, 
				EchoSbeEncoder.TEMPLATE_ID, 
				EchoSbeEncoder.SCHEMA_ID, 
				EchoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		
		echo.seq(seq);
		
		return sbe.encodedLength() + sender.headerSize();
	}
 
	public static int encodeEcho(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset, 
			EchoSbeEncoder sbe, 
			int key, 
			long startTime, 
			BooleanType isResponse){
		
		sbe.wrap(buffer, offset + sender.headerSize())
		.key(key)
		.isResponse(isResponse)
		.startTime(startTime);

		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				EchoSbeEncoder.BLOCK_LENGTH, 
				EchoSbeEncoder.TEMPLATE_ID, 
				EchoSbeEncoder.SCHEMA_ID, 
				EchoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());

		return sbe.encodedLength() + sender.headerSize();
	}
}
