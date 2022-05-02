package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;

public class CommandAckFbsEncoder {
	static final Logger LOG = LogManager.getLogger(CommandAckFbsEncoder.class);

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, CommandAckSbeDecoder commandAck){
		int limit = commandAck.limit();
		CommandAckFbs.startCommandAckFbs(builder);
		CommandAckFbs.addAckType(builder, commandAck.ackType().value());
		CommandAckFbs.addClientKey(builder, commandAck.clientKey());
		CommandAckFbs.addCommandType(builder, commandAck.commandType().value());
		commandAck.limit(limit);
		return CommandAckFbs.endCommandAckFbs(builder);
	}		

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, int clientKey, CommandAckType ackType, CommandType commandType){
		CommandAckFbs.startCommandAckFbs(builder);
		CommandAckFbs.addAckType(builder, ackType.value());
		CommandAckFbs.addClientKey(builder, clientKey);
		CommandAckFbs.addCommandType(builder, commandType.value());
		return CommandAckFbs.endCommandAckFbs(builder);
	}		
}
