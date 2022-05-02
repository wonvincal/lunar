package com.lunar.fsm.service.lunar;

import com.lunar.service.ServiceLifecycleAware;

public class LunarServiceBridge {
	private final LunarService messageService;
	public static LunarServiceBridge of(LunarService messageService){
		return new LunarServiceBridge(messageService);
	}
	public LunarServiceBridge(LunarService messageService){
		this.messageService = messageService;
	}
	public ServiceLifecycleAware coreService(){
		return messageService.service();
	}
}
