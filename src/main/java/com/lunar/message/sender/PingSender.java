package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.PingSbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class PingSender {
	static final Logger LOG = LogManager.getLogger(PingSender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	public static int EXPECTED_NUM_TIMESTAMPS = 3;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = PingSbeEncoder.BLOCK_LENGTH + PingSbeEncoder.TimestampsEncoder.sbeHeaderSize() + PingSbeEncoder.TimestampsEncoder.sbeBlockLength() * EXPECTED_NUM_TIMESTAMPS;
	}
	private final MessageSender msgSender;
	private final MessageSinkRefMgr refMgr;
	private final PingSbeEncoder sbe = new PingSbeEncoder();
	
	public static PingSender of(MessageSender messageSender, MessageSinkRefMgr referenceManager){
		return new PingSender(messageSender, referenceManager);
	}
	
	PingSender(MessageSender messageSender, MessageSinkRefMgr referenceManager){
		this.msgSender = messageSender;
		this.refMgr = referenceManager;
	}
	
	public void appendAndSendTimestampAndSend(MessageSinkRef sink, MutableDirectBuffer buffer, int offset, BooleanType isResponse, int existingTimestampCount, int sinkId, long timestamp){
		appendAndSendTimestampAndSend(msgSender.self().sinkId(), sink, buffer, offset, isResponse, existingTimestampCount, sinkId, timestamp);
	}

	public void appendAndSendTimestampAndSend(int senderSinkId, MessageSinkRef sink, MutableDirectBuffer buffer, int offset, BooleanType isResponse, int existingTimestampCount, int sinkId, long timestamp){
		int size = appendTimestamp(buffer,
									 offset,
									 msgSender.header(),
									 sbe,
									 senderSinkId,
									 sink.sinkId(),
									 msgSender.getAndIncSeq(),
									 isResponse,
									 existingTimestampCount,
									 sinkId,
									 timestamp);
		msgSender.send(sink, buffer, offset, size);
	}
	
	public void sendPingToLocalSinks(BooleanType isResponse, long startTimeNs){
		int size = encodePing(msgSender.buffer(),
				0,
				msgSender.header(),
				sbe,
				msgSender.self().sinkId(),
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.getAndIncSeq(),
				isResponse,
				startTimeNs);
		for (MessageSinkRef sink : this.refMgr.localSinks()){
			MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
			msgSender.send(sink, msgSender.buffer(), 0, size);			
		}
	}
	
	public void sendPing(MessageSinkRef sink, BooleanType isResponse, long startTimeNs){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(0), sink, bufferClaim) == MessageSink.OK){
			encodePing(bufferClaim.buffer(),
					bufferClaim.offset(),
					msgSender.header(),
					sbe,
					msgSender.self().sinkId(),
					sink.sinkId(),
					msgSender.getAndIncSeq(),
					isResponse,
					startTimeNs);
			bufferClaim.commit();
			return;
		}
		sendPingCopy(msgSender.self(), sink, isResponse, startTimeNs);
	}
	
	public void sendPing(MessageSinkRef[] sinks, BooleanType isResponse, long startTimeNs){
		int size = encodePing(msgSender.buffer(),
				0,
				msgSender.header(),
				sbe,
				msgSender.self().sinkId(),
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.getAndIncSeq(),
				isResponse,
				startTimeNs);
		for (MessageSinkRef sink : sinks){
			MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
			msgSender.send(sink, msgSender.buffer(), 0, size);
		}
	}
	
	private void sendPingCopy(MessageSinkRef sender, MessageSinkRef sink, BooleanType isResponse, long startTimeNs){
		int size = encodePing(
				msgSender.buffer(),
				0,
				msgSender.header(),
				sbe,
				sender.sinkId(),
				sink.sinkId(),
				msgSender.getAndIncSeq(),
				isResponse,
				startTimeNs);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	private int expectedEncodedLength(int numTimestamps){
		int size = MessageHeaderEncoder.ENCODED_LENGTH + PingSbeEncoder.BLOCK_LENGTH + PingSbeEncoder.TimestampsEncoder.sbeHeaderSize() + PingSbeEncoder.TimestampsEncoder.sbeBlockLength() * numTimestamps;
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + size + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}
	
	static int encodePing(final MutableDirectBuffer buffer, 
							int offset, 
							MessageHeaderEncoder header,
							PingSbeEncoder sbe,
							int senderSinkId, 
							int dstSinkId, 
							int seq, 
							BooleanType isResponse, 
							long startTimeNs){
		sbe.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH)
		.isResponse(isResponse)
		.startTime(startTimeNs);
		MessageEncoder.encodeHeader(buffer,
				offset,
				header, 
				PingSbeEncoder.BLOCK_LENGTH,
				PingSbeEncoder.TEMPLATE_ID,
				PingSbeEncoder.SCHEMA_ID, 
				PingSbeEncoder.SCHEMA_VERSION,
				senderSinkId,
				dstSinkId,
				seq,
				sbe.encodedLength());
		return sbe.encodedLength() + MessageHeaderEncoder.ENCODED_LENGTH;
	}

	static int appendTimestamp(final MutableDirectBuffer buffer, 
											int offset, 
											MessageHeaderEncoder header,
											PingSbeEncoder sbe,
											int senderSinkId, 
											int dstSinkId,
											int seq,
											BooleanType isResponse, 
											int existingNumTs, 
											int sinkId, 
											long timestamp){
		
		PingSbeEncoder.TimestampsEncoder timestamps = sbe.wrap(buffer, 
				offset + header.encodedLength())
			.isResponse(isResponse)
			.timestampsCount(existingNumTs + 1);		
		for (int i = 0; i < existingNumTs; i++){
			LOG.debug("extra next");
			timestamps.next();
		}
		timestamps.next()
				  .sinkId((byte)sinkId)
			      .timestamp(timestamp);
		
		MessageEncoder.encodeHeader(buffer,
				offset,
				header, 
				PingSbeEncoder.BLOCK_LENGTH,
				PingSbeEncoder.TEMPLATE_ID,
				PingSbeEncoder.SCHEMA_ID, 
				PingSbeEncoder.SCHEMA_VERSION,
				senderSinkId, 
				dstSinkId,
				seq,
				sbe.encodedLength());

		return sbe.encodedLength() + header.encodedLength();
	}
}
