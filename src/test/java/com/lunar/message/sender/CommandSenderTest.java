package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.message.Command;
import com.lunar.message.Message;
import com.lunar.message.Parameter;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.CommandSbeEncoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class CommandSenderTest {
	static final Logger LOG = LogManager.getLogger(CommandSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final CommandSbeEncoder sbeEncoder = new CommandSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
												refSenderSink);
		final int refSeq = sender.peekNextSeq();
		final int clientKey = 11111;
		final CommandType refCommandType = CommandType.START;
		final BooleanType refToSend = BooleanType.TRUE;
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.EXCHANGE.value()),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		final long refTimeoutNs = 1000000000l * 60;
		Command command = Command.of(refSenderSink.sinkId(),
				clientKey,
				refCommandType, 
				refParameters, 
				refToSend,
				refTimeoutNs);
		CommandSender.encodeCommand(sender, 
				refDstSinkId,
				buffer,
				0,
				sbeEncoder,
				command);
				
		MessageReceiver receiver = MessageReceiver.of();
		receiver.commandHandlerList().add(new Handler<CommandSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, MessageHeaderDecoder header,
							   CommandSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refCommandType, codec.commandType());
				assertEquals(refToSend, codec.toSend());
				assertEquals(refTimeoutNs, codec.timeoutNs());
				
				// verify parameter
				ParametersDecoder next = codec.parameters().next();
				assertParameter(ParameterType.TEMPLATE_TYPE, null, Long.valueOf(TemplateType.EXCHANGE.value()), next);
				assertParameter(ParameterType.EXCHANGE_CODE, "SEHK", null, next.next());				
			}
		});
		receiver.receive(buffer, 0);
	}

	@Test
	public void testEncodeDecodeAsMessage(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(101);
		final int refClientKey = 1212;
		final CommandType refCommandType = CommandType.START;
		final BooleanType refToSend = BooleanType.TRUE;
		final long refTimeoutNs = 1000000000l * 60;
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.EXCHANGE.value()),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		Command command = Command.of(refSenderSink.sinkId(),
				refClientKey,
				refCommandType, 
				refParameters, 
				refToSend,
				refTimeoutNs);
		
		CommandSender.encodeCommand(sender, 
				refDstSinkId,
				buffer,
				0,
				sbeEncoder,
				command);
				
		MessageReceiver receiver = MessageReceiver.of();
		Message receivedMessage = receiver.decodeAsMessage(buffer, 0);
		Command receivedCommand = (Command)receivedMessage;
		
		assertEquals(refSeq, receivedCommand.seq());
		assertEquals(refClientKey, receivedCommand.clientKey());
		assertEquals(refCommandType, receivedCommand.commandType());
		assertEquals(refToSend, receivedCommand.toSend());
		assertEquals(refTimeoutNs, receivedCommand.timeoutNs().get().longValue());
		
		// verify parameters
		assertParameter(ParameterType.TEMPLATE_TYPE, null, Long.valueOf(TemplateType.EXCHANGE.value()), command.parameters().get(0));
		assertParameter(ParameterType.EXCHANGE_CODE, "SEHK", null, command.parameters().get(1));
	}
	
	private static void assertParameter(ParameterType expectedType, String expectedValue, Long expectedValueLong, Parameter actual){
		assertEquals(expectedType, actual.type());
		assertEquals(expectedValue, actual.value());
		assertEquals(expectedValueLong, actual.valueLong());
	}

	private static void assertParameter(ParameterType expectedType, String expectedValue, Long expectedValueLong, ParametersDecoder actual){
		assertEquals(expectedType, actual.parameterType());
		if (expectedValue == null){
			assertEquals(ParametersDecoder.parameterValueNullValue(), actual.parameterValue(0));
		}
		else {
			ByteBuffer buffer = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
			actual.getParameterValue(buffer.array(), 0);
			String string = new String(buffer.array(), 0, ParametersDecoder.parameterValueLength());
			assertEquals(expectedValue, string);
		}
		if (expectedValueLong == null){
			assertEquals(ParametersDecoder.parameterValueLongNullValue(), actual.parameterValueLong()); 
		}
		else{
			assertEquals(expectedValueLong.longValue(), actual.parameterValueLong());
		}
	}
}
