package com.lunar.message.binary;

import com.lunar.core.CommandTrackerWrapper;
import com.lunar.core.OrderReqAndExecTrackerWrapper;
import com.lunar.core.RequestTrackerWrapper;

public class MessengerWrapper {
	private final Messenger messenger;
	private final RequestTrackerWrapper requestTrackerWrapper;
	private final CommandTrackerWrapper commandTrackerWrapper;
	private final OrderReqAndExecTrackerWrapper orderTrackerWrapper;
	
	public MessengerWrapper(Messenger messenger){
		this.messenger = messenger;
		this.requestTrackerWrapper = new RequestTrackerWrapper(this.messenger.requestTracker());
		this.commandTrackerWrapper = new CommandTrackerWrapper(this.messenger.commandTracker());
		this.orderTrackerWrapper = new OrderReqAndExecTrackerWrapper(this.messenger.orderReqAndExecTracker());
	}
	public RequestTrackerWrapper requestTracker(){
		return requestTrackerWrapper;
	}
	public CommandTrackerWrapper commandTracker(){
		return commandTrackerWrapper;
	}
	public OrderReqAndExecTrackerWrapper orderTracker(){
		return orderTrackerWrapper;
	}
}
