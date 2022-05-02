package com.lunar.message;

import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;

public class CommandAck extends Message {
	private final int senderSinkId;
	private final int clientKey;
	private final CommandAckType ackType;
	private final CommandType commandType;
	
	public CommandAck(int senderSinkId, int clientKey, CommandAckType ackType, CommandType commandType){
		this.senderSinkId = senderSinkId;
		this.clientKey = clientKey;
		this.ackType = ackType;
		this.commandType = commandType;
	}
	
	public CommandType commandType(){
		return commandType;
	}

	public CommandAckType ackType(){
		return ackType;
	}
	
	public int clientKey(){
		return clientKey;
	}
	
	@Override
	public int senderSinkId() {
		return senderSinkId;
	}
	
	@Override
	public String toString() {
		return "sender:" + senderSinkId + ", clientKey:" + clientKey + ", actType:" + ackType.name() +", commandType:" + commandType.name();
	}
	
	public static CommandAck of(int senderSinkId, int clientKey, CommandAckType ackType, CommandType commandType){
		return new CommandAck(senderSinkId, clientKey, ackType, commandType);
	}
	
	/**
	 * This is mainly being used for testing, because there shouldn't be any scenario  where
	 * we have a command obj when we create a command ack.
	 * @param command
	 * @param senderSinkId
	 * @param ackType
	 * @return
	 */
	public static CommandAck of (Command command, int senderSinkId, CommandAckType ackType){
		return new CommandAck(senderSinkId, command.clientKey(), ackType, command.commandType());
	}
}
