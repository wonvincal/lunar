package com.lunar.message.binary;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.message.Command;
import com.lunar.message.Message;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.service.ServiceConstant;

public class CommandDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(CommandDecoder.class);
	private final CommandSbeDecoder sbe = new CommandSbeDecoder();
	private final HandlerList<CommandSbeDecoder> handlerList;
	private final byte[] byteArrayBuffer = new byte[CommandSbeDecoder.ParametersDecoder.parameterValueLength()];

	CommandDecoder(Handler<CommandSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<CommandSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(CommandSbeDecoder sbe){
		return String.format("commandType:%s, toSend:%s",
				sbe.commandType().name(),
				sbe.toSend().name());
	}

	@Override
	public Message decodeAsMessage(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		int clientKey = sbe.clientKey();
		BooleanType toSend = sbe.toSend();
		CommandType commandType = sbe.commandType();
		long timeoutNs = sbe.timeoutNs();
		ParametersDecoder parameters = sbe.parameters();
		int count = parameters.count();
		List<Parameter> parameterList = new ArrayList<Parameter>(count);
		while (parameters.hasNext()){
			ParametersDecoder parameter = parameters.next();
			String parameterValue = null;
			if (parameter.parameterValueLong() == ParametersDecoder.parameterValueLongNullValue()){
				parameterList.add(Parameter.of(parameter.parameterType(), parameter.parameterValueLong()));					
			}
			else{
				parameter.getParameterValue(byteArrayBuffer, 0);
				parameterValue = new String(byteArrayBuffer);
				parameterList.add(Parameter.of(parameter.parameterType(), parameterValue));					
			}
		}
		
		if (timeoutNs == CommandSbeDecoder.timeoutNsNullValue()){
			return Command.of(header.senderSinkId(), clientKey, commandType, parameterList, toSend).seq(header.seq());
		}
		return Command.of(header.senderSinkId(), clientKey, commandType, parameterList, toSend, timeoutNs).seq(header.seq());			
	}

	HandlerList<CommandSbeDecoder> handlerList(){
		return handlerList;
	}
	
	public static CommandDecoder of(){
		return new CommandDecoder(NULL_HANDLER);
	}

	static final Handler<CommandSbeDecoder> NULL_HANDLER = new Handler<CommandSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
			LOG.warn("message sent to command null handler [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	public static ImmutableListMultimap<ParameterType, Parameter> generateParameterMap(MutableDirectBuffer stringByteBuffer, CommandSbeDecoder command) throws UnsupportedEncodingException{
		int origLimit = command.limit();
		ImmutableListMultimap.Builder<ParameterType, Parameter> builder = ImmutableListMultimap.<ParameterType, Parameter>builder();
		ParametersDecoder parameterGroup = command.parameters();
		for (ParametersDecoder parameter : parameterGroup){
			if (parameter.parameterValueLong() != ParametersDecoder.parameterValueLongNullValue()){
				builder.put(parameter.parameterType(), Parameter.of(parameter.parameterType(), parameter.parameterValueLong()));
			}
			else{
				parameter.getParameterValue(stringByteBuffer.byteArray(), 0);
				builder.put(parameter.parameterType(), 
						Parameter.of(parameter.parameterType(),
								new String(stringByteBuffer.byteArray(),
										0, 
										ParametersDecoder.parameterValueLength(), 
										ParametersDecoder.parameterValueCharacterEncoding())));
			}
		}
		command.limit(origLimit);
		return builder.build();
	}
	
	public static ImmutableList<Parameter> generateParameterList(MutableDirectBuffer stringByteBuffer, CommandSbeDecoder command) throws UnsupportedEncodingException{
		int origLimit = command.limit();
		ParametersDecoder parameterGroup = command.parameters();
		Builder<Parameter> builder = ImmutableList.<Parameter>builder();
		for (ParametersDecoder parameter : parameterGroup){
			if (parameter.parameterValueLong() != ParametersDecoder.parameterValueLongNullValue()){
				builder.add(Parameter.of(parameter.parameterType(), parameter.parameterValueLong()));
			}
			else{
				parameter.getParameterValue(stringByteBuffer.byteArray(), 0);
				builder.add(Parameter.of(parameter.parameterType(),
								new String(stringByteBuffer.byteArray(),
										0, 
										ParametersDecoder.parameterValueLength(), 
										ParametersDecoder.parameterValueCharacterEncoding())));
			}
		}
		command.limit(origLimit);
		return builder.build();
	}
}
