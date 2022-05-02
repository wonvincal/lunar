package com.lunar.message;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.message.binary.CommandDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;

import org.agrona.MutableDirectBuffer;

public class Command extends Message {
	static final Logger LOG = LogManager.getLogger(Command.class);

	private final CommandType commandType;
	private final int senderSinkId;
	private final ImmutableList<Parameter> parameters;
	private final BooleanType toSend;
	private int clientKey;
	private final Optional<Long> timeoutNs;
	private CommandAckType ackType = CommandAckType.NULL_VAL;

	private Command(int senderSinkId, int clientKey, CommandType commandType, ImmutableList<Parameter> parameters, BooleanType toSend, Optional<Long> timeoutNs){
		this.senderSinkId = senderSinkId;
		this.clientKey = clientKey;
		this.commandType = commandType;
		this.parameters = parameters;
		this.toSend = toSend;
		this.timeoutNs = timeoutNs;
		if (timeoutNs.isPresent() && timeoutNs.get() <= 0){
			throw new IllegalArgumentException("timeoutNs must be greater than zero [senderSinkdId:" + senderSinkId + 
					", clientKey: " + clientKey + 
					", commandType: " + commandType.name() + 
					", timeoutNs:" + timeoutNs.get() + "]");
		}
	}

	public int clientKey(){
		return clientKey;
	}
	
	public Command clientKey(int value){
		clientKey = value;
		return this;
	}
	
	public BooleanType toSend(){
		return toSend;
	}
	
	public Optional<Long> timeoutNs(){
		return timeoutNs;
	}
	
	public CommandType commandType(){
		return commandType;
	}
	
	public ImmutableList<Parameter> parameters(){
		return parameters;
	}
	
	public CommandAckType ackType(){
		return ackType;
	}
	
	public Command ackType(CommandAckType ackType){
		this.ackType = ackType;
		return this;
	}

	@Override
	public int senderSinkId() {
		return senderSinkId;
	}
	
	public Command changeSenderTo(int newSenderSinkId){
		return new Command(newSenderSinkId,
				this.clientKey,
				this.commandType,
				this.parameters,
				this.toSend,
				this.timeoutNs);
	}
	
	public static Command of(int senderSinkId, CommandSbeDecoder command, MutableDirectBuffer stringBuffer) throws UnsupportedEncodingException{
		return new Command(senderSinkId, 
				command.clientKey(), 
				command.commandType(),
				CommandDecoder.generateParameterList(stringBuffer, command),
				command.toSend(),
				(command.timeoutNs() == CommandSbeDecoder.timeoutNsNullValue()) ? Optional.empty() : Optional.of(command.timeoutNs()));
	}
	
	public static Command of(int senderSinkId, int clientKey, CommandType commandType, List<Parameter> parameters, BooleanType toSend, long timeoutNs){
		return new Command(senderSinkId, clientKey, commandType, (new ImmutableList.Builder<Parameter>().addAll(parameters)).build(), toSend, Optional.of(timeoutNs));
	}

	public static Command of(int senderSinkId, int clientKey, CommandType commandType, List<Parameter> parameters, BooleanType toSend){
		return new Command(senderSinkId, clientKey, commandType, (new ImmutableList.Builder<Parameter>().addAll(parameters)).build(), toSend, Optional.empty());
	}

	public static Command of(int senderSinkId, int clientKey, CommandType commandType, List<Parameter> parameters, long timeoutNs){
		return new Command(senderSinkId, clientKey, commandType, new ImmutableList.Builder<Parameter>().addAll(parameters).build(), BooleanType.FALSE, Optional.of(timeoutNs));
	}
	
	public static Command of(int senderSinkId, int clientKey, CommandType commandType, List<Parameter> parameters){
		return new Command(senderSinkId, clientKey, commandType, new ImmutableList.Builder<Parameter>().addAll(parameters).build(), BooleanType.FALSE, Optional.empty());
	}
	
	public static Command of(int senderSinkId, int clientKey, CommandType commandType){
		return new Command(senderSinkId, clientKey, commandType, Parameter.NULL_LIST, BooleanType.FALSE, Optional.empty());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("commandType:").append(commandType.name())
			.append(", ackType: ").append(ackType.name())
			.append(", toSend:").append(toSend.name())
			.append(", timeoutNs:").append(timeoutNs.isPresent() ? timeoutNs.get() : "default")
			.append(", parameters:");
		for (Parameter parameter : parameters){
			builder.append(parameter.toString()).append(", ");
		}
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((commandType == null) ? 0 : commandType.hashCode());
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + senderSinkId;
		result = prime * result + ((toSend == null) ? 0 : toSend.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Command other = (Command) obj;
		if (commandType != other.commandType)
			return false;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} 
		else {
			if (other.parameters == null){
				return false;
			}
			if (parameters.size() != other.parameters.size()){
				return false;
			}
			for (int i = 0; i < parameters.size(); i++){
				if (!parameters.get(i).equals(other.parameters.get(i))){
					return false;
				}
			}
		}
		if (senderSinkId != other.senderSinkId)
			return false;
		if (toSend != other.toSend)
			return false;
		return true;
	}
}
