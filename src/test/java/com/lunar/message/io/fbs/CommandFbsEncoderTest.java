package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeEncoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.sender.CommandSender;
import com.lunar.service.ServiceConstant;

public class CommandFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(CommandFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final CommandSbeEncoder sbeEncoder = new CommandSbeEncoder();
	private final CommandSbeDecoder sbeDecoder = new CommandSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);

	@Test 
	public void testObjectToFbs(){
		// Given
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		CommandType refCommandType = CommandType.START;
		final long refTimeoutNs = 1_000_000_000l * 60;
		Command command = Command.of(anySinkId,
									 refClientKey,
									 refCommandType,
									 refParameters,
									 refTimeoutNs);
		// When
		int offset = CommandFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, command);
		builder.finish(offset);
		
		CommandFbs commandFbs = CommandFbs.getRootAsCommandFbs(builder.dataBuffer());
		assertCommand(command, commandFbs);
	}
	
	@Test
	public void testSbeToFbs(){
		// Given
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		CommandType refCommandType = CommandType.START;
		final long refTimeoutNs = 1_000_000_000l * 60;
		Command command = Command.of(anySinkId,
									 refClientKey,
									 refCommandType,
									 refParameters,
									 refTimeoutNs);
		CommandSender.encodeCommandOnly(buffer, 0, sbeEncoder, command);
		sbeDecoder.wrap(buffer, 0, CommandSbeDecoder.BLOCK_LENGTH, CommandSbeDecoder.SCHEMA_VERSION);
		
		// When
		int offset = CommandFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		CommandFbs commandFbs = CommandFbs.getRootAsCommandFbs(builder.dataBuffer());
		assertCommand(command, commandFbs);
	}
	
	public static void assertCommand(Command command, CommandFbs commandFbs){
		assertEquals(command.commandType().value(), commandFbs.commandType());
		assertEquals(command.clientKey(), commandFbs.clientKey());
		assertEquals(command.toSend().value(), commandFbs.toSend());
		if (command.timeoutNs().isPresent()){
			assertEquals(command.timeoutNs().get().longValue(), commandFbs.timeoutNs());			
		}
		else{
			assertEquals(0, commandFbs.timeoutNs());
		}
		
		int parameterCount = commandFbs.parametersLength();
		assertEquals(parameterCount, command.parameters().size());
		for (int i = 0; i < parameterCount; i++){
			ParameterFbs parameterFbs = commandFbs.parameters(i);
			Parameter refParameter = command.parameters().get(i);
			assertEquals(refParameter.type().value(), parameterFbs.parameterType());
			if (refParameter.isLongValue()){
				// make sure fbs has same long value
				assertEquals(refParameter.valueLong().longValue(), parameterFbs.parameterValueLong());
				// make sure fbs has no string value
				assertNull(parameterFbs.parameterValue());
			}
			else{
				// make sure fbs has no long value
				// make sure fbs has same string value
				assertEquals(refParameter.value(), parameterFbs.parameterValue());
				assertEquals(0, parameterFbs.parameterValueLong());
			}
		}		
	}
}
