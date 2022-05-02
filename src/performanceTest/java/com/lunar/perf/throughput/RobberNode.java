package com.lunar.perf.throughput;

import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceBridge;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.RobberService;

/**
 * 1) Send message to the next node.  
 * 2) Wait until the same message comes back to the head node.
 * 3) Record the same
 * 4) Send the time to histogram
 * @author wongca
 *
 */
public class RobberNode {
	protected final LunarService messageService;
	private RobberService robberService;
	
	public RobberNode(LunarService messageService){
		this.messageService = messageService;
	}
	
	public RobberService robberService(){
		return robberService;
	}
	
	public RobberNode bind(){
		this.robberService = (RobberService)LunarServiceBridge.of(messageService).coreService();
		return this;
	}
	
	public MessageSinkRef sink(){
		return this.messageService.messenger().self();
	}
	
	public void start(){
		this.robberService.run();
	}
}
