package com.lunar.message.io.fbs;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.ParameterType;

public class CommandFbsEncoder {
	static final Logger LOG = LogManager.getLogger(CommandFbsEncoder.class);

	private static int NULL_OFFSET = -1;

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, Command command){
		int numParameters = command.parameters().size();
		int[] parameterValueEndOffsets = new int[numParameters];
		long[] parameterValueLongs = new long[numParameters];
		ParameterType[] parameterTypes = new ParameterType[numParameters];
		int i = 0;
		for (Parameter parameter : command	 .parameters()){
			parameterTypes[i] = parameter.type();
			if (parameter.isLongValue()){
				parameterValueLongs[i] = parameter.valueLong();
				parameterValueEndOffsets[i] = NULL_OFFSET;
			}
			else{
				// Using SBE's null value to represent a null for parameterValueLong
				parameterValueLongs[i] = ParametersDecoder.parameterValueLongNullValue();
				stringBuffer.clear();
				try {
					stringBuffer.put(parameter.value().getBytes(ParametersDecoder.parameterValueCharacterEncoding()));
				} 
				catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				stringBuffer.flip();
				parameterValueEndOffsets[i] = builder.createString(stringBuffer);
			}
			i++;
		}
		int[] parameterEndOffsets = new int[numParameters];
		for (i = 0; i < numParameters; i++){
			ParameterFbs.startParameterFbs(builder);
			ParameterFbs.addParameterType(builder, parameterTypes[i].value());
			if (parameterValueLongs[i] != ParametersDecoder.parameterValueLongNullValue()){
				ParameterFbs.addParameterValueLong(builder, parameterValueLongs[i]);
			}
			if (parameterValueEndOffsets[i] != NULL_OFFSET){
				ParameterFbs.addParameterValue(builder, parameterValueEndOffsets[i]);
			}
			parameterEndOffsets[i] = ParameterFbs.endParameterFbs(builder);
		}
		int parametersOffset = CommandFbs.createParametersVector(builder, parameterEndOffsets);
		
		CommandFbs.startCommandFbs(builder);
		CommandFbs.addClientKey(builder, command.clientKey());
		CommandFbs.addParameters(builder, parametersOffset);
		CommandFbs.addCommandType(builder, command.commandType().value());
		if (command.timeoutNs().isPresent()){
			CommandFbs.addTimeoutNs(builder, command.timeoutNs().get());
		}
		CommandFbs.addToSend(builder, command.toSend().value());
		return CommandFbs.endCommandFbs(builder);
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, CommandSbeDecoder command){
		int limit = command.limit();
		ParametersDecoder parameterGroup = command.parameters();
		int numParameters = parameterGroup.count();
		int[] parameterValueEndOffsets = new int[numParameters];
		long[] parameterValueLongs = new long[numParameters];
		ParameterType[] parameterTypes = new ParameterType[numParameters];
		int i = 0;
		for (ParametersDecoder parameter : parameterGroup){
			parameterTypes[i] = parameter.parameterType();
			parameterValueLongs[i] = parameter.parameterValueLong();
			if (parameter.parameterValue(0) != ParametersDecoder.parameterValueNullValue()){
				stringBuffer.clear();
				stringBuffer.limit(parameter.getParameterValue(stringBuffer.array(), 0));
				parameterValueEndOffsets[i] = builder.createString(stringBuffer);
			}
			else{
				parameterValueEndOffsets[i] = NULL_OFFSET;
			}
			i++;
		}
		int[] parameterEndOffsets = new int[numParameters];
		for (i = 0; i < numParameters; i++){
			ParameterFbs.startParameterFbs(builder);
			ParameterFbs.addParameterType(builder, parameterTypes[i].value());
			if (parameterValueLongs[i] != ParametersDecoder.parameterValueLongNullValue()){
				ParameterFbs.addParameterValueLong(builder, parameterValueLongs[i]);
			}
			if (parameterValueEndOffsets[i] != NULL_OFFSET){
				ParameterFbs.addParameterValue(builder, parameterValueEndOffsets[i]);
			}
			parameterEndOffsets[i] = ParameterFbs.endParameterFbs(builder);
		}

		int parametersOffset = CommandFbs.createParametersVector(builder, parameterEndOffsets);
		
		CommandFbs.startCommandFbs(builder);
		CommandFbs.addClientKey(builder, command.clientKey());
		CommandFbs.addParameters(builder, parametersOffset);
		CommandFbs.addCommandType(builder, command.commandType().value());
		CommandFbs.addToSend(builder, command.toSend().value());
		if (command.timeoutNs() != CommandSbeDecoder.timeoutNsNullValue()){
			CommandFbs.addTimeoutNs(builder, command.timeoutNs());
		}
		command.limit(limit);
		return CommandFbs.endCommandFbs(builder);
	}
}
