package com.lunar.perf.latency;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceBridge;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.NodeService;

public class Node {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(Node.class);
	protected final LunarService messageService;
	protected NodeService nodeService;
	
	public Node(LunarService messageService){
		this.messageService = messageService;
	}
	
	public void forwardTo(Node node){
		nodeService.forwardTo(node.sink());
	}
	
	public MessageSinkRef sink(){
		return messageService.messenger().self();
	}
	
	public Node bind(){
		nodeService = (NodeService)LunarServiceBridge.of(messageService).coreService();
		return this;
	}	
}
