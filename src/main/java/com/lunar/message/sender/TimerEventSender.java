package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class TimerEventSender {
	static final Logger LOG = LogManager.getLogger(TimerEventSender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = TimerEventSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final TimerEventSbeEncoder sbe = new TimerEventSbeEncoder();

	public static TimerEventSender of(MessageSender msgSender){
		return new TimerEventSender(msgSender);
	}
	
	TimerEventSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	/**
	 * Send timer event
	 * @param sender
	 * @param sink
	 * @param key
	 * @param timerEventType
	 * @param startTimeNs
	 * @param expiryTimeNs
	 */
	public long sendTimerEvent(MessageSinkRef sink, int key, TimerEventType timerEventType, long startTimeNs, long expiryTimeNs){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeTimerEvent(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					key,
					timerEventType,
					startTimeNs,
					expiryTimeNs);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		return sendTimerEventCopy(msgSender.self(), sink, key, timerEventType, startTimeNs, expiryTimeNs);
	}
	
	private long sendTimerEventCopy(MessageSinkRef sender, MessageSinkRef sink, int key, TimerEventType timerEventType, long startTimeNs, long expiryTimeNs){
		int size = encodeTimerEvent(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				key,
				timerEventType,
				startTimeNs,
				expiryTimeNs);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + TimerEventSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + TimerEventSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + TimerEventSbeEncoder.BLOCK_LENGTH;
	}
	
	static int encodeTimerEvent(final MessageSender sender,
								int dstSinkId,
								final MutableDirectBuffer buffer, 
								int offset, 
								TimerEventSbeEncoder sbe,
								int clientKey, 
								TimerEventType timerEventType, 
								long startTimeNs, 
								long expiryTimeNs){
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(clientKey)
		.timerEventType(timerEventType)
		.startTime(startTimeNs)
		.expiryTime(expiryTimeNs);
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				TimerEventSbeEncoder.BLOCK_LENGTH, 
				TimerEventSbeEncoder.TEMPLATE_ID, 
				TimerEventSbeEncoder.SCHEMA_ID, 
				TimerEventSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sbe.encodedLength() + sender.headerSize();
	}	
}
