package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.CommandAck;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.CommandAckSbeEncoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class CommandAckSender {
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = CommandAckSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final MessageHeaderEncoder header = new MessageHeaderEncoder();
	private final CommandAckSbeEncoder sbe = new CommandAckSbeEncoder();
	
	public static CommandAckSender of(MessageSender msgSender){
		return new CommandAckSender(msgSender);
	}
	
	CommandAckSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	/**
	 * Send command ack. Message sequence number will be set in the input {@link commandAck} 
	 * @param sender
	 * @param sink
	 * @param commandAck
	 */
	public void sendCommandAck(MessageSinkRef sink, CommandAck commandAck){
		int seq = msgSender.getAndIncSeq();
		sendCommandAck(msgSender.self(), sink, seq, commandAck.clientKey(), commandAck.ackType(), commandAck.commandType());
		commandAck.seq(seq);
	}
	
	/**
	 * Send command ack. Caller has no access to the generated message sequence number.
	 * @param sender
	 * @param sink
	 * @param key
	 * @param commandAckType
	 * @param commandType
	 */
	public void sendCommandAck(MessageSinkRef sink, int key, CommandAckType commandAckType, CommandType commandType){
		sendCommandAck(msgSender.self(), sink, msgSender.getAndIncSeq(), key, commandAckType, commandType);
	}

	private void sendCommandAck(MessageSinkRef sender, MessageSinkRef sink, int seq, int key, CommandAckType commandAckType, CommandType commandType){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(blockLength(), sink, bufferClaim) == MessageSink.OK){
			encodeCommandAck(bufferClaim.buffer(),
								bufferClaim.offset(), 
								header,
								sbe,
								sender.sinkId(), 
								sink.sinkId(),
								seq,
								key, 
								commandAckType,
								commandType);
			bufferClaim.commit();
			return;
		}
		sendCommandAckCopy(sender, sink, seq, key, commandAckType, commandType);
	}

	private void sendCommandAckCopy(MessageSinkRef sender, MessageSinkRef sink, int seq, int key, CommandAckType commandAckType, CommandType commandType){
		int size = encodeCommandAck(msgSender.buffer(), 
									0, 
									header, 
									sbe, 
									sender.sinkId(), 
									sink.sinkId(), 
									seq,
									key,
									commandAckType,
									commandType);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	public int getAndIncSeq(){
		return msgSender.getAndIncSeq();
	}

	@SuppressWarnings("unused")
	private int blockLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + CommandAckSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + CommandAckSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + CommandAckSbeEncoder.BLOCK_LENGTH;
	}
	
	public int encodeCommandAck(MutableDirectBuffer dstBuffer, int offset, int dstSinkId, int seq, int clientKey, CommandAckType commandAckType, CommandType commandType){
		int payloadLength = encodeCommandAckOnly(dstBuffer,
				offset + header.encodedLength(),
				sbe,
				clientKey,
				commandAckType,
				commandType);
		
		msgSender.encodeHeader(dstSinkId, 
				dstBuffer, 
				offset, 
				CommandAckSbeEncoder.BLOCK_LENGTH, 
				CommandAckSbeEncoder.TEMPLATE_ID, 
				CommandAckSbeEncoder.SCHEMA_ID, 
				CommandAckSbeEncoder.SCHEMA_VERSION, 
				payloadLength);
		return header.encodedLength() + payloadLength; 
	}

	static int encodeCommandAck(MutableDirectBuffer buffer, int offset, MessageHeaderEncoder header, CommandAckSbeEncoder sbe, int senderSinkId, int dstSinkId, int seq, int clientKey, CommandAckType commandAckType, CommandType commandType){
		int payloadLength = encodeCommandAckOnly(buffer,
				offset + header.encodedLength(),
				sbe,
				clientKey,
				commandAckType,
				commandType);
		
		MessageEncoder.encodeHeader(buffer,
				offset,
				header,
				CommandAckSbeEncoder.BLOCK_LENGTH,
				CommandAckSbeEncoder.TEMPLATE_ID,
				CommandAckSbeEncoder.SCHEMA_ID,
				CommandAckSbeEncoder.SCHEMA_VERSION,
				senderSinkId,
				dstSinkId,
				seq,
				payloadLength);
		
		return header.encodedLength() + payloadLength; 
	}
	
	public static int encodeCommandAckOnly(MutableDirectBuffer buffer, 
			int offset, 
			CommandAckSbeEncoder sbe, 
			int clientKey, 
			CommandAckType commandAckType, 
			CommandType commandType){
		sbe.wrap(buffer,  offset)
		.clientKey(clientKey)
		.ackType(commandAckType)
		.commandType(commandType);
		return sbe.encodedLength();
	}
}
