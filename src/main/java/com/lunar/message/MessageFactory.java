package com.lunar.message;

import java.util.EnumMap;

import com.google.common.collect.ImmutableList;
import com.lunar.config.MessagingConfig;
import com.lunar.message.binary.MessageSinkEventFactory;
import com.lunar.message.binary.NullHandlerFactory;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSinkRef;
import com.typesafe.config.Config;

/**
 * Factory that holds messaging related configuration from configuration file.
 * 
 * Thread Safety: Yes
 * @author Calvin
 *
 */
public class MessageFactory {
	private final int frameSize;
	private final MessageSinkEventFactory eventFactory;
	private final long commandTimeoutNs;
	private final EnumMap<RequestType, RequestTypeSetting> reqTypeSettings;
	@SuppressWarnings("unused")
	private final NullHandlerFactory nullHandlerFactory; 
	
	public MessageFactory(MessagingConfig config){
		this.frameSize = config.frameSize();
		this.eventFactory = new MessageSinkEventFactory(frameSize);
		this.reqTypeSettings = config.reqTypeSettings();
		this.commandTimeoutNs = config.commandTimeout().toNanos();
		this.nullHandlerFactory = new NullHandlerFactory(); // TODO Support using deadletters as null handlers
	}

	public MessageSinkEventFactory eventFactory(){
		return eventFactory;
	}
	
	public int frameSize(){
		return frameSize;
	}
	
	public long commandTimeoutNs(){
		return commandTimeoutNs;
	}
	
	public boolean update(Config cfg){
		return false;
	}
	
	/**
	 * Create request.  Request settings that base on settings in configuration file will be passed into the constructor
	 * of the request.
	 * @param requestType
	 * @param senderSinkId
	 * @param seq
	 * @param parameters
	 * @return
	 */
	public Request createRequest(RequestType requestType, int senderSinkId, ImmutableList<Parameter> parameters, BooleanType toSend){
		return new Request(senderSinkId, requestType, parameters, toSend);
	}
	
	public RequestTypeSetting getSettings(RequestType requestType){
		RequestTypeSetting setting = reqTypeSettings.get(requestType);
		if (setting == null){
			throw new IllegalArgumentException("Setting for " + requestType.name() + " is not available");
		}
		return setting;
	}
	
	public MessageBuffer createMessageBuffer(){
		return MessageBuffer.ofSize(frameSize);
	}
	
	public MessageBuffer createMessageBuffer(int size){
		return MessageBuffer.ofSize(size);
	}

	public MessageSender createMessageSender(MessageSinkRef self){
		return MessageSender.of(frameSize, self);
	}
	public TimerEventSender createStandaloneTimerEventSender(){
		return TimerEventSender.of(MessageSender.of(frameSize,
									MessageSinkRef.createNaSinkRef()));
	}
}
