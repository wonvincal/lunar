package com.lunar.perf.throughput;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceBridge;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.PoliceService;

public class PoliceNode {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(PoliceNode.class);
	protected final LunarService messageService;
	protected PoliceService policeService;
	private final ThroughputTestContext context;
	
	public PoliceNode(ThroughputTestContext context, LunarService messageService){
		this.messageService = messageService;
		this.context = context;
	}
	
	public void aim(RobberNode node){
		policeService.aim(node.sink());
	}
	
	public MessageSinkRef sink(){
		return messageService.messenger().self();
	}
	
	public PoliceNode bind(){
		policeService = (PoliceService)LunarServiceBridge.of(messageService).coreService();
		context.actualMessageSize(policeService.prepareMessage(context.messageSize()));
		return this;
	}
}
