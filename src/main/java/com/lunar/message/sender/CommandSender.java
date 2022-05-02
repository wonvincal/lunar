package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.CommandSbeEncoder;
import com.lunar.message.io.sbe.CommandSbeEncoder.ParametersEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class CommandSender {
	static final Logger LOG = LogManager.getLogger(CommandSender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	public static int EXPECTED_NUM_PARAMETERS = 3;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = CommandSbeEncoder.BLOCK_LENGTH + CommandSbeEncoder.ParametersEncoder.sbeHeaderSize() + CommandSbeEncoder.ParametersEncoder.sbeBlockLength() * EXPECTED_NUM_PARAMETERS;
	}

	private final MessageSender msgSender;
	private final CommandSbeEncoder sbe = new CommandSbeEncoder();

	/**
	 * Create a CommandSender with a specific MessageSender
	 * @param msgSender
	 * @return
	 */
	public static CommandSender of(MessageSender msgSender){
		return new CommandSender(msgSender);
	}
	
	CommandSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}

	/**
	 * Send a command to sink.  Message sequence number will be set in the {@link command} object.
	 * @param sender
	 * @param sink
	 * @param command
	 */
	public long sendCommand(MessageSinkRef sink, Command command){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(command), sink, bufferClaim) == MessageSink.OK){
			encodeCommand(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					command);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		return sendCommandCopy(sink, command);
	}
	
	public int encodeCommand(MutableDirectBuffer dstBuffer, int offset, int sinkId, Command command){
		// try to write directly to the message sink buffer
		return encodeCommand(msgSender,
					sinkId,
					dstBuffer,
					offset,
					sbe,
					command);
	}
	
	private long sendCommandCopy(MessageSinkRef sink, Command command){
		command.seq(msgSender.getAndIncSeq());
		int size = encodeCommand(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0, 
				sbe,
				command);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	private int expectedEncodedLength(Command command){
		int size = CommandSbeEncoder.BLOCK_LENGTH + CommandSbeEncoder.ParametersEncoder.sbeHeaderSize() + CommandSbeEncoder.ParametersEncoder.sbeBlockLength() * command.parameters().size();
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + size +  
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}
	
	static int encodeCommand(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset, 
			CommandSbeEncoder sbe, 
			Command command){
		int payloadLength = encodeCommandOnly(buffer, sender.headerSize(), sbe, command);
		int seq = sender.encodeHeader(dstSinkId,
							buffer,
							offset,
							CommandSbeEncoder.BLOCK_LENGTH,
							CommandSbeEncoder.TEMPLATE_ID,
							CommandSbeEncoder.SCHEMA_ID,
							CommandSbeEncoder.SCHEMA_VERSION,
							payloadLength);
		// set message sequence number
		command.seq(seq);
		return sender.headerSize() + payloadLength;
	}
	
	public static int encodeCommandOnly(MutableDirectBuffer buffer, 
			int offset, 
			CommandSbeEncoder sbe,
			Command command){
		
		sbe.wrap(buffer, offset)
		.clientKey(command.clientKey())
		.commandType(command.commandType())
		.toSend(command.toSend())
		.timeoutNs(command.timeoutNs().isPresent() ? command.timeoutNs().get() : CommandSbeEncoder.timeoutNsNullValue());

		ParametersEncoder parameters = sbe.parametersCount(command.parameters().size());
		for (Parameter parameter : command.parameters()){
			ParametersEncoder p = parameters.next().parameterType(parameter.type());
			if (parameter.isLongValue()){
				p.parameterValueLong(parameter.valueLong());
				p.parameterValue(0, ParametersEncoder.parameterValueNullValue());
			}
			else {
				p.putParameterValue(parameter.value().getBytes(), 0);
				p.parameterValueLong(ParametersEncoder.parameterValueLongNullValue());
			}
		}		
		return sbe.encodedLength();
	}
}
