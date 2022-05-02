package com.lunar.order;

import com.lunar.core.TriggerInfo;
import com.lunar.message.sink.MessageSinkRef;

public class InvalidOrderRequest extends OrderRequest {

	public static InvalidOrderRequest of(int clientKey){
		return new InvalidOrderRequest(clientKey, null);
	}
	
	InvalidOrderRequest(int clientKey, MessageSinkRef owner) {
		super(clientKey, owner, true, TriggerInfo.of());
	}

	@Override
	public OrderRequestType type() {
		// TODO Auto-generated method stub
		return null;
	}

}
