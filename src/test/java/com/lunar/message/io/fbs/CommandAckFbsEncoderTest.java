package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeEncoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.sender.CommandAckSender;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CommandAckFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(CommandAckFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final CommandAckSbeEncoder sbeEncoder = new CommandAckSbeEncoder();
	private final CommandAckSbeDecoder sbeDecoder = new CommandAckSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
		int clientKey = 121312;
		CommandAckType commandAckType = CommandAckType.INVALID_PARAMETER;
		CommandType commandType = CommandType.REFRESH;
		CommandAckSender.encodeCommandAckOnly(buffer,
				0, 
				sbeEncoder, 
				clientKey, 
				commandAckType, 
				commandType);
		sbeDecoder.wrap(buffer, 0, CommandAckSbeDecoder.BLOCK_LENGTH, CommandAckSbeDecoder.SCHEMA_VERSION);
		
		int offset = CommandAckFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		CommandAckFbs commandAckFbs = CommandAckFbs.getRootAsCommandAckFbs(builder.dataBuffer());
		assertEquals(clientKey, commandAckFbs.clientKey());
		assertEquals(commandAckType.value(), commandAckFbs.ackType());
		assertEquals(commandType.value(), commandAckFbs.commandType());
	}
}
