package com.lunar.perf.latency;

import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceBridge;
import com.lunar.service.HeadNodeService;

/**
 * 1) Send message to the next node.  
 * 2) Wait until the same message comes back to the head node.
 * 3) Record the same
 * 4) Send the time to histogram
 * @author wongca
 *
 */
public class HeadNode extends Node {
	private final LatencyTestContext context;
	private HeadNodeService headNodeService;
	
	public HeadNode(LatencyTestContext context, LunarService messageService){
		super(messageService);
		this.context = context;
	}
	
	public HeadNodeService headNodeService(){
		return headNodeService;
	}
	
	@Override
	public Node bind(){
		super.bind();
		this.headNodeService = (HeadNodeService)LunarServiceBridge.of(messageService).coreService();
		context.actualMessageSize(this.headNodeService.prepareMessage(context.messageSize()));
		return this;
	}
	
	public void start(){
		this.headNodeService.start();
	}
}
