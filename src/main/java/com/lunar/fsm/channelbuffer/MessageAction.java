package com.lunar.fsm.channelbuffer;

public class MessageAction {
	public enum MessageActionType {
		SEND_NOW,
		STORED_NO_ACTION,
		DROPPED_NO_ACTION
	}
	private MessageActionType actionType;
	public static MessageAction of(){
		return new MessageAction();
	}
	MessageAction(){
		this.actionType = MessageActionType.SEND_NOW;
	}
	public MessageActionType actionType(){
		return actionType;
	}
	public void actionType(MessageActionType actionType){
		this.actionType = actionType;
	}
}
