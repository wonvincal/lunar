package com.lunar.message.sender;

import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.PerfDataSbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class PerfDataSender {
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = PerfDataSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final MessageHeaderEncoder header = new MessageHeaderEncoder();
	private final PerfDataSbeEncoder sbe = new PerfDataSbeEncoder();
	
	public static PerfDataSender of(MessageSender msgSender){
		return new PerfDataSender(msgSender);
	}
	
	PerfDataSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public void sendPerfData(MessageSinkRef sink, int sinkId, long roundTripNs){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodePerfData(bufferClaim.buffer(),
					bufferClaim.offset(),
					header,
					sbe,
					msgSender.self().sinkId(),
					sink.sinkId(),
					msgSender.getAndIncSeq(),
					sinkId,
					roundTripNs);
			bufferClaim.commit();
			return;
		}
		sendPerfDataCopy(msgSender.self(), sink, sinkId, roundTripNs);
	}
	
	private void sendPerfDataCopy(MessageSinkRef sender, MessageSinkRef sink, int sinkId, long roundTripNs){
		int size = encodePerfData(msgSender.buffer(),
				0,
				header,
				sbe,
				sender.sinkId(),
				sink.sinkId(),
				msgSender.getAndIncSeq(),
				sinkId,
				roundTripNs);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + PerfDataSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + PerfDataSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + PerfDataSbeEncoder.BLOCK_LENGTH;
	}
	
	static int encodePerfData(final MutableDirectBuffer buffer, int offset, MessageHeaderEncoder header, PerfDataSbeEncoder sbe, int senderSinkId, int dstSinkId, int seq, int sinkId, long roundTripNs){
		sbe.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH)
		.sinkId((byte)sinkId)
		.roundTripNs(roundTripNs);
		MessageEncoder.encodeHeader(buffer, 
				offset, 
				header, 
				PerfDataSbeEncoder.BLOCK_LENGTH, 
				PerfDataSbeEncoder.TEMPLATE_ID, 
				PerfDataSbeEncoder.SCHEMA_ID, 
				PerfDataSbeEncoder.SCHEMA_VERSION, 
				senderSinkId, 
				dstSinkId,
				seq,
				sbe.encodedLength());
		return sbe.encodedLength() + MessageHeaderEncoder.ENCODED_LENGTH;
	}
}
